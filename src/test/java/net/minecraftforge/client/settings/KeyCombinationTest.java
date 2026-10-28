package net.minecraftforge.client.settings;

import java.util.List;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.InputSnapshot;
import org.lwjgl.input.InputState;
import org.lwjgl.input.Keyboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyCombinationTest {

    @AfterEach
    void clearInput() {
        InputState.clear();
    }

    @Test
    void matchesWholeChordButTriggersOnlyOnFinalKey() {
        KeyCombination combo = KeyCombination.fromChord(
            Keyboard.KEY_E,
            List.of(Keyboard.KEY_LCONTROL, Keyboard.KEY_G, Keyboard.KEY_E)
        );
        InputState.update(Keyboard.KEY_RCONTROL, true);
        InputState.update(Keyboard.KEY_G, true);
        InputState.update(Keyboard.KEY_E, true);
        InputSnapshot snapshot = InputSnapshot.current();

        assertTrue(combo.matches(snapshot));
        assertTrue(combo.matchesEvent(Keyboard.KEY_E, snapshot));
        assertFalse(combo.matchesEvent(Keyboard.KEY_G, snapshot));

        InputState.update(Keyboard.KEY_G, false);
        assertFalse(combo.matches(InputSnapshot.current()));
    }

    @Test
    void matchesMultipleModifiersAndMouseButtons() {
        KeyCombination combo = KeyCombination.fromChord(
            -100,
            List.of(Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT, Keyboard.KEY_LMENU, Keyboard.KEY_G, -100)
        );
        InputState.update(Keyboard.KEY_RCONTROL, true);
        InputState.update(Keyboard.KEY_RSHIFT, true);
        InputState.update(Keyboard.KEY_RMENU, true);
        InputState.update(Keyboard.KEY_G, true);
        InputState.update(-100, true);

        assertTrue(combo.matches(InputSnapshot.current()));
        assertTrue(combo.matchesEvent(-100, InputSnapshot.current()));
        assertEquals(5, combo.size());

        InputState.update(Keyboard.KEY_RMENU, false);
        assertFalse(combo.matches(InputSnapshot.current()));
    }

    @Test
    void legacyAndChordFormatsRoundTrip() {
        KeyCombination legacy = KeyCombination.of(Keyboard.KEY_E, KeyModifier.CONTROL);
        KeyCombination chord = KeyCombination.fromChord(
            Keyboard.KEY_E,
            List.of(Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT, Keyboard.KEY_G, Keyboard.KEY_E)
        );

        assertEquals("18:CONTROL", legacy.serialize());
        assertEquals(legacy, KeyCombination.parse(legacy.serialize()));
        assertEquals(chord, KeyCombination.parse(chord.serializeChord()));
        assertEquals(KeyCombination.unbound(), KeyCombination.parse("0"));
        assertEquals("chord:57::", KeyCombination.of(Keyboard.KEY_SPACE, KeyModifier.NONE).serializeChord());
    }

    @Test
    void rejectsInvalidCombinations() {
        assertThrows(IllegalArgumentException.class, () -> KeyCombination.fromChord(
            Keyboard.KEY_E,
            List.of(Keyboard.KEY_LCONTROL, Keyboard.KEY_E, Keyboard.KEY_E)
        ));
        assertThrows(IllegalArgumentException.class, () -> KeyCombination.fromChord(
            Keyboard.KEY_E,
            List.of(Keyboard.KEY_G)
        ));
        assertThrows(IllegalArgumentException.class, () -> KeyCombination.parse("chord:18:CONTROL:34,34"));
        assertThrows(IllegalArgumentException.class, () -> KeyCombination.parse("chord:18:UNKNOWN:"));
        assertThrows(IllegalArgumentException.class, () -> KeyCombination.parse("chord:0:CONTROL:"));
        assertThrows(IllegalArgumentException.class, () -> KeyCombination.parse("chord:18:CONTROL"));
    }

    @Test
    void comparesRequiredKeysIndependentlyOfTriggerOrder() {
        KeyCombination shorter = KeyCombination.fromChord(Keyboard.KEY_E, List.of(Keyboard.KEY_E));
        KeyCombination longer = KeyCombination.fromChord(
            Keyboard.KEY_K,
            List.of(Keyboard.KEY_G, Keyboard.KEY_E, Keyboard.KEY_K)
        );
        KeyCombination sameKeysDifferentTrigger = KeyCombination.fromChord(
            Keyboard.KEY_E,
            List.of(Keyboard.KEY_G, Keyboard.KEY_E, Keyboard.KEY_K)
        );

        assertTrue(shorter.isSubsetOf(longer));
        assertFalse(longer.isSubsetOf(shorter));
        assertTrue(longer.hasSameKeys(sameKeysDifferentTrigger));
        assertFalse(longer.equals(sameKeysDifferentTrigger));
    }
}
