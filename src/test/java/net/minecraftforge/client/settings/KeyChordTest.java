package net.minecraftforge.client.settings;

import net.minecraft.client.settings.KeyBinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Chord normalisation is the part of the feature that can be checked without a game: everything else needs the
 * client's key state, but what a chord *is* has to be decided before any key is pressed.
 */
class KeyChordTest {

    @Test
    void chordKeysAreSortedAndDeduplicated() {
        final KeyBinding binding = new KeyBinding("key.test.chord", 34, "key.categories.misc");
        binding.setChordKeys(KeyboardProbe.LSHIFT, KeyboardProbe.LALT, KeyboardProbe.LSHIFT);

        assertArrayEquals(new int[] { KeyboardProbe.LSHIFT, KeyboardProbe.LALT }, binding.getChordKeys(),
            "order and repeats must not decide whether two chords are the same chord");
        assertTrue(binding.hasChord());
    }

    @Test
    void theMainKeyCannotBeItsOwnChordKey() {
        final KeyBinding binding = new KeyBinding("key.test.self", KeyboardProbe.G, "key.categories.misc");
        binding.setChordKeys(KeyboardProbe.G, KeyboardProbe.LCTRL);

        assertArrayEquals(new int[] { KeyboardProbe.LCTRL }, binding.getChordKeys());
    }

    @Test
    void anEmptyChordIsNoChord() {
        final KeyBinding binding = new KeyBinding("key.test.empty", KeyboardProbe.G, "key.categories.misc");
        binding.setChordKeys();
        assertFalse(binding.hasChord());
        assertArrayEquals(new int[0], binding.getChordKeys());

        binding.setChordKeys(KeyboardProbe.LCTRL);
        binding.setChordKeys((int[]) null);
        assertFalse(binding.hasChord(), "clearing a chord has to work as well as never setting one");
    }

    @Test
    void defaultsAreKeptApartFromTheCurrentChord() {
        final KeyBinding binding = new KeyBinding("key.test.defaults", KeyboardProbe.G, "key.categories.misc");
        binding.setDefaultChordKeys(KeyboardProbe.LCTRL);
        binding.setChordKeys(KeyboardProbe.LALT);

        assertArrayEquals(new int[] { KeyboardProbe.LCTRL }, binding.getChordKeysDefault());
        assertArrayEquals(new int[] { KeyboardProbe.LALT }, binding.getChordKeys());
        assertFalse(binding.isSetToDefaultValue());

        binding.setToDefault();
        assertArrayEquals(new int[] { KeyboardProbe.LCTRL }, binding.getChordKeys());
        assertTrue(binding.isSetToDefaultValue());
    }

    /** Key codes as LWJGL2 numbers, so the test does not need the input layer to be initialised. */
    private static final class KeyboardProbe {
        static final int LCTRL = 29;
        static final int LSHIFT = 42;
        static final int LALT = 56;
        static final int G = 34;
    }
}
