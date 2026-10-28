package net.minecraftforge.client.settings;

import java.util.List;

import org.junit.jupiter.api.Test;
import org.lwjgl.input.Keyboard;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

class KeyCombinationRecorderTest {

    @Test
    void recordsChordUntilAllKeysAreReleased() {
        KeyCombinationRecorder recorder = new KeyCombinationRecorder();
        recorder.press(Keyboard.KEY_LCONTROL);
        recorder.press(Keyboard.KEY_G);
        recorder.press(Keyboard.KEY_E);
        recorder.press(Keyboard.KEY_G);
        KeyCombination expected = KeyCombination.fromChord(
            Keyboard.KEY_E,
            List.of(Keyboard.KEY_LCONTROL, Keyboard.KEY_G, Keyboard.KEY_E)
        );

        assertEquals(expected, recorder.getPending());
        assertNull(recorder.release(Keyboard.KEY_E));
        assertNull(recorder.release(Keyboard.KEY_G));
        assertEquals(expected, recorder.release(Keyboard.KEY_LCONTROL));
        assertTrue(recorder.isEmpty());
        assertNull(recorder.getPending());
    }

    @Test
    void singleKeyCompletesOnRelease() {
        KeyCombinationRecorder recorder = new KeyCombinationRecorder();
        recorder.press(Keyboard.KEY_G);

        assertEquals(KeyCombination.of(Keyboard.KEY_G, KeyModifier.NONE), recorder.release(Keyboard.KEY_G));
        assertTrue(recorder.isEmpty());
    }

    @Test
    void startingMouseReleaseDoesNotCompleteAnEmptyRecording() {
        KeyCombinationRecorder recorder = new KeyCombinationRecorder();

        assertNull(recorder.release(-100));
        assertTrue(recorder.isEmpty());
        assertFalse(recorder.release(Keyboard.KEY_G) != null);
    }

    @Test
    void resetDropsUncommittedInput() {
        KeyCombinationRecorder recorder = new KeyCombinationRecorder();
        recorder.press(Keyboard.KEY_LCONTROL);
        recorder.press(Keyboard.KEY_G);

        recorder.reset();

        assertTrue(recorder.isEmpty());
        assertNull(recorder.getPending());
        assertNull(recorder.release(Keyboard.KEY_G));
    }
}
