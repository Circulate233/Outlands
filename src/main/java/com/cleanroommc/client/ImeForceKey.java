package com.cleanroommc.client;

import java.util.List;

import net.minecraft.client.Minecraft;
import net.minecraft.client.gui.GuiControls;
import net.minecraft.client.settings.KeyBinding;
import net.minecraftforge.client.settings.KeyCombination;
import net.minecraftforge.client.settings.KeyModifier;
import net.minecraftforge.fml.client.registry.ClientRegistry;
import org.lwjgl.input.InputSnapshot;
import org.lwjgl.input.Keyboard;

/** Opens SDL text input from a configurable chord, including screens that suppress normal key input events. */
public final class ImeForceKey {

    public static final KeyBinding FORCE_IME = new KeyBinding(
        "key.cleanroommc.forceIme",
        KeyCombination.fromChord(Keyboard.KEY_SPACE, List.of(Keyboard.KEY_LCONTROL, Keyboard.KEY_SPACE)),
        "key.categories.cleanroommc"
    );

    private ImeForceKey() {}

    public static void register() {
        ClientRegistry.registerKeyBinding(FORCE_IME);
    }

    /** Handles a non-repeat SDL key press with the complete event-time input state. */
    public static void onKeyDown(int lwjglKey, InputSnapshot snapshot) {
        if (Minecraft.getMinecraft().currentScreen instanceof GuiControls
            && isRecording((GuiControls) Minecraft.getMinecraft().currentScreen)) {
            return;
        }
        final KeyCombination combination = FORCE_IME.getKeyCombination();
        if (!combination.matchesEvent(lwjglKey, snapshot)) {
            return;
        }
        if (combination.requires(KeyModifier.CONTROL)) {
            if (!combination.requires(KeyModifier.ALT) && KeyModifier.ALT.isActive(snapshot)) {
                return;
            }
            if (hasUnrequiredMeta(combination, snapshot)) {
                return;
            }
        }
        TextFieldHandler.forceTextInput();
    }

    static boolean isRecording(GuiControls controls) {
        return controls.buttonId != null;
    }

    private static boolean hasUnrequiredMeta(KeyCombination combination, InputSnapshot snapshot) {
        if (!snapshot.isKeyDown(Keyboard.KEY_LMETA) && !snapshot.isKeyDown(Keyboard.KEY_RMETA)) {
            return false;
        }
        if (Minecraft.IS_RUNNING_ON_MAC && combination.requires(KeyModifier.CONTROL)) {
            return false;
        }
        if (isMeta(combination.getKeyCode())) {
            return false;
        }
        for (int keyCode : combination.getAdditionalKeys()) {
            if (isMeta(keyCode)) {
                return false;
            }
        }
        return true;
    }

    private static boolean isMeta(int keyCode) {
        return keyCode == Keyboard.KEY_LMETA || keyCode == Keyboard.KEY_RMETA;
    }

    /** Migrates the legacy hardcoded-Ctrl binding and preserves explicit chord configurations. */
    public static KeyCombination fromOptions(String value) {
        final KeyCombination parsed = KeyCombination.parse(value);
        if (value.startsWith("chord:") || parsed.getKeyCode() == 0) {
            return parsed;
        }
        if (KeyModifier.CONTROL.matches(parsed.getKeyCode())) {
            return KeyCombination.of(parsed.getKeyCode(), KeyModifier.NONE);
        }
        return KeyCombination.fromChord(parsed.getKeyCode(), List.of(Keyboard.KEY_LCONTROL, parsed.getKeyCode()));
    }

    /** Marks this binding explicit so a new single-key rebind is not migrated again. */
    public static String toOptions(KeyCombination combination) {
        return combination.serializeChord();
    }
}
