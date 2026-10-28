package org.lwjgl.input;

/**
 * One entry of the key queue.
 * <p>
 * The four original fields are what LWJGL2 exposed, and {@code aChar} is still the character a key produced. Three
 * more are carried because the moment a key changes is the only moment they are true: the physical key, the modifier
 * state, and the whole keyboard and mouse state that a key combination has to be checked against.
 */
public class KeyEvent {

    public int key;
    public char aChar;
    public KeyState state;
    public long nano;

    /**
     * LWJGL2 scan code, i.e. the physical key independent of the keyboard layout. For most keys it equals
     * {@link #key}; they differ where the layout moves a character off its usual position.
     */
    public int scancode;

    /**
     * Modifier state at the moment of the event, as an {@code SDL_KMOD_*} bit mask. Unlike asking
     * {@link Keyboard#isKeyDown(int)} later, this cannot be wrong: it is a snapshot, so a chord stays correct even
     * when the player lets go of the modifier before the game acts on the key.
     */
    public short mods;

    /** Everything that was held when this event was queued, for checking a whole key combination. */
    final InputSnapshot snapshot;

    public KeyEvent(int key, char c, KeyState state, long nano) {
        this(key, c, state, nano, 0, (short) 0, InputSnapshot.current());
    }

    public KeyEvent(int key, char c, KeyState state, long nano, int scancode, short mods) {
        this(key, c, state, nano, scancode, mods, InputSnapshot.current());
    }

    KeyEvent(int key, char c, KeyState state, long nano, InputSnapshot snapshot) {
        this(key, c, state, nano, 0, (short) 0, snapshot);
    }

    KeyEvent(int key, char c, KeyState state, long nano, int scancode, short mods, InputSnapshot snapshot) {
        this.key = key;
        this.aChar = c;
        this.state = state;
        this.nano = nano;
        this.scancode = scancode;
        this.mods = mods;
        this.snapshot = snapshot;
    }
}
