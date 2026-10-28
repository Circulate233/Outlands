package org.lwjgl.input;

import java.util.BitSet;

/**
 * Immutable LWJGL2 keyboard and mouse button state from one point in the input stream.
 * The current reference may be read from any thread.
 */
public final class InputSnapshot {

    private static volatile InputSnapshot current = new InputSnapshot(new BitSet());

    private final BitSet keys;

    InputSnapshot(BitSet keys) {
        this.keys = (BitSet) keys.clone();
    }

    /**
     * Returns the latest input snapshot.
     *
     * @return an immutable snapshot whose contents do not change after later input
     */
    public static InputSnapshot current() {
        return current;
    }

    /**
     * Queries an LWJGL2 keyboard key or negative mouse button keycode.
     *
     * @param keyCode keyboard keycodes 1 through 255, or mouse keycodes -100 through -1
     * @return whether the key was down; unsupported keycodes always return false
     */
    public boolean isKeyDown(int keyCode) {
        final int bit = bitIndex(keyCode);
        return bit >= 0 && keys.get(bit);
    }

    static int bitIndex(int keyCode) {
        if (keyCode > 0 && keyCode <= 255) {
            return keyCode;
        }
        if (keyCode < 0 && keyCode >= -100) {
            return 256 + keyCode + 100;
        }
        return -1;
    }

    BitSet copyKeys() {
        return (BitSet) keys.clone();
    }

    boolean isEmpty() {
        return keys.isEmpty();
    }

    boolean equalsKeys(BitSet other) {
        return keys.equals(other);
    }

    static void setCurrent(InputSnapshot snapshot) {
        current = snapshot;
    }
}
