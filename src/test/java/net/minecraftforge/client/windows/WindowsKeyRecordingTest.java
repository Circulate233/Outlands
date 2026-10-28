package net.minecraftforge.client.windows;

import java.util.List;

import net.minecraftforge.client.settings.KeyCombination;
import net.minecraftforge.client.settings.KeyCombinationRecorder;
import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

class WindowsKeyRecordingTest {

    @Test
    void completesCtrlSpaceFromPhysicalStateWhenSpaceMessageIsMissing() {
        KeyCombinationRecorder recorder = new KeyCombinationRecorder();
        recorder.press(Keyboard.KEY_LCONTROL);

        assertNull(WindowsKeyRecording.poll(recorder, () -> true));
        assertNull(recorder.release(Keyboard.KEY_LCONTROL));

        KeyCombination completed = WindowsKeyRecording.poll(recorder, () -> false);
        assertEquals(
            KeyCombination.fromChord(Keyboard.KEY_SPACE, List.of(Keyboard.KEY_LCONTROL, Keyboard.KEY_SPACE)),
            completed
        );
    }

    @Test
    void physicalSpaceStateIsIdempotentWithKeyboardEvents() {
        KeyCombinationRecorder recorder = new KeyCombinationRecorder();
        recorder.press(Keyboard.KEY_LCONTROL);
        recorder.press(Keyboard.KEY_SPACE);

        assertNull(WindowsKeyRecording.poll(recorder, () -> true));
        assertNull(recorder.release(Keyboard.KEY_LCONTROL));

        assertEquals(
            KeyCombination.fromChord(Keyboard.KEY_SPACE, List.of(Keyboard.KEY_LCONTROL, Keyboard.KEY_SPACE)),
            WindowsKeyRecording.poll(recorder, () -> false)
        );
    }
}
