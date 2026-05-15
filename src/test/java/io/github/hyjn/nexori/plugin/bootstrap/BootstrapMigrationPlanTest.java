package io.github.hyjn.nexori.plugin.bootstrap;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class BootstrapMigrationPlanTest {

    @Test
    void emptyHasNoReplacements() {
        BootstrapMigrationPlan plan = BootstrapMigrationPlan.empty();
        assertTrue(plan.isEmpty());
        assertTrue(plan.replacements().isEmpty());
    }

    @Test
    void constructorNullReplacementsBecomesEmptyList() {
        BootstrapMigrationPlan plan = new BootstrapMigrationPlan(null);
        assertNotNull(plan.replacements());
        assertTrue(plan.replacements().isEmpty());
    }

    @Test
    void constructorCopiesReplacements() {
        BootstrapTextReplacement rep = new BootstrapTextReplacement("old", "new", BootstrapTextReplacementScope.ALL_TEXT_FILES);
        BootstrapMigrationPlan plan = new BootstrapMigrationPlan(List.of(rep));

        assertEquals(1, plan.replacements().size());
        assertEquals("old", plan.replacements().get(0).oldValue());
    }

    @Test
    void isEmptyFalseWhenHasReplacement() {
        BootstrapTextReplacement rep = new BootstrapTextReplacement("old", "new", BootstrapTextReplacementScope.ALL_TEXT_FILES);
        BootstrapMigrationPlan plan = new BootstrapMigrationPlan(List.of(rep));

        assertFalse(plan.isEmpty());
    }
}
