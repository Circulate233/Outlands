package net.minecraftforge.client.settings;

import java.util.LinkedHashSet;
import java.util.Set;

import javax.annotation.Nullable;

/** Collects one chord without applying it to a KeyBinding before the final release. */
public final class KeyCombinationRecorder {
    private final Set<Integer> heldKeys = new LinkedHashSet<>();
    private final Set<Integer> recordedKeys = new LinkedHashSet<>();
    private int triggerKeyCode;

    /** Adds a physical key once; repeated press events do not change the trigger. */
    public void press(int keyCode) {
        if (keyCode == 0) {
            throw new IllegalArgumentException("Cannot record an empty key");
        }
        if (heldKeys.add(keyCode)) {
            recordedKeys.add(keyCode);
            triggerKeyCode = keyCode;
        }
    }

    /** Returns the completed chord after the last held key is released, otherwise null. */
    @Nullable
    public KeyCombination release(int keyCode) {
        if (!heldKeys.remove(keyCode) || !heldKeys.isEmpty()) {
            return null;
        }
        final KeyCombination completed = KeyCombination.fromChord(triggerKeyCode, recordedKeys);
        reset();
        return completed;
    }

    public boolean isEmpty() {
        return recordedKeys.isEmpty();
    }

    @Nullable
    public KeyCombination getPending() {
        return isEmpty() ? null : KeyCombination.fromChord(triggerKeyCode, recordedKeys);
    }

    public void reset() {
        heldKeys.clear();
        recordedKeys.clear();
        triggerKeyCode = 0;
    }
}
