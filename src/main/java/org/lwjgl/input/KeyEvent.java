package org.lwjgl.input;

/**
 * One entry of the key queue.
 * <p>
 * The four original fields are what LWJGL2 exposed, and {@code aChar} is still the character a key produced. Two
 * more are carried because SDL gives them to us for free at the moment the key changed and there is no way to
 * recover them afterwards: the physical key and the modifier state.
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

    public KeyEvent(int key, char c, KeyState state, long nano) {
        this(key, c, state, nano, 0, (short) 0);
    }

    public KeyEvent(int key, char c, KeyState state, long nano, int scancode, short mods) {
        this.key = key;
        this.aChar = c;
        this.state = state;
        this.nano = nano;
        this.scancode = scancode;
        this.mods = mods;
    }
}
