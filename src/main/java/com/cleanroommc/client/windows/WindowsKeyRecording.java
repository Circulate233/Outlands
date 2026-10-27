package com.cleanroommc.client.windows;

import java.util.function.BooleanSupplier;

import com.sun.jna.platform.win32.User32;
import net.minecraftforge.client.settings.KeyCombination;
import net.minecraftforge.client.settings.KeyCombinationRecorder;
import org.lwjgl.input.Keyboard;

/** Supplements key recording for Space, whose message Windows may consume as an IME shortcut. */
public final class WindowsKeyRecording {

    private static final int VK_SPACE = 0x20;
    private static final int KEY_DOWN_MASK = 0x8000;

    private WindowsKeyRecording() {}

    public static KeyCombination poll(KeyCombinationRecorder recorder) {
        return poll(recorder, () -> (User32.INSTANCE.GetAsyncKeyState(VK_SPACE) & KEY_DOWN_MASK) != 0);
    }

    static KeyCombination poll(KeyCombinationRecorder recorder, BooleanSupplier spaceDown) {
        if (spaceDown.getAsBoolean()) {
            recorder.press(Keyboard.KEY_SPACE);
            return null;
        }
        return recorder.release(Keyboard.KEY_SPACE);
    }
}
