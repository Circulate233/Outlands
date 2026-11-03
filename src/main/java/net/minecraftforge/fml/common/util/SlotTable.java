package net.minecraftforge.fml.common.util;

import java.lang.reflect.Array;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.annotation.Nullable;

/**
 * An append-only table of ids to values, indexed by the order in which they were registered.
 *
 * <p>Written for the set that would otherwise be an {@code enum} but cannot stay one: a handful of entries that are
 * fixed when the loader is written, that code outside the loader has a legitimate reason to add to, and that must be
 * closed before anything is constructed from it. {@code EnumHelper}/{@code EnumHackery} is what that shape costs when it
 * is spelled as an enum - new entries have to be pushed into the enum class at runtime - so a loader-owned set of that
 * shape uses this instead, and the values it holds are ordinary objects.
 *
 * <p>Two properties make it a faithful replacement for an enum:
 *
 * <ul>
 *   <li><b>Slot order is declaration order.</b> The built-in entries registered through {@link #registerBuiltin} before
 *       {@link #freezeBuiltins()} occupy slots {@code 0..n-1}, exactly where the enum constants used to sit, so anything
 *       that indexes an array or compares by position keeps working. Entries added later start at {@code n}.</li>
 *   <li><b>Only the id may leave the process.</b> A slot is a position in this launch's table, not a name: it shifts
 *       when a built-in entry is inserted, so it is the one thing that must never be written to a file, a packet or a
 *       log line. The id is the only persistable key.</li>
 * </ul>
 *
 * <p>Deliberately not a game registry. {@code net.minecraftforge.registries.ForgeRegistry} is the wrong host for these
 * entries rather than an alternative to this class: it keys by {@code ResourceLocation}, requires entries to implement
 * {@link net.minecraftforge.registries.IForgeRegistryEntry}, and carries world-save and network-handshake semantics that
 * a loader-internal table must not have. Reusing it would put, say, language adapters into the world save.
 *
 * <p>Nor is it for every closed set. A set that has to stay an enum - because a mod-facing annotation names it as an
 * element type, or because vanilla code switches on it - cannot move here; the annotation element and the switch both
 * require the enum. This is for the loader's own tables, whose entries nothing outside the loader is compiled against.
 *
 * <p>{@code net.minecraftforge.fml.common.discovery.ContainerType} is the documented example of a set that has to stay
 * an enum for a different reason: mods add types to it through {@code EnumHelper.addEnum}/{@code EnumHackery} and pass
 * them to {@code ModCandidate}'s public constructors. Moving it here would delete that extension point rather than
 * modernise it - see the note on that enum.
 *
 * <p>Registration is {@code synchronized} because a table can be reached from a coremod's thread as well as the
 * loader's. Reads are not: they are documented to happen after {@link #freeze()}, which is the point where the table
 * stops changing.
 *
 * @param <T> registered value type
 */
public final class SlotTable<T> {

    private final String name;
    private final Class<T> type;

    /** Registration-time id index. Kept after the freeze: the table holds a handful of entries, and a second lookup
     * regime would be a complexity cliff no call site could see. */
    private final Map<String, Integer> index = new HashMap<>();
    private T[] values;
    private String[] ids;
    private int size;
    private int builtins;
    private boolean builtinsFrozen;
    private boolean frozen;

    public SlotTable(String name, Class<T> type) {
        this.name = name;
        this.type = type;
        this.values = newArray(8);
        this.ids = new String[8];
    }

    @SuppressWarnings("unchecked")
    private T[] newArray(int length) {
        return (T[]) Array.newInstance(type, length);
    }

    /**
     * Registers a built-in entry, which keeps the slot the enum constant used to have.
     *
     * <p>Only before {@link #freezeBuiltins()}: a built-in entry registered after an external one would be numbered
     * after it, and every array indexed by the old ordinal would silently read the wrong entry.
     *
     * @return the slot, {@code 0..n-1}
     * @throws IllegalStateException    if the built-in prefix is already closed or the table is frozen
     * @throws IllegalArgumentException if the id is null or blank, already taken, or the value is of the wrong type
     */
    public synchronized int registerBuiltin(String id, T value) {
        if (builtinsFrozen) {
            throw new IllegalStateException("Cannot register the built-in '" + id + "' in " + name
                    + ": the built-in entries are already closed by freezeBuiltins()");
        }
        return add(id, value);
    }

    /**
     * Closes the built-in prefix: from here on {@link #register} accepts entries and they start after the built-ins.
     *
     * <p>Called even by a table with no built-ins, so a table can never accept an external entry before its own author
     * has said which entries are the fixed part.
     */
    public synchronized void freezeBuiltins() {
        if (builtinsFrozen) {
            return;
        }
        builtinsFrozen = true;
        builtins = size;
    }

    /**
     * Registers an entry that is not part of the fixed set.
     *
     * @return the slot, {@code >= builtinCount()}
     * @throws IllegalStateException    if {@link #freezeBuiltins()} has not been called yet, or the table is frozen
     * @throws IllegalArgumentException if the id is null or blank, already taken, or the value is of the wrong type
     */
    public synchronized int register(String id, T value) {
        if (!builtinsFrozen) {
            throw new IllegalStateException("Cannot register '" + id + "' in " + name
                    + ": call freezeBuiltins() first, so the built-in entries keep slots 0..n-1");
        }
        return add(id, value);
    }

    private int add(String id, T value) {
        if (frozen) {
            throw new IllegalStateException("Cannot register '" + id + "' in " + name + ": it was frozen after discovery");
        }
        if (id == null || id.isBlank()) {
            throw new IllegalArgumentException("Cannot register a " + type.getSimpleName() + " in " + name + " under a blank id");
        }
        if (!type.isInstance(value)) {
            throw new IllegalArgumentException("Cannot register " + (value == null ? "null" : value.getClass().getName())
                    + " in " + name + ", expected " + type.getName());
        }
        Integer existing = index.get(id);
        if (existing != null) {
            // The id is the only key a user or a log reader can use, so a duplicate is a conflict between two
            // declarations rather than a value to overwrite.
            throw new IllegalArgumentException("Duplicate id '" + id + "' in " + name
                    + ": slot " + existing + " is already taken");
        }
        if (size == values.length) {
            int grown = values.length * 2;
            values = Arrays.copyOf(values, grown);
            ids = Arrays.copyOf(ids, grown);
        }
        values[size] = value;
        ids[size] = id;
        index.put(id, size);
        return size++;
    }

    /** Closes the table: further registrations fail, lookups and iteration keep working. Idempotent. */
    public synchronized void freeze() {
        if (frozen) {
            return;
        }
        freezeBuiltins();
        frozen = true;
    }

    public boolean isFrozen() {
        return frozen;
    }

    /** @return whether the built-in prefix was closed, i.e. whether {@link #register} is open */
    public boolean builtinsAreFrozen() {
        return builtinsFrozen;
    }

    /** @return how many entries were registered as built-in, i.e. the number of reserved slots {@code 0..n-1} */
    public int builtinCount() {
        return builtins;
    }

    public int size() {
        return size;
    }

    /**
     * @return the value in {@code slot}
     * @throws IndexOutOfBoundsException when no such slot exists, which is the answer an enum gives for a missing
     *                                   ordinal too - returning {@code null} would only move the failure
     */
    public T at(int slot) {
        if (slot < 0 || slot >= size) {
            throw new IndexOutOfBoundsException("No slot " + slot + " in " + name + ": it holds " + size + " entries");
        }
        return values[slot];
    }

    /**
     * Resolves an id the way every caller wants it: one lookup, {@code null} for unknown.
     *
     * @return the registered value, or {@code null} when {@code id} is null or unknown
     */
    @Nullable
    public T get(@Nullable String id) {
        Integer slot = id == null ? null : index.get(id);
        return slot == null ? null : values[slot];
    }

    /**
     * @return the slot of {@code id}, or {@code -1} when unknown
     *
     * <p>For a caller that wants to keep the {@code int} itself - the point of the slot, and the only reason to prefer
     * this over {@link #get}.
     */
    public int slotOf(@Nullable String id) {
        Integer slot = id == null ? null : index.get(id);
        return slot == null ? -1 : slot;
    }

    /** @return every registered id, in registration order (built-ins first) */
    public List<String> ids() {
        return List.of(Arrays.copyOf(ids, size));
    }

    /**
     * @return the registered values in registration order, as {@code Enum#values()} would return them
     *
     * <p>A copy: mutating it must not reach the table, which is what an enum promises too.
     */
    public T[] values() {
        return Arrays.copyOf(values, size);
    }

    @Override
    public String toString() {
        return name + "[" + size + ", builtins=" + builtins + (frozen ? ", frozen" : "") + "]";
    }
}
