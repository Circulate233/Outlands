package org.lwjgl.input;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;

/**
 * The key event is handed to mods, so the shape LWJGL2 gave them has to keep working exactly as it did while the
 * two fields SDL can supply are carried alongside.
 */
class KeyEventTest {

    @Test
    void theOriginalShapeStillWorks() {
        final KeyEvent event = new KeyEvent(Keyboard.KEY_A, 'a', KeyState.PRESS, 42L);

        assertEquals(Keyboard.KEY_A, event.key);
        assertEquals('a', event.aChar);
        assertSame(KeyState.PRESS, event.state);
        assertEquals(42L, event.nano);
        assertEquals(0, event.scancode, "an event without a physical key reports none");
        assertEquals(0, event.mods, "an event without modifiers reports none");
    }

    @Test
    void theSdlShapeCarriesThePhysicalKeyAndModifiers() {
        final short mods = org.lwjgl.sdl.SDLKeycode.SDL_KMOD_LCTRL | org.lwjgl.sdl.SDLKeycode.SDL_KMOD_LSHIFT;
        final KeyEvent event = new KeyEvent(Keyboard.KEY_G, 'g', KeyState.PRESS, 7L, Keyboard.KEY_G, mods);

        assertEquals(Keyboard.KEY_G, event.key);
        assertEquals('g', event.aChar);
        assertSame(KeyState.PRESS, event.state);
        assertEquals(7L, event.nano);
        assertEquals(Keyboard.KEY_G, event.scancode);
        assertEquals(mods, event.mods);
    }

    @Test
    void everyStateIsDistinguishable() {
        assertEquals(false, KeyState.RELEASE.isPressed);
        assertEquals(true, KeyState.PRESS.isPressed);
        assertEquals(true, KeyState.REPEAT.isPressed, "a repeat still counts as held for key state purposes");
    }
}
