package net.minecraftforge.client.settings;

import net.minecraft.client.settings.KeyBinding;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * A binding can be kept out of conflict reporting, which is the escape hatch for keys that are meant to sit on top of
 * another binding. It must only affect the reporting, so the tests here check both directions: that the pair stops
 * being reported, and that ordinary pairs still are.
 */
class KeyConflictSuppressionTest {

    private static KeyBinding binding(String name, int keyCode) {
        return new KeyBinding(name, KeyCombination.of(keyCode, KeyModifier.NONE), "key.categories.misc");
    }

    @Test
    void aSuppressedBindingStopsBeingReportedByEitherSide() {
        final KeyBinding plain = binding("key.test.plain", 34);
        final KeyBinding overlay = binding("key.test.overlay", 34);

        assertTrue(plain.conflicts(overlay), "two bindings on one key conflict to begin with");

        overlay.setConflictReportingSuppressed(true);

        assertFalse(plain.conflicts(overlay), "the suppression has to hold no matter which side asks");
        assertFalse(overlay.conflicts(plain));
        assertFalse(overlay.hasPartialConflict(plain));
        assertFalse(plain.hasPartialConflict(overlay));
    }

    @Test
    void suppressionDoesNotAffectUnrelatedPairs() {
        final KeyBinding plain = binding("key.test.plain", 34);
        final KeyBinding other = binding("key.test.other", 34);

        plain.setConflictReportingSuppressed(true);

        assertFalse(plain.conflicts(other), "the suppressed pair is the only one silenced");
        assertTrue(binding("key.test.a", 35).conflicts(binding("key.test.b", 35)),
            "a pair with neither side suppressed still reports");
    }

    @Test
    void suppressionCanBeTakenBack() {
        final KeyBinding plain = binding("key.test.plain", 34);
        final KeyBinding overlay = binding("key.test.overlay", 34);

        overlay.setConflictReportingSuppressed(true);
        assertFalse(plain.conflicts(overlay));

        overlay.setConflictReportingSuppressed(false);
        assertTrue(plain.conflicts(overlay), "clearing it restores the report");
    }

    @Test
    void anUnboundBindingNeverReports() {
        final KeyBinding unbound = binding("key.test.unbound", 0);
        final KeyBinding plain = binding("key.test.plain", 34);

        assertFalse(unbound.conflicts(plain));
        assertFalse(plain.conflicts(unbound));
    }
}
