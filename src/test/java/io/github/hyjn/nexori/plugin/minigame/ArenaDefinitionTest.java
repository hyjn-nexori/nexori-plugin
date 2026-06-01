package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class ArenaDefinitionTest {

    private static ArenaDefinition arena(String arenaId) {
        return new ArenaDefinition(
            arenaId, "My Arena", "srv.example.com:25565", "hub-1",
            "none", "", 16, true
        );
    }

    // ── normalized: arenaId ───────────────────────────────────────────────────

    @Test
    void normalizedTrimsAndLowercasesArenaId() {
        ArenaDefinition a = arena("  SurvivalArena  ").normalized();
        assertEquals("survivalarena", a.arenaId());
    }

    @Test
    void normalizedBlankArenaIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> arena("   ").normalized());
    }

    @Test
    void normalizedNullArenaIdThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ArenaDefinition(null, "Arena", "addr:1", "hub", "none", "", 4, true).normalized());
    }

    // ── normalized: displayName ───────────────────────────────────────────────

    @Test
    void normalizedPreservesDisplayNameCase() {
        ArenaDefinition a = new ArenaDefinition(
            "arena-1", "  My Arena  ", "addr:1", "hub", "none", "", 4, true).normalized();
        assertEquals("My Arena", a.displayName());
    }

    @Test
    void normalizedBlankDisplayNameDefaultsToArenaId() {
        ArenaDefinition a = new ArenaDefinition(
            "arena-1", "   ", "addr:1", "hub", "none", "", 4, true).normalized();
        assertEquals("arena-1", a.displayName());
    }

    @Test
    void normalizedNullDisplayNameDefaultsToArenaId() {
        ArenaDefinition a = new ArenaDefinition(
            "arena-1", null, "addr:1", "hub", "none", "", 4, true).normalized();
        assertEquals("arena-1", a.displayName());
    }

    // ── normalized: destinationConnectionAddress ──────────────────────────────

    @Test
    void normalizedTrimsAndLowercasesDestinationAddress() {
        ArenaDefinition a = new ArenaDefinition(
            "a", "A", "  SRV.EXAMPLE.COM:25565  ", "hub", "none", "", 4, true).normalized();
        assertEquals("srv.example.com:25565", a.destinationConnectionAddress());
    }

    @Test
    void normalizedNullAddressBecomesEmpty() {
        ArenaDefinition a = new ArenaDefinition(
            "a", "A", null, "hub", "none", "", 4, true).normalized();
        assertEquals("", a.destinationConnectionAddress());
    }

    // ── normalized: instanceTemplateId ────────────────────────────────────────

    @Test
    void normalizedNullInstanceTemplateIdBecomesNone() {
        ArenaDefinition a = new ArenaDefinition(
            "a", "A", "addr:1", "hub", null, "", 4, true).normalized();
        assertEquals(ArenaDefinition.NO_INSTANCE_TEMPLATE_ID, a.instanceTemplateId());
    }

    @Test
    void normalizedBlankInstanceTemplateIdBecomesNone() {
        ArenaDefinition a = new ArenaDefinition(
            "a", "A", "addr:1", "hub", "  ", "", 4, true).normalized();
        assertEquals(ArenaDefinition.NO_INSTANCE_TEMPLATE_ID, a.instanceTemplateId());
    }

    @Test
    void normalizedNoneStringInstanceTemplateIdStaysNone() {
        ArenaDefinition a = new ArenaDefinition(
            "a", "A", "addr:1", "hub", "NONE", "", 4, true).normalized();
        assertEquals(ArenaDefinition.NO_INSTANCE_TEMPLATE_ID, a.instanceTemplateId());
    }

    @Test
    void normalizedRealInstanceTemplateIdPreserved() {
        ArenaDefinition a = new ArenaDefinition(
            "a", "A", "addr:1", "hub", "  MyTemplate  ", "", 4, true).normalized();
        assertEquals("MyTemplate", a.instanceTemplateId());
    }

    // ── normalized: rulesEngineId ─────────────────────────────────────────────

    @Test
    void normalizedNullRulesEngineIdBecomesEmpty() {
        ArenaDefinition a = new ArenaDefinition(
            "a", "A", "addr:1", "hub", "none", null, 4, true).normalized();
        assertEquals("", a.rulesEngineId());
    }

    @Test
    void oldArenaDefinitionsDefaultAfkDetectionDisabledWithThirtySecondTimeout() {
        ArenaDefinition a = new ArenaDefinition(
            "a", "A", "addr:1", "hub", "none", "", 4, true).normalized();

        assertFalse(a.afkDetectionPolicy().enabled());
        assertEquals(30, a.afkDetectionPolicy().inactivityTimeoutSeconds());
    }

    @Test
    void normalizedAfkPolicyProtectsOutOfRangeTimeouts() {
        ArenaDefinition belowMin = new ArenaDefinition(
            "a", "A", "addr:1", "hub", "none", "", 4, true,
            new AfkDetectionPolicy(true, 1)
        ).normalized();
        ArenaDefinition aboveMax = new ArenaDefinition(
            "b", "B", "addr:1", "hub", "none", "", 4, true,
            new AfkDetectionPolicy(true, 99999)
        ).normalized();
        ArenaDefinition missingTimeout = new ArenaDefinition(
            "c", "C", "addr:1", "hub", "none", "", 4, true,
            new AfkDetectionPolicy(true, 0)
        ).normalized();

        assertEquals(AfkDetectionPolicy.MIN_INACTIVITY_TIMEOUT_SECONDS, belowMin.afkDetectionPolicy().inactivityTimeoutSeconds());
        assertEquals(AfkDetectionPolicy.MAX_INACTIVITY_TIMEOUT_SECONDS, aboveMax.afkDetectionPolicy().inactivityTimeoutSeconds());
        assertEquals(AfkDetectionPolicy.DEFAULT_INACTIVITY_TIMEOUT_SECONDS, missingTimeout.afkDetectionPolicy().inactivityTimeoutSeconds());
    }

    @Test
    void normalizedBlankRulesEngineIdBecomesEmpty() {
        ArenaDefinition a = new ArenaDefinition(
            "a", "A", "addr:1", "hub", "none", "  ", 4, true).normalized();
        assertEquals("", a.rulesEngineId());
    }

    @Test
    void normalizedRulesEngineIdPreservesCase() {
        ArenaDefinition a = new ArenaDefinition(
            "a", "A", "addr:1", "hub", "none", "  MyRules_v1  ", 4, true).normalized();
        assertEquals("MyRules_v1", a.rulesEngineId());
    }

    @Test
    void normalizedRulesEngineIdTooLongThrows() {
        String tooLong = "a".repeat(ArenaDefinition.MAX_RULES_ENGINE_ID_LENGTH + 1);
        assertThrows(IllegalArgumentException.class,
            () -> new ArenaDefinition("a", "A", "addr:1", "hub", "none", tooLong, 4, true).normalized());
    }

    @Test
    void normalizedRulesEngineIdInvalidCharactersThrows() {
        assertThrows(IllegalArgumentException.class,
            () -> new ArenaDefinition("a", "A", "addr:1", "hub", "none", "rules@invalid!", 4, true).normalized());
    }

    // ── usesInstanceTemplate ─────────────────────────────────────────────────

    @Test
    void usesInstanceTemplateFalseWhenNone() {
        assertFalse(arena("my-arena").usesInstanceTemplate());
    }

    @Test
    void usesInstanceTemplateTrueWhenSet() {
        ArenaDefinition a = new ArenaDefinition(
            "a", "A", "addr:1", "hub", "template-x", "", 4, true);
        assertTrue(a.usesInstanceTemplate());
    }

    @Test
    void usesInstanceTemplateCaseInsensitiveNone() {
        ArenaDefinition a = new ArenaDefinition(
            "a", "A", "addr:1", "hub", "NONE", "", 4, true);
        assertFalse(a.usesInstanceTemplate());
    }

    @Test
    void oldArenaDefinitionsDefaultInitialPlacementWindowSeconds() {
        ArenaDefinition a = new ArenaDefinition("a", "A", "addr:1", "hub", "none", "", 4, true).normalized();

        assertEquals(ArenaDefinition.DEFAULT_INITIAL_PLACEMENT_WINDOW_SECONDS, a.initialPlacementWindowSeconds());
    }

    @Test
    void initialPlacementWindowSecondsNormalizeToSupportedRange() {
        ArenaDefinition belowMin = new ArenaDefinition("a", "A", "addr:1", "hub", "none", "", -1, 4, true, AfkDetectionPolicy.defaults()).normalized();
        ArenaDefinition aboveMax = new ArenaDefinition("b", "B", "addr:1", "hub", "none", "", 9_999, 4, true, AfkDetectionPolicy.defaults()).normalized();

        assertEquals(ArenaDefinition.DEFAULT_INITIAL_PLACEMENT_WINDOW_SECONDS, belowMin.initialPlacementWindowSeconds());
        assertEquals(ArenaDefinition.MAX_INITIAL_PLACEMENT_WINDOW_SECONDS, aboveMax.initialPlacementWindowSeconds());
    }

    // ── normalizeId ───────────────────────────────────────────────────────────

    @Test
    void normalizeIdTrimsAndLowercases() {
        assertEquals("my-arena", ArenaDefinition.normalizeId("  My-Arena  "));
    }

    @Test
    void normalizeIdBlankThrows() {
        assertThrows(IllegalArgumentException.class, () -> ArenaDefinition.normalizeId("  "));
    }

    // ── normalizeRulesEngineId ────────────────────────────────────────────────

    @Test
    void normalizeRulesEngineIdNullReturnsEmpty() {
        assertEquals("", ArenaDefinition.normalizeRulesEngineId(null));
    }

    @Test
    void normalizeRulesEngineIdBlankReturnsEmpty() {
        assertEquals("", ArenaDefinition.normalizeRulesEngineId("   "));
    }

    @Test
    void normalizeRulesEngineIdValidValuePreservedAccordingToCurrentBehavior() {
        assertEquals("myRules_v1.0-a", ArenaDefinition.normalizeRulesEngineId("myRules_v1.0-a"));
    }
}

