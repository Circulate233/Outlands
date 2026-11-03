package net.minecraftforge.fml.common.util;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CountDownLatch;
import java.util.stream.IntStream;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Covers what makes {@link SlotTable} a drop-in replacement for an enum of loader-owned entries: the built-in entries
 * keep the slots the enum constants had, the set is closed at a known point, and nothing can be added after that.
 *
 * <p>The slot/ordinal equivalence is the property a refactor would break silently - an off-by-one there reads the wrong
 * entry from an array indexed by the old ordinal instead of failing.
 */
class SlotTableTest {

    private static SlotTable<String> newTable() {
        return new SlotTable<>("test entries", String.class);
    }

    /** A table shaped like the enum it stands in for: three fixed entries, then closed. */
    private static SlotTable<String> fixedSet() {
        SlotTable<String> table = newTable();
        table.registerBuiltin("highest", "first");
        table.registerBuiltin("high", "second");
        table.registerBuiltin("normal", "third");
        table.freezeBuiltins();
        return table;
    }

    @Test
    void builtinsOccupyTheSlotsTheEnumConstantsHad() {
        SlotTable<String> table = fixedSet();

        assertEquals(3, table.builtinCount());
        assertEquals(0, table.slotOf("highest"));
        assertEquals(1, table.slotOf("high"));
        assertEquals(2, table.slotOf("normal"));
        assertSame("second", table.at(1));

        // The first entry registered after the prefix starts where the enum's entries ended, so a caller that kept an
        // "add at the end" habit keeps the same numbering.
        assertEquals(3, table.register("extra", "fourth"));
        assertEquals(4, table.size());
        assertSame("fourth", table.at(3));
    }

    @Test
    void externalRegistrationBeforeThePrefixIsClosedIsRejected() {
        SlotTable<String> table = newTable();
        table.registerBuiltin("builtin", "value");

        // Allowing this would number an external entry before the fixed ones and shift every ordinal after it.
        IllegalStateException error = assertThrows(IllegalStateException.class,
                () -> table.register("external", "value"));
        assertTrue(error.getMessage().contains("freezeBuiltins"), error.getMessage());
        assertEquals(1, table.size());
        assertFalse(table.builtinsAreFrozen());
    }

    @Test
    void builtinRegistrationAfterThePrefixIsClosedIsRejected() {
        SlotTable<String> table = fixedSet();

        assertThrows(IllegalStateException.class, () -> table.registerBuiltin("late", "value"));
        assertEquals(3, table.size(), "the late built-in must not be stored");
    }

    @Test
    void duplicateIdIsRejectedRatherThanOverwritten() {
        SlotTable<String> table = newTable();
        table.registerBuiltin("scala", "first");

        IllegalArgumentException builtinError = assertThrows(IllegalArgumentException.class,
                () -> table.registerBuiltin("scala", "second"));
        assertTrue(builtinError.getMessage().contains("scala"), builtinError.getMessage());

        table.freezeBuiltins();
        table.register("kotlin", "first");

        IllegalArgumentException error = assertThrows(IllegalArgumentException.class,
                () -> table.register("kotlin", "second"));

        // The message has to name the clash: two jars claiming one entry is a conflict the user must resolve, and
        // keeping whichever registered first hides it forever.
        assertTrue(error.getMessage().contains("kotlin"), error.getMessage());
        assertEquals(2, table.size(), "the rejected registration must not replace the existing one");
        assertSame("first", table.get("kotlin"));
    }

    @Test
    void registrationAfterFreezeIsRejected() {
        SlotTable<String> table = fixedSet();
        table.freeze();

        assertTrue(table.isFrozen());
        assertThrows(IllegalStateException.class, () -> table.register("scala", "late"));
        assertThrows(IllegalStateException.class, () -> table.registerBuiltin("scala", "late"));
        assertEquals(3, table.size(), "the late registration must not be stored");
    }

    @Test
    void freezeClosesThePrefixOnItsOwn() {
        SlotTable<String> table = newTable();
        table.registerBuiltin("only", "value");

        table.freeze();

        assertTrue(table.builtinsAreFrozen());
        assertEquals(1, table.builtinCount());
    }

    @Test
    void lookupsAgreeWithTheSlots() {
        SlotTable<String> table = fixedSet();
        int scala = table.register("scala", "scala-adapter");

        assertEquals(scala, table.slotOf("scala"));
        assertSame("scala-adapter", table.get("scala"));
        assertSame("scala-adapter", table.at(table.slotOf("scala")));
        assertEquals(-1, table.slotOf("clojure"));
        assertNull(table.get("clojure"));
        assertNull(table.get(null));
        assertEquals(-1, table.slotOf(null));
    }

    @Test
    void lookupsStillWorkAfterTheFreeze() {
        SlotTable<String> table = fixedSet();
        int scala = table.register("scala", "scala-adapter");
        table.freeze();

        // Freezing rejects writes; it must not change what a lookup returns, which is the whole point of freezing
        // before anything is constructed from the table.
        assertEquals(scala, table.slotOf("scala"));
        assertSame("scala-adapter", table.get("scala"));
        assertSame("first", table.at(0));
        assertEquals(List.of("highest", "high", "normal", "scala"), table.ids());
    }

    @Test
    void atRejectsASlotThatDoesNotExist() {
        SlotTable<String> table = fixedSet();

        // An enum has no "constant at ordinal 9" either; returning null would only move the failure one frame away.
        assertThrows(IndexOutOfBoundsException.class, () -> table.at(3));
        assertThrows(IndexOutOfBoundsException.class, () -> table.at(-1));
    }

    @Test
    void valuesKeepRegistrationOrderAndAreACopy() {
        SlotTable<String> table = fixedSet();
        table.register("extra", "fourth");

        assertArrayEquals(new String[] {"first", "second", "third", "fourth"}, table.values());
        assertEquals(List.of("highest", "high", "normal", "extra"), table.ids());

        // Mutating what values() returned must not reach the table, exactly as Enum#values() promises.
        table.values()[0] = "mutated";
        assertSame("first", table.at(0));
    }

    @Test
    void anEmptyTableIsUsable() {
        SlotTable<String> table = newTable();
        table.freezeBuiltins();

        assertEquals(0, table.builtinCount());
        assertEquals(0, table.size());
        assertEquals(List.of(), table.ids());
        assertEquals(0, table.values().length);
        assertEquals(-1, table.slotOf("anything"));
        assertThrows(IndexOutOfBoundsException.class, () -> table.at(0));
    }

    @Test
    void blankIdAndWrongTypeAreRejected() {
        SlotTable<String> table = fixedSet();

        assertThrows(IllegalArgumentException.class, () -> table.register("", "value"));
        assertThrows(IllegalArgumentException.class, () -> table.register("  ", "value"));
        assertThrows(IllegalArgumentException.class, () -> table.register("null-value", null));
        assertEquals(3, table.size());

        SlotTable<CharSequence> typed = new SlotTable<>("typed", CharSequence.class);
        typed.freezeBuiltins();
        // Raw on purpose: the point is the runtime type check, not the compile-time one.
        @SuppressWarnings({"rawtypes", "unchecked"})
        SlotTable raw = typed;
        assertThrows(IllegalArgumentException.class, () -> raw.register("wrong", 42));
    }

    @Test
    void concurrentRegistrationLosesNothing() throws InterruptedException {
        SlotTable<String> table = fixedSet();

        // A table can be reached from a coremod's thread while the loader is still scanning; the point of the lock is
        // that two registrations cannot interleave between the duplicate check and the array write.
        int threads = 8;
        int perThread = 25;
        CountDownLatch start = new CountDownLatch(1);
        List<Thread> workers = new ArrayList<>();
        for (int t = 0; t < threads; t++) {
            final int id = t;
            Thread worker = new Thread(() -> {
                try {
                    start.await();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    return;
                }
                for (int i = 0; i < perThread; i++) {
                    table.register("thread-" + id + "-" + i, "value");
                }
            });
            workers.add(worker);
            worker.start();
        }
        start.countDown();
        for (Thread worker : workers) {
            worker.join();
        }

        int expected = threads * perThread;
        assertEquals(3 + expected, table.size());
        Set<Integer> slots = new HashSet<>();
        for (int t = 0; t < threads; t++) {
            for (int i = 0; i < perThread; i++) {
                String key = "thread-" + t + "-" + i;
                int slot = table.slotOf(key);
                assertTrue(slot >= 3, key + " is missing");
                slots.add(slot);
            }
        }
        assertEquals(expected, slots.size(), "two ids must not share a slot");
        assertEquals(IntStream.range(3, 3 + expected).boxed().collect(java.util.stream.Collectors.toSet()), slots,
                "every non-built-in entry takes the slot after the built-in prefix");
    }
}
