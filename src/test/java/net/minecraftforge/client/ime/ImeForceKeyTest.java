package net.minecraftforge.client.ime;

import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.settings.GameSettings;
import net.minecraftforge.client.settings.KeyCombination;
import net.minecraftforge.client.settings.KeyModifier;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ImeForceKeyTest {

    @Test
    void migratesOnlyLegacyImeBindings() {
        KeyCombination legacy = ImeForceKey.fromOptions("57");
        assertTrue(legacy.requires(KeyModifier.CONTROL));
        assertEquals(Keyboard.KEY_SPACE, legacy.getKeyCode());

        KeyCombination legacyCustom = ImeForceKey.fromOptions("34:SHIFT");
        assertTrue(legacyCustom.requires(KeyModifier.CONTROL));
        assertEquals(Keyboard.KEY_G, legacyCustom.getKeyCode());

        KeyCombination explicitSingle = ImeForceKey.fromOptions("chord:57::");
        assertFalse(explicitSingle.requires(KeyModifier.CONTROL));
        assertEquals(explicitSingle, KeyCombination.parse(ImeForceKey.toOptions(explicitSingle)));
        assertEquals(KeyCombination.unbound(), ImeForceKey.fromOptions("0"));
    }

    @Test
    void legacyControlTriggerDoesNotBecomeADuplicateChord() {
        KeyCombination combination = ImeForceKey.fromOptions(Integer.toString(Keyboard.KEY_RCONTROL));

        assertEquals(Keyboard.KEY_RCONTROL, combination.getKeyCode());
        assertEquals(1, combination.size());
        assertFalse(combination.requires(KeyModifier.CONTROL));
    }

    @Test
    void suppressesForceKeyWhileAControlBindingIsBeingRecorded() {
        GuiControls controls = new GuiControls(null, new GameSettings());
        controls.buttonId = ImeForceKey.FORCE_IME;

        assertTrue(ImeForceKey.isRecording(controls));

        controls.buttonId = null;
        assertFalse(ImeForceKey.isRecording(controls));
    }
}
