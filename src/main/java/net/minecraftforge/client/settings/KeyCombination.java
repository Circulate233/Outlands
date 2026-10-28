package net.minecraftforge.client.settings;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.Collections;
import java.util.EnumSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.TreeSet;

import net.minecraft.client.settings.GameSettings;
import org.lwjgl.input.InputSnapshot;

/**
 * Immutable chord made of a trigger key and additional held keys. Legacy Ctrl/Shift/Alt modifiers remain
 * side-independent; other keyboard keys and mouse buttons use their LWJGL2 keycodes.
 */
public final class KeyCombination {

    private static final String CHORD_PREFIX = "chord:";
    private static final KeyCombination UNBOUND = new KeyCombination(
        0,
        EnumSet.noneOf(KeyModifier.class),
        new int[0]
    );

    private final int triggerKeyCode;
    private final EnumSet<KeyModifier> modifiers;
    private final int[] additionalKeys;

    private KeyCombination(int triggerKeyCode, EnumSet<KeyModifier> modifiers, int[] additionalKeys) {
        this.triggerKeyCode = triggerKeyCode;
        this.modifiers = modifiers.clone();
        this.additionalKeys = additionalKeys.clone();
    }

    /** Returns the empty binding. */
    public static KeyCombination unbound() {
        return UNBOUND;
    }

    /** Adapts the legacy single-key, single-modifier representation. */
    public static KeyCombination of(int keyCode, KeyModifier modifier) {
        Objects.requireNonNull(modifier, "modifier");
        if (keyCode == 0) {
            if (modifier == KeyModifier.NONE) {
                return unbound();
            }
            throw new IllegalArgumentException("An unbound key cannot require " + modifier);
        }
        final EnumSet<KeyModifier> modifiers = EnumSet.noneOf(KeyModifier.class);
        if (modifier != KeyModifier.NONE && !modifier.matches(keyCode)) {
            modifiers.add(modifier);
        }
        return new KeyCombination(keyCode, modifiers, new int[0]);
    }

    /**
     * Builds a chord from the last pressed trigger and every key in the recording.
     * The collection must contain the trigger exactly once.
     */
    public static KeyCombination fromChord(int triggerKeyCode, Collection<Integer> keyCodes) {
        Objects.requireNonNull(keyCodes, "keyCodes");
        if (triggerKeyCode == 0) {
            for (Integer keyCode : keyCodes) {
                if (keyCode == null || keyCode != 0) {
                    throw new IllegalArgumentException("An unbound chord cannot contain keys: " + keyCodes);
                }
            }
            return unbound();
        }

        final EnumSet<KeyModifier> modifiers = EnumSet.noneOf(KeyModifier.class);
        final List<Integer> additionalKeys = new ArrayList<>();
        boolean triggerSeen = false;
        for (Integer keyCode : keyCodes) {
            if (keyCode == null || keyCode == 0) {
                throw new IllegalArgumentException("Chord contains an empty key: " + keyCodes);
            }
            if (keyCode == triggerKeyCode) {
                if (triggerSeen) {
                    throw new IllegalArgumentException("Chord repeats trigger " + triggerKeyCode + ": " + keyCodes);
                }
                triggerSeen = true;
                continue;
            }
            final KeyModifier modifier = modifierForKey(keyCode);
            if (modifier == KeyModifier.NONE) {
                additionalKeys.add(keyCode);
            } else {
                modifiers.add(modifier);
            }
        }
        if (!triggerSeen) {
            throw new IllegalArgumentException("Chord does not contain trigger " + triggerKeyCode + ": " + keyCodes);
        }
        return create(triggerKeyCode, modifiers, additionalKeys);
    }

    /** Changes only the trigger key while preserving the other held conditions. */
    public KeyCombination withTrigger(int keyCode) {
        if (keyCode == 0) {
            if (modifiers.isEmpty() && additionalKeys.length == 0) {
                return unbound();
            }
            throw new IllegalArgumentException("An unbound key cannot retain additional requirements");
        }
        final EnumSet<KeyModifier> newModifiers = modifiers.clone();
        final KeyModifier triggerModifier = modifierForKey(keyCode);
        if (triggerModifier != KeyModifier.NONE) {
            newModifiers.remove(triggerModifier);
        }
        final List<Integer> newAdditionalKeys = new ArrayList<>();
        for (int additionalKey : additionalKeys) {
            if (additionalKey != keyCode) {
                newAdditionalKeys.add(additionalKey);
            }
        }
        if (keyCode > 255 && newAdditionalKeys.isEmpty() && newModifiers.size() <= 1) {
            return of(
                keyCode,
                newModifiers.isEmpty() ? KeyModifier.NONE : newModifiers.iterator().next()
            );
        }
        return create(keyCode, newModifiers, newAdditionalKeys);
    }

    private static KeyCombination create(
        int triggerKeyCode,
        EnumSet<KeyModifier> modifiers,
        Collection<Integer> additionalKeys
    ) {
        if (modifiers.contains(KeyModifier.NONE)) {
            throw new IllegalArgumentException("NONE is not a held modifier");
        }
        if (triggerKeyCode == 0) {
            if (modifiers.isEmpty() && additionalKeys.isEmpty()) {
                return unbound();
            }
            throw new IllegalArgumentException("An unbound key cannot have additional requirements");
        }
        requirePhysicalKey(triggerKeyCode);
        final TreeSet<Integer> sortedKeys = new TreeSet<>();
        for (Integer keyCode : additionalKeys) {
            if (keyCode == null) {
                throw new IllegalArgumentException("Chord contains a null key");
            }
            requirePhysicalKey(keyCode);
            if (keyCode == triggerKeyCode) {
                throw new IllegalArgumentException("Chord repeats trigger " + triggerKeyCode);
            }
            if (modifierForKey(keyCode) != KeyModifier.NONE) {
                throw new IllegalArgumentException("Modifier " + keyCode + " must use its logical modifier entry");
            }
            if (!sortedKeys.add(keyCode)) {
                throw new IllegalArgumentException("Chord repeats additional key " + keyCode);
            }
        }
        final int[] keys = new int[sortedKeys.size()];
        int index = 0;
        for (int keyCode : sortedKeys) {
            keys[index++] = keyCode;
        }
        return new KeyCombination(triggerKeyCode, modifiers, keys);
    }

    private static void requirePhysicalKey(int keyCode) {
        if (keyCode <= 0 || keyCode > 255) {
            if (keyCode >= -100 && keyCode < 0) {
                return;
            }
            throw new IllegalArgumentException("Unsupported physical keycode " + keyCode);
        }
    }

    private static KeyModifier modifierForKey(int keyCode) {
        for (KeyModifier modifier : KeyModifier.MODIFIER_VALUES) {
            if (modifier.matches(keyCode)) {
                return modifier;
            }
        }
        return KeyModifier.NONE;
    }

    public int getKeyCode() {
        return triggerKeyCode;
    }

    public Set<KeyModifier> getModifiers() {
        return Collections.unmodifiableSet(modifiers.clone());
    }

    public int[] getAdditionalKeys() {
        return additionalKeys.clone();
    }

    /** Returns the number of held conditions, including the trigger. */
    public int size() {
        return triggerKeyCode == 0 ? 0 : 1 + modifiers.size() + additionalKeys.length;
    }

    public boolean requires(KeyModifier modifier) {
        return modifiers.contains(modifier);
    }

    /** Checks every held condition against an event-time or current snapshot. */
    public boolean matches(InputSnapshot snapshot) {
        Objects.requireNonNull(snapshot, "snapshot");
        if (triggerKeyCode == 0 || !snapshot.isKeyDown(triggerKeyCode)) {
            return false;
        }
        for (KeyModifier modifier : modifiers) {
            if (!modifier.isActive(snapshot)) {
                return false;
            }
        }
        for (int keyCode : additionalKeys) {
            if (!snapshot.isKeyDown(keyCode)) {
                return false;
            }
        }
        return true;
    }

    /** Checks both the event identity and all held conditions. */
    public boolean matchesEvent(int keyCode, InputSnapshot snapshot) {
        return keyCode == triggerKeyCode && matches(snapshot);
    }

    /** Returns true when every required key in this chord also belongs to the other chord. */
    public boolean isSubsetOf(KeyCombination other) {
        Objects.requireNonNull(other, "other");
        if (triggerKeyCode == 0) {
            return true;
        }
        if (!other.containsKey(triggerKeyCode)) {
            return false;
        }
        for (KeyModifier modifier : modifiers) {
            if (!other.modifiers.contains(modifier)) {
                return false;
            }
        }
        for (int keyCode : additionalKeys) {
            if (!other.containsKey(keyCode)) {
                return false;
            }
        }
        return true;
    }

    /** Compares the complete held key set without considering which key triggers the chord. */
    public boolean hasSameKeys(KeyCombination other) {
        return isSubsetOf(other) && other.isSubsetOf(this);
    }

    private boolean containsKey(int keyCode) {
        if (triggerKeyCode == 0) {
            return false;
        }
        if (triggerKeyCode == keyCode || Arrays.binarySearch(additionalKeys, keyCode) >= 0) {
            return true;
        }
        final KeyModifier modifier = modifierForKey(keyCode);
        return modifier != KeyModifier.NONE && modifiers.contains(modifier);
    }

    /** Formats the complete chord for controls and HUD text. */
    public String getDisplayName() {
        if (triggerKeyCode == 0) {
            return GameSettings.getKeyDisplayString(0);
        }
        String displayName = GameSettings.getKeyDisplayString(triggerKeyCode);
        for (int index = additionalKeys.length - 1; index >= 0; index--) {
            displayName = GameSettings.getKeyDisplayString(additionalKeys[index]) + " + " + displayName;
        }
        final List<KeyModifier> orderedModifiers = new ArrayList<>(modifiers);
        Collections.reverse(orderedModifiers);
        for (KeyModifier modifier : orderedModifiers) {
            displayName = modifier.getLocalizedComboName(displayName);
        }
        return displayName;
    }

    /** Uses the legacy representation whenever the chord fits it. */
    public String serialize() {
        if (additionalKeys.length == 0 && modifiers.size() <= 1) {
            if (modifiers.isEmpty()) {
                return Integer.toString(triggerKeyCode);
            }
            return triggerKeyCode + ":" + modifiers.iterator().next().name();
        }
        return serializeChord();
    }

    /** Always uses the explicit chord representation, including for a single key. */
    public String serializeChord() {
        final StringBuilder value = new StringBuilder(CHORD_PREFIX).append(triggerKeyCode).append(':');
        boolean firstModifier = true;
        for (KeyModifier modifier : modifiers) {
            if (!firstModifier) {
                value.append(',');
            }
            firstModifier = false;
            value.append(modifier.name());
        }
        value.append(':');
        for (int index = 0; index < additionalKeys.length; index++) {
            if (index > 0) {
                value.append(',');
            }
            value.append(additionalKeys[index]);
        }
        return value.toString();
    }

    /** Reads both the legacy representation and the explicit chord representation. */
    public static KeyCombination parse(String value) {
        if (value == null) {
            throw new IllegalArgumentException("Key combination cannot be null");
        }
        try {
            if (value.startsWith(CHORD_PREFIX)) {
                return parseChord(value);
            }
            return parseLegacy(value);
        } catch (IllegalArgumentException exception) {
            throw new IllegalArgumentException("Invalid key combination '" + value + "': " + exception.getMessage(), exception);
        }
    }

    private static KeyCombination parseChord(String value) {
        final String[] parts = value.split(":", -1);
        if (parts.length != 4) {
            throw new IllegalArgumentException("Expected chord:<trigger>:<modifiers>:<additionalKeys>");
        }
        final int triggerKeyCode = Integer.parseInt(parts[1]);
        final EnumSet<KeyModifier> modifiers = EnumSet.noneOf(KeyModifier.class);
        if (!parts[2].isEmpty()) {
            for (String name : parts[2].split(",", -1)) {
                final KeyModifier modifier = KeyModifier.valueOf(name);
                if (modifier == KeyModifier.NONE) {
                    throw new IllegalArgumentException("NONE is not a held modifier");
                }
                modifiers.add(modifier);
            }
        }
        final List<Integer> additionalKeys = new ArrayList<>();
        if (!parts[3].isEmpty()) {
            for (String keyCode : parts[3].split(",", -1)) {
                additionalKeys.add(Integer.parseInt(keyCode));
            }
        }
        return create(triggerKeyCode, modifiers, additionalKeys);
    }

    private static KeyCombination parseLegacy(String value) {
        final String[] parts = value.split(":", -1);
        if (parts.length == 1) {
            return of(Integer.parseInt(parts[0]), KeyModifier.NONE);
        }
        if (parts.length == 2) {
            return of(Integer.parseInt(parts[0]), KeyModifier.valueOf(parts[1]));
        }
        throw new IllegalArgumentException("Expected keyCode or keyCode:MODIFIER");
    }

    @Override
    public boolean equals(Object object) {
        if (this == object) {
            return true;
        }
        if (!(object instanceof KeyCombination)) {
            return false;
        }
        final KeyCombination other = (KeyCombination) object;
        return triggerKeyCode == other.triggerKeyCode
            && modifiers.equals(other.modifiers)
            && Arrays.equals(additionalKeys, other.additionalKeys);
    }

    @Override
    public int hashCode() {
        return 31 * (31 * triggerKeyCode + modifiers.hashCode()) + Arrays.hashCode(additionalKeys);
    }

    @Override
    public String toString() {
        return serializeChord();
    }
}
