package io.github.hyjn.nexori.plugin.bootstrap.logic;

import io.github.hyjn.nexori.plugin.bootstrap.BootstrapTextReplacement;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapTextReplacementScope;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BootstrapTextMigrationPlannerTest {

    private static BootstrapTextReplacement replacement(String oldValue, String newValue, BootstrapTextReplacementScope scope) {
        return new BootstrapTextReplacement(oldValue, newValue, scope);
    }

    @Test
    void emptyPlanChangesNothing() {
        String result = BootstrapTextMigrationPlanner.applyReplacements(
            "original content", "config.json", List.of());
        assertEquals("original content", result);
    }

    @Test
    void ineffectiveReplacementChangesNothing() {
        BootstrapTextReplacement sameValue = replacement("same.host:5520", "same.host:5520",
            BootstrapTextReplacementScope.ALL_TEXT_FILES);

        String result = BootstrapTextMigrationPlanner.applyReplacements(
            "connect to same.host:5520 now", "config.json", List.of(sameValue));

        assertEquals("connect to same.host:5520 now", result);
    }

    @Test
    void allTextFilesReplacementAppliesToAnyFile() {
        BootstrapTextReplacement r = replacement("old.host:5520", "new.host:5520",
            BootstrapTextReplacementScope.ALL_TEXT_FILES);

        String result = BootstrapTextMigrationPlanner.applyReplacements(
            "server=old.host:5520", "readme.txt", List.of(r));

        assertEquals("server=new.host:5520", result);
    }

    @Test
    void jsonScopeAppliesToJsonFile() {
        BootstrapTextReplacement r = replacement("old.host:5520", "new.host:5520",
            BootstrapTextReplacementScope.JSON_TEXT_FILES);

        String result = BootstrapTextMigrationPlanner.applyReplacements(
            "{\"host\":\"old.host:5520\"}", "settings.json", List.of(r));

        assertEquals("{\"host\":\"new.host:5520\"}", result);
    }

    @Test
    void jsonScopeAppliesToJsonlFile() {
        BootstrapTextReplacement r = replacement("old.host:5520", "new.host:5520",
            BootstrapTextReplacementScope.JSON_TEXT_FILES);

        String result = BootstrapTextMigrationPlanner.applyReplacements(
            "{\"host\":\"old.host:5520\"}", "data.jsonl", List.of(r));

        assertEquals("{\"host\":\"new.host:5520\"}", result);
    }

    @Test
    void jsonScopeDoesNotApplyToTxtFile() {
        BootstrapTextReplacement r = replacement("old.host:5520", "new.host:5520",
            BootstrapTextReplacementScope.JSON_TEXT_FILES);

        String result = BootstrapTextMigrationPlanner.applyReplacements(
            "server=old.host:5520", "readme.txt", List.of(r));

        assertEquals("server=old.host:5520", result, "JSON scope should not apply to .txt files");
    }

    @Test
    void multipleReplacementsApplyInOrderAccordingToCurrentBehavior() {
        List<BootstrapTextReplacement> replacements = List.of(
            replacement("alpha.host:5520", "beta.host:5520", BootstrapTextReplacementScope.ALL_TEXT_FILES),
            replacement("beta.host:5520", "gamma.host:5520", BootstrapTextReplacementScope.ALL_TEXT_FILES)
        );

        String result = BootstrapTextMigrationPlanner.applyReplacements(
            "connect alpha.host:5520", "config.json", replacements);

        assertEquals("connect gamma.host:5520", result,
            "Replacements apply sequentially so beta introduced by first replacement gets replaced by second");
    }

    @Test
    void replacementTrimsValuesBeforeApplyingAccordingToCurrentBehavior() {
        BootstrapTextReplacement r = replacement("  old.host:5520  ", "  new.host:5520  ",
            BootstrapTextReplacementScope.ALL_TEXT_FILES);

        String result = BootstrapTextMigrationPlanner.applyReplacements(
            "server=old.host:5520 end", "config.json", List.of(r));

        assertEquals("server=new.host:5520 end", result,
            "normalized() trims whitespace before replacement is applied");
    }

    @Test
    void unchangedTextReturnsOriginalString() {
        String original = "no matching content here";
        BootstrapTextReplacement r = replacement("old.host:5520", "new.host:5520",
            BootstrapTextReplacementScope.ALL_TEXT_FILES);

        String result = BootstrapTextMigrationPlanner.applyReplacements(
            original, "config.json", List.of(r));

        assertEquals(original, result);
    }

    @Test
    void supportsScopeReturnsTrueForAllTextFiles() {
        assertTrue(BootstrapTextMigrationPlanner.supportsScope(
            "anything.txt", BootstrapTextReplacementScope.ALL_TEXT_FILES));
    }

    @Test
    void supportsScopeReturnsFalseForJsonScopeOnNonJsonFile() {
        assertFalse(BootstrapTextMigrationPlanner.supportsScope(
            "config.properties", BootstrapTextReplacementScope.JSON_TEXT_FILES));
    }

    @Test
    void supportsScopeIsCaseInsensitiveForFileName() {
        assertTrue(BootstrapTextMigrationPlanner.supportsScope(
            "CONFIG.JSON", BootstrapTextReplacementScope.JSON_TEXT_FILES));
    }
}
