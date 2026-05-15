package io.github.hyjn.nexori.plugin.bootstrap;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BootstrapTextReplacementTest {

    @Test
    void normalizedTrimsOldAndNewValue() {
        BootstrapTextReplacement rep = new BootstrapTextReplacement(
            "  old.host:5520  ", "  new.host:5520  ", BootstrapTextReplacementScope.ALL_TEXT_FILES);

        assertEquals("old.host:5520", rep.normalized().oldValue());
        assertEquals("new.host:5520", rep.normalized().newValue());
    }

    @Test
    void normalizedDefaultsNullScopeToAllTextFiles() {
        BootstrapTextReplacement rep = new BootstrapTextReplacement("old", "new", null);

        assertEquals(BootstrapTextReplacementScope.ALL_TEXT_FILES, rep.normalized().scope());
    }

    @Test
    void normalizedNullOldValueBecomesBlank() {
        BootstrapTextReplacement rep = new BootstrapTextReplacement(null, "new", BootstrapTextReplacementScope.ALL_TEXT_FILES);

        assertEquals("", rep.normalized().oldValue());
    }

    @Test
    void normalizedNullNewValueBecomesBlank() {
        BootstrapTextReplacement rep = new BootstrapTextReplacement("old", null, BootstrapTextReplacementScope.ALL_TEXT_FILES);

        assertEquals("", rep.normalized().newValue());
    }

    @Test
    void isEffectiveFalseWhenOldBlank() {
        BootstrapTextReplacement rep = new BootstrapTextReplacement("", "new", BootstrapTextReplacementScope.ALL_TEXT_FILES);
        assertFalse(rep.isEffective());
    }

    @Test
    void isEffectiveFalseWhenNewBlank() {
        BootstrapTextReplacement rep = new BootstrapTextReplacement("old", "", BootstrapTextReplacementScope.ALL_TEXT_FILES);
        assertFalse(rep.isEffective());
    }

    @Test
    void isEffectiveFalseWhenValuesEqual() {
        BootstrapTextReplacement rep = new BootstrapTextReplacement("same", "same", BootstrapTextReplacementScope.ALL_TEXT_FILES);
        assertFalse(rep.isEffective());
    }

    @Test
    void isEffectiveTrueWhenValuesDiffer() {
        BootstrapTextReplacement rep = new BootstrapTextReplacement("old.host:5520", "new.host:5520", BootstrapTextReplacementScope.ALL_TEXT_FILES);
        assertTrue(rep.isEffective());
    }

    @Test
    void jsonScopeEnumIsStable() {
        assertEquals("JSON_TEXT_FILES", BootstrapTextReplacementScope.JSON_TEXT_FILES.name());
        assertEquals(1, BootstrapTextReplacementScope.JSON_TEXT_FILES.ordinal());
    }

    @Test
    void allTextFilesEnumIsStable() {
        assertEquals("ALL_TEXT_FILES", BootstrapTextReplacementScope.ALL_TEXT_FILES.name());
        assertEquals(0, BootstrapTextReplacementScope.ALL_TEXT_FILES.ordinal());
    }
}
