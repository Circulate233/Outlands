package org.lwjgl.input;

import java.util.BitSet;

/** Maintains the current input snapshot from the client input thread. */
public final class InputState {

    private InputState() {}

    /**
     * Applies one transition atomically while snapshot readers remain lock-free.
     *
     * @param keyCode an LWJGL2 keycode supported by {@link InputSnapshot#isKeyDown(int)}
     * @param down the new pressed state
     */
    public static synchronized void update(int keyCode, boolean down) {
        final InputSnapshot previous = InputSnapshot.current();
        final int bit = InputSnapshot.bitIndex(keyCode);
        if (bit < 0 || previous.isKeyDown(keyCode) == down) {
            return;
        }
        final BitSet keys = previous.copyKeys();
        keys.set(bit, down);
        InputSnapshot.setCurrent(new InputSnapshot(keys));
    }

    /** Clears every pressed key without changing snapshots already attached to events. */
    public static synchronized void clear() {
        if (InputSnapshot.current().isEmpty()) {
            return;
        }
        InputSnapshot.setCurrent(new InputSnapshot(new BitSet()));
    }

    /** Rebuilds the current snapshot from SDL-backed keyboard and mouse state queries. */
    public static synchronized void synchronize() {
        final BitSet keys = new BitSet();
        for (int key = 1; key <= 255; key++) {
            if (Keyboard.isKeyDown(key)) {
                keys.set(key);
            }
        }
        for (int button = 0; button < Mouse.getButtonCount(); button++) {
            final int bit = InputSnapshot.bitIndex(button - 100);
            if (bit >= 0 && Mouse.isButtonDown(button)) {
                keys.set(bit);
            }
        }
        if (!InputSnapshot.current().equalsKeys(keys)) {
            InputSnapshot.setCurrent(new InputSnapshot(keys));
        }
    }
}
