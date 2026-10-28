package net.minecraftforge.client.settings;

import java.util.List;
import java.util.Set;

import net.minecraft.client.settings.KeyBinding;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.InputSnapshot;
import org.lwjgl.input.InputState;
import org.lwjgl.input.Keyboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyBindingMapTest {

    @AfterEach
    void clearInput() {
        InputState.clear();
    }

    @Test
    void mostSpecificChordWinsForOneTriggerEvent() {
        KeyBinding plain = binding("key.test.plain", KeyCombination.of(Keyboard.KEY_E, KeyModifier.NONE));
        KeyBinding control = binding("key.test.control", KeyCombination.of(Keyboard.KEY_E, KeyModifier.CONTROL));
        KeyBinding controlShift = binding("key.test.controlShift", KeyCombination.fromChord(
            Keyboard.KEY_E,
            List.of(Keyboard.KEY_LCONTROL, Keyboard.KEY_LSHIFT, Keyboard.KEY_E)
        ));
        KeyBindingMap map = new KeyBindingMap();
        map.addKey(plain.getKeyCode(), plain);
        map.addKey(control.getKeyCode(), control);
        map.addKey(controlShift.getKeyCode(), controlShift);

        InputState.update(Keyboard.KEY_E, true);
        assertEquals(Set.of(plain), map.lookupActives(Keyboard.KEY_E, InputSnapshot.current()));

        InputState.update(Keyboard.KEY_LCONTROL, true);
        assertEquals(Set.of(control), map.lookupActives(Keyboard.KEY_E, InputSnapshot.current()));

        InputState.update(Keyboard.KEY_LSHIFT, true);
        assertEquals(Set.of(controlShift), map.lookupActives(Keyboard.KEY_E, InputSnapshot.current()));
    }

    @Test
    void sameLayerBindingsShareTheTrigger() {
        KeyCombination chord = KeyCombination.of(Keyboard.KEY_E, KeyModifier.CONTROL);
        KeyBinding first = binding("key.test.same.first", chord);
        KeyBinding second = binding("key.test.same.second", chord);
        KeyBindingMap map = new KeyBindingMap();
        map.addKey(first.getKeyCode(), first);
        map.addKey(second.getKeyCode(), second);
        InputState.update(Keyboard.KEY_LCONTROL, true);
        InputState.update(Keyboard.KEY_E, true);

        assertEquals(Set.of(first, second), map.lookupActives(Keyboard.KEY_E, InputSnapshot.current()));
    }

    @Test
    void repeatDoesNotTriggerAgainAndReleaseEndsHeldState() {
        KeyBinding binding = binding("key.test.repeat", KeyCombination.fromChord(
            Keyboard.KEY_F19,
            List.of(Keyboard.KEY_G, Keyboard.KEY_F19)
        ));
        InputState.update(Keyboard.KEY_G, true);
        InputState.update(Keyboard.KEY_F19, true);

        KeyBinding.onInputEvent(Keyboard.KEY_F19, true, false, InputSnapshot.current());
        assertTrue(binding.isPressed());
        assertTrue(binding.isKeyDown(InputSnapshot.current()));

        KeyBinding.onInputEvent(Keyboard.KEY_F19, true, true, InputSnapshot.current());
        assertFalse(binding.isPressed());

        InputState.update(Keyboard.KEY_G, false);
        KeyBinding.onInputEvent(Keyboard.KEY_G, false, false, InputSnapshot.current());
        assertFalse(binding.isKeyDown(InputSnapshot.current()));
    }

    @Test
    void nestedChordsCanKeepTheirHeldStatesTogether() {
        KeyBinding plain = binding("key.test.held.plain", KeyCombination.of(Keyboard.KEY_E, KeyModifier.NONE));
        KeyBinding control = binding("key.test.held.control", KeyCombination.of(Keyboard.KEY_E, KeyModifier.CONTROL));
        InputState.update(Keyboard.KEY_LCONTROL, true);
        InputState.update(Keyboard.KEY_E, true);
        KeyBinding.onInputEvent(Keyboard.KEY_E, true, false, InputSnapshot.current());

        assertTrue(plain.isKeyDown(InputSnapshot.current()));
        assertTrue(control.isKeyDown(InputSnapshot.current()));
        assertTrue(control.isPressed());
        assertFalse(plain.isPressed());
    }

    @Test
    void conflictSeverityFollowsActualTriggerOverlap() {
        KeyBinding plain = binding("key.test.conflict.plain", KeyCombination.of(Keyboard.KEY_E, KeyModifier.NONE));
        KeyBinding control = binding("key.test.conflict.control", KeyCombination.of(Keyboard.KEY_E, KeyModifier.CONTROL));
        KeyBinding duplicate = binding("key.test.conflict.duplicate", KeyCombination.of(Keyboard.KEY_E, KeyModifier.CONTROL));
        KeyBinding shift = binding("key.test.conflict.shift", KeyCombination.of(Keyboard.KEY_E, KeyModifier.SHIFT));

        assertTrue(control.conflicts(duplicate));
        assertFalse(control.hasPartialConflict(duplicate));
        assertTrue(plain.conflicts(control));
        assertTrue(plain.hasPartialConflict(control));
        assertTrue(control.conflicts(shift));
        assertFalse(control.hasPartialConflict(shift));

        KeyBinding reordered = binding("key.test.conflict.reordered", KeyCombination.fromChord(
            Keyboard.KEY_G,
            List.of(Keyboard.KEY_LCONTROL, Keyboard.KEY_E, Keyboard.KEY_G)
        ));
        KeyBinding sameKeysOtherTrigger = binding("key.test.conflict.sameKeys", KeyCombination.fromChord(
            Keyboard.KEY_E,
            List.of(Keyboard.KEY_LCONTROL, Keyboard.KEY_E, Keyboard.KEY_G)
        ));
        assertTrue(reordered.conflicts(sameKeysOtherTrigger));
        assertTrue(reordered.hasPartialConflict(sameKeysOtherTrigger));
        assertTrue(plain.conflicts(reordered));
        assertTrue(plain.hasPartialConflict(reordered));

        KeyBinding unrelated = binding("key.test.conflict.unrelated", KeyCombination.of(Keyboard.KEY_X, KeyModifier.NONE));
        assertFalse(plain.conflicts(unrelated));
    }

    @Test
    void legacyModifierApiAdaptsWithoutChangingMeaning() {
        KeyBinding legacy = new KeyBinding(
            "key.test.legacy",
            KeyConflictContext.UNIVERSAL,
            KeyModifier.SHIFT,
            Keyboard.KEY_E,
            "key.categories.misc"
        );

        assertEquals(Keyboard.KEY_E, legacy.getKeyCode());
        assertEquals(KeyModifier.SHIFT, legacy.getKeyModifier());
        assertEquals(KeyCombination.of(Keyboard.KEY_E, KeyModifier.SHIFT), legacy.getKeyCombination());
        assertTrue(legacy.isSetToDefaultValue());

        legacy.setKeyCombination(KeyCombination.fromChord(
            Keyboard.KEY_F19,
            List.of(Keyboard.KEY_G, Keyboard.KEY_F19)
        ));
        assertFalse(legacy.isSetToDefaultValue());
        assertEquals(KeyModifier.NONE, legacy.getKeyModifier());
        legacy.setToDefault();
        assertEquals(KeyCombination.of(Keyboard.KEY_E, KeyModifier.SHIFT), legacy.getKeyCombination());

        legacy.setKeyCode(300);
        assertEquals(KeyCombination.of(300, KeyModifier.SHIFT), legacy.getKeyCombination());
    }

    private static KeyBinding binding(String description, KeyCombination combination) {
        return new KeyBinding(description, KeyConflictContext.UNIVERSAL, combination, "key.categories.misc");
    }
}
