package org.lwjgl.input;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;
import static org.junit.Assert.assertNotSame;
import static org.junit.Assert.assertSame;
import static org.junit.Assert.assertTrue;

import java.nio.ByteBuffer;
import java.util.BitSet;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.junit.Test;

public class InputStateTest {

    @Test
    public void eventSnapshotDoesNotChangeWithLaterInput() {
        try {
            InputState.clear();
            InputState.update(Keyboard.KEY_LCONTROL, true);
            InputSnapshot controlDown = InputSnapshot.current();
            InputState.update(Keyboard.KEY_E, true);
            InputSnapshot bothDown = InputSnapshot.current();
            InputState.update(Keyboard.KEY_LCONTROL, false);

            assertTrue(controlDown.isKeyDown(Keyboard.KEY_LCONTROL));
            assertFalse(controlDown.isKeyDown(Keyboard.KEY_E));
            assertTrue(bothDown.isKeyDown(Keyboard.KEY_LCONTROL));
            assertTrue(bothDown.isKeyDown(Keyboard.KEY_E));
            assertFalse(InputSnapshot.current().isKeyDown(Keyboard.KEY_LCONTROL));
            assertTrue(InputSnapshot.current().isKeyDown(Keyboard.KEY_E));
        } finally {
            InputState.clear();
        }
    }

    @Test
    public void snapshotCopiesSourceBits() {
        try {
            BitSet source = new BitSet();
            source.set(Keyboard.KEY_E);
            InputSnapshot snapshot = new InputSnapshot(source);

            source.clear();

            assertTrue(snapshot.isKeyDown(Keyboard.KEY_E));
        } finally {
            InputState.clear();
        }
    }

    @Test
    public void mouseButtonsUseNegativeKeyCodeBits() {
        try {
            InputState.clear();
            InputState.update(-100, true);
            InputState.update(-93, true);
            InputState.update(255, true);
            InputSnapshot snapshot = InputSnapshot.current();

            assertTrue(snapshot.isKeyDown(-100));
            assertTrue(snapshot.isKeyDown(-93));
            assertTrue(snapshot.isKeyDown(255));
            assertFalse(snapshot.isKeyDown(-99));
            assertFalse(snapshot.isKeyDown(Keyboard.KEY_NONE));
            assertFalse(snapshot.isKeyDown(256));
            assertFalse(snapshot.isKeyDown(-101));
        } finally {
            InputState.clear();
        }
    }

    @Test
    public void repeatedOrInvalidUpdateReusesSnapshot() {
        try {
            InputState.clear();
            InputSnapshot empty = InputSnapshot.current();
            InputState.update(Keyboard.KEY_E, true);
            InputSnapshot pressed = InputSnapshot.current();
            assertNotSame(empty, pressed);

            InputState.update(Keyboard.KEY_E, true);
            assertSame(pressed, InputSnapshot.current());
            InputState.update(Keyboard.KEY_NONE, true);
            InputState.update(256, true);
            InputState.update(-101, true);
            assertSame(pressed, InputSnapshot.current());

            InputState.update(Keyboard.KEY_E, false);
            InputSnapshot released = InputSnapshot.current();
            assertNotSame(pressed, released);
            InputState.update(Keyboard.KEY_E, false);
            assertSame(released, InputSnapshot.current());
        } finally {
            InputState.clear();
        }
    }

    @Test
    public void clearClearsKeyboardAndMouseWithoutChangingHistory() {
        try {
            InputState.clear();
            InputState.update(Keyboard.KEY_E, true);
            InputState.update(-100, true);
            InputSnapshot populated = InputSnapshot.current();

            InputState.clear();
            InputSnapshot cleared = InputSnapshot.current();

            assertNotSame(populated, cleared);
            assertTrue(populated.isKeyDown(Keyboard.KEY_E));
            assertTrue(populated.isKeyDown(-100));
            assertFalse(cleared.isKeyDown(Keyboard.KEY_E));
            assertFalse(cleared.isKeyDown(-100));
            InputState.clear();
            assertSame(cleared, InputSnapshot.current());
        } finally {
            InputState.clear();
        }
    }

    @Test
    public void synchronizeRebuildsStateFromPhysicalInput() {
        ByteBuffer previousKeys = Keyboard.sdlKeyPressedArray;
        int previousButtons = Mouse.sdlButtonState;
        try {
            InputState.clear();
            ByteBuffer physicalKeys = ByteBuffer.allocate(512);
            physicalKeys.put(KeyCodes.lwjglToSdlScancode(Keyboard.KEY_E), (byte) 1);
            Keyboard.sdlKeyPressedArray = physicalKeys;
            Mouse.sdlButtonState = 1;
            InputState.update(Keyboard.KEY_A, true);

            InputState.synchronize();
            InputSnapshot synchronizedState = InputSnapshot.current();

            assertFalse(synchronizedState.isKeyDown(Keyboard.KEY_A));
            assertTrue(synchronizedState.isKeyDown(Keyboard.KEY_E));
            assertTrue(synchronizedState.isKeyDown(-100));
            assertFalse(synchronizedState.isKeyDown(-99));
        } finally {
            Keyboard.sdlKeyPressedArray = previousKeys;
            Mouse.sdlButtonState = previousButtons;
            InputState.clear();
        }
    }

    @Test
    public void keyboardEventsCarryEnqueueSnapshot() {
        try {
            InputState.clear();
            drainKeyboard();
            Keyboard.addKeyEvent(Keyboard.KEY_E, true);
            Keyboard.addCharEvent(Keyboard.KEY_NONE, 'x');

            assertTrue(Keyboard.next());
            InputSnapshot keyEvent = Keyboard.getEventSnapshot();
            assertTrue(keyEvent.isKeyDown(Keyboard.KEY_E));
            assertTrue(Keyboard.next());
            InputSnapshot charEvent = Keyboard.getEventSnapshot();
            assertSame(keyEvent, charEvent);
            assertEquals(Keyboard.KEY_NONE, Keyboard.getEventKey());
            assertEquals('x', Keyboard.getEventCharacter());

            InputState.update(Keyboard.KEY_E, false);
            assertTrue(keyEvent.isKeyDown(Keyboard.KEY_E));
            assertTrue(charEvent.isKeyDown(Keyboard.KEY_E));
            assertFalse(InputSnapshot.current().isKeyDown(Keyboard.KEY_E));
        } finally {
            drainKeyboard();
            Keyboard.enableRepeatEvents(true);
            InputState.clear();
        }
    }

    @Test
    public void prebuiltKeyboardEventIsRefreshedAtEnqueue() {
        try {
            InputState.clear();
            drainKeyboard();
            KeyEvent event = new KeyEvent(Keyboard.KEY_A, '\0', KeyState.PRESS, 1L);

            Keyboard.addKeyEvent(event);

            assertTrue(Keyboard.next());
            assertEquals(Keyboard.KEY_A, Keyboard.getEventKey());
            assertTrue(Keyboard.getEventSnapshot().isKeyDown(Keyboard.KEY_A));
        } finally {
            drainKeyboard();
            Keyboard.enableRepeatEvents(true);
            InputState.clear();
        }
    }

    @Test
    public void reusedKeyEventKeepsEarlierQueueSnapshot() {
        try {
            InputState.clear();
            drainKeyboard();
            KeyEvent event = new KeyEvent(Keyboard.KEY_A, '\0', KeyState.PRESS, 1L);

            Keyboard.addKeyEvent(event);
            InputState.update(Keyboard.KEY_E, true);
            Keyboard.addKeyEvent(event);

            assertTrue(Keyboard.next());
            assertFalse(Keyboard.getEventSnapshot().isKeyDown(Keyboard.KEY_E));
            assertTrue(Keyboard.next());
            assertTrue(Keyboard.getEventSnapshot().isKeyDown(Keyboard.KEY_E));
        } finally {
            drainKeyboard();
            Keyboard.enableRepeatEvents(true);
            InputState.clear();
        }
    }

    @Test
    public void repeatFilteringAndEventSnapshotArePreserved() {
        try {
            InputState.clear();
            drainKeyboard();
            Keyboard.enableRepeatEvents(false);
            Keyboard.addSdlKeyEvent(Keyboard.KEY_E, 'e', KeyState.REPEAT);
            assertFalse(Keyboard.next());

            Keyboard.enableRepeatEvents(true);
            Keyboard.addSdlKeyEvent(Keyboard.KEY_E, 'e', KeyState.REPEAT);

            assertTrue(Keyboard.next());
            assertTrue(Keyboard.isRepeatEvent());
            assertTrue(Keyboard.getEventSnapshot().isKeyDown(Keyboard.KEY_E));
        } finally {
            drainKeyboard();
            Keyboard.enableRepeatEvents(true);
            InputState.clear();
        }
    }

    @Test
    public void mouseButtonEventsKeepTheirOwnSnapshots() {
        try {
            InputState.clear();
            drainMouse();
            Mouse.addButtonEvent(0, true);
            assertTrue(Mouse.next());
            InputSnapshot pressed = Mouse.getEventSnapshot();
            assertEquals(0, Mouse.getEventButton());
            assertTrue(Mouse.getEventButtonState());
            assertTrue(pressed.isKeyDown(-100));

            Mouse.addButtonEvent(0, false);
            assertTrue(Mouse.next());
            InputSnapshot released = Mouse.getEventSnapshot();

            assertFalse(released.isKeyDown(-100));
            assertNotSame(pressed, released);
            assertTrue(pressed.isKeyDown(-100));
        } finally {
            drainMouse();
            InputState.clear();
        }
    }

    @Test
    public void mouseWheelReusesCurrentSnapshot() {
        double previousFraction = Mouse.fractionalWheelPosition;
        double previousTotal = Mouse.totalScrollAmount;
        try {
            InputState.clear();
            InputState.update(Keyboard.KEY_E, true);
            InputSnapshot current = InputSnapshot.current();
            Mouse.fractionalWheelPosition = 0.0;
            drainMouse();

            Mouse.addWheelEvent(1.0);

            assertTrue(Mouse.next());
            assertEquals(1, Mouse.getEventDWheel());
            assertSame(current, Mouse.getEventSnapshot());
            assertSame(current, InputSnapshot.current());
        } finally {
            Mouse.fractionalWheelPosition = previousFraction;
            Mouse.totalScrollAmount = previousTotal;
            drainMouse();
            InputState.clear();
        }
    }

    @Test
    public void fullQueueKeepsLatestReleaseEvent() {
        try {
            InputState.clear();
            drainKeyboard();
            InputState.update(Keyboard.KEY_E, true);
            fillKeyboardQueue(() -> Keyboard.addKeyEvent(Keyboard.KEY_E, true));

            Keyboard.addKeyEvent(Keyboard.KEY_E, false);

            drainKeyboard();
            assertEquals(Keyboard.KEY_E, Keyboard.getEventKey());
            assertFalse(Keyboard.getEventKeyState());
            assertFalse(Keyboard.getEventSnapshot().isKeyDown(Keyboard.KEY_E));
            assertFalse(InputSnapshot.current().isKeyDown(Keyboard.KEY_E));
        } finally {
            drainKeyboard();
            InputState.clear();
        }
    }

    @Test
    public void fullQueueProducerDoesNotReplaceCurrentEvent() {
        try {
            InputState.clear();
            drainKeyboard();
            InputState.update(Keyboard.KEY_E, true);
            fillKeyboardQueue(() -> Keyboard.addKeyEvent(Keyboard.KEY_E, true));
            final int headKey = Keyboard.getEventKey();
            final long headNano = Keyboard.getEventNanoseconds();

            Keyboard.addKeyEvent(Keyboard.KEY_E, false);

            assertEquals(headKey, Keyboard.getEventKey());
            assertEquals(headNano, Keyboard.getEventNanoseconds());

            drainKeyboard();
            assertEquals(Keyboard.KEY_E, Keyboard.getEventKey());
            assertFalse(Keyboard.getEventKeyState());
        } finally {
            drainKeyboard();
            InputState.clear();
        }
    }

    @Test
    public void concurrentProducerAndConsumerDoNotSpin() throws Exception {
        ExecutorService producer = Executors.newSingleThreadExecutor();
        try {
            InputState.clear();
            drainKeyboard();
            InputState.update(Keyboard.KEY_E, true);
            fillKeyboardQueue(() -> Keyboard.addKeyEvent(Keyboard.KEY_E, true));
            final long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(10);

            Future<Integer> produced = producer.submit(() -> {
                for (int i = 0; i < 500; i++) {
                    Keyboard.addKeyEvent(Keyboard.KEY_E, (i & 1) == 0);
                }
                Keyboard.addKeyEvent(Keyboard.KEY_E, false);
                return 501;
            });

            while (!produced.isDone()) {
                assertTrue("producer spun without finishing", System.nanoTime() < deadline);
                Keyboard.next();
            }
            assertEquals(501, produced.get(5, TimeUnit.SECONDS).intValue());

            drainKeyboard();
            assertEquals(Keyboard.KEY_E, Keyboard.getEventKey());
            assertFalse(Keyboard.getEventKeyState());
        } finally {
            drainKeyboard();
            InputState.clear();
            producer.shutdownNow();
        }
    }

    @Test
    public void fullQueueKeepsLatestReleaseEventThroughSdlPath() {
        try {
            InputState.clear();
            drainKeyboard();
            Keyboard.enableRepeatEvents(true);
            InputState.update(Keyboard.KEY_E, true);
            fillKeyboardQueue(() -> Keyboard.addSdlKeyEvent(Keyboard.KEY_E, 'e', KeyState.REPEAT));

            Keyboard.addSdlKeyEvent(Keyboard.KEY_E, 'e', KeyState.RELEASE);

            drainKeyboard();
            assertEquals(Keyboard.KEY_E, Keyboard.getEventKey());
            assertFalse(Keyboard.getEventKeyState());
            assertFalse(Keyboard.getEventSnapshot().isKeyDown(Keyboard.KEY_E));
            assertFalse(InputSnapshot.current().isKeyDown(Keyboard.KEY_E));
        } finally {
            drainKeyboard();
            InputState.clear();
        }
    }

    @Test
    public void fullQueueDropKeepsCurrentEvent() {
        try {
            InputState.clear();
            drainKeyboard();
            final int currentKey = Keyboard.getEventKey();
            final InputSnapshot currentSnapshot = Keyboard.getEventSnapshot();
            InputState.update(Keyboard.KEY_E, true);
            fillKeyboardQueue(() -> Keyboard.addKeyEvent(Keyboard.KEY_E, true));

            assertEquals(currentKey, Keyboard.getEventKey());
            assertSame(currentSnapshot, Keyboard.getEventSnapshot());

            Keyboard.addKeyEvent(Keyboard.KEY_E, false);

            assertEquals(currentKey, Keyboard.getEventKey());
            assertSame(currentSnapshot, Keyboard.getEventSnapshot());

            drainKeyboard();
            assertEquals(Keyboard.KEY_E, Keyboard.getEventKey());
            assertFalse(Keyboard.getEventKeyState());
            assertFalse(Keyboard.getEventSnapshot().isKeyDown(Keyboard.KEY_E));
        } finally {
            drainKeyboard();
            InputState.clear();
        }
    }

    @Test
    public void concurrentUpdatesKeepBothTransitions() throws InterruptedException {
        try {
            InputState.clear();
            for (int round = 0; round < 200; round++) {
                InputState.clear();
                CountDownLatch start = new CountDownLatch(1);
                Thread first = new Thread(() -> {
                    await(start);
                    InputState.update(Keyboard.KEY_A, true);
                });
                Thread second = new Thread(() -> {
                    await(start);
                    InputState.update(Keyboard.KEY_B, true);
                });
                first.start();
                second.start();
                start.countDown();
                first.join();
                second.join();

                InputSnapshot snapshot = InputSnapshot.current();
                assertTrue("round " + round + " lost KEY_A", snapshot.isKeyDown(Keyboard.KEY_A));
                assertTrue("round " + round + " lost KEY_B", snapshot.isKeyDown(Keyboard.KEY_B));
            }
        } finally {
            InputState.clear();
        }
    }

    private static void await(CountDownLatch latch) {
        try {
            latch.await();
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
    }

    /** Adds events until the queue stops growing, which is what tells a 128-slot queue is full. */
    private static void fillKeyboardQueue(Runnable enqueue) {
        int size = Keyboard.getNumKeyboardEvents();
        while (true) {
            enqueue.run();
            int next = Keyboard.getNumKeyboardEvents();
            if (next == size) {
                return;
            }
            size = next;
        }
    }

    private static void drainKeyboard() {
        while (Keyboard.next()) {}
    }

    private static void drainMouse() {
        while (Mouse.next()) {}
    }
}
