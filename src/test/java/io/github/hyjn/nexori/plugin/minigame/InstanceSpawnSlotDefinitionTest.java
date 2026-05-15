package io.github.hyjn.nexori.plugin.minigame;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class InstanceSpawnSlotDefinitionTest {

    private static InstanceSpawnSlotDefinition slot(String slotId, String templateId) {
        return new InstanceSpawnSlotDefinition(slotId, templateId, 10.5, 64.0, -3.5, 45f, 90f, 0f, 1000L);
    }

    // ── normalized: slotId ────────────────────────────────────────────────────

    @Test
    void normalizedTrimsAndLowercasesSlotId() {
        InstanceSpawnSlotDefinition s = slot("  SLOT-Alpha-1  ", "template-1").normalized();
        assertEquals("slot-alpha-1", s.slotId());
    }

    @Test
    void normalizedBlankSlotIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> slot("   ", "template-1").normalized());
    }

    @Test
    void normalizedNullSlotIdThrowsAccordingToCurrentBehavior() {
        assertThrows(IllegalArgumentException.class, () -> slot(null, "template-1").normalized(),
            "null slotId goes to normalizeRequired which null-checks and throws IAE immediately");
    }

    // ── normalized: instanceTemplateId ────────────────────────────────────────

    @Test
    void normalizedTrimsInstanceTemplateIdButPreservesCaseAccordingToCurrentBehavior() {
        InstanceSpawnSlotDefinition s = slot("slot-1", "  MyTemplate-v2  ").normalized();
        assertEquals("MyTemplate-v2", s.instanceTemplateId(),
            "normalizeRequiredTemplateId trims but does NOT lowercase; case is preserved");
    }

    @Test
    void normalizedBlankInstanceTemplateIdThrows() {
        assertThrows(IllegalArgumentException.class, () -> slot("slot-1", "  ").normalized());
    }

    @Test
    void normalizedNullInstanceTemplateIdThrowsAccordingToCurrentBehavior() {
        assertThrows(IllegalArgumentException.class, () -> slot("slot-1", null).normalized(),
            "null instanceTemplateId is null-checked before trim; throws IAE immediately");
    }

    // ── normalized: position and rotation ────────────────────────────────────

    @Test
    void normalizedPreservesPositionAndRotation() {
        InstanceSpawnSlotDefinition s = new InstanceSpawnSlotDefinition(
            "slot-1", "template-1", 10.5, 64.0, -3.5, 45f, 90f, 180f, 1000L
        ).normalized();
        assertEquals(10.5, s.x());
        assertEquals(64.0, s.y());
        assertEquals(-3.5, s.z());
        assertEquals(45f, s.pitch());
        assertEquals(90f, s.yaw());
        assertEquals(180f, s.roll());
    }

    // ── normalized: createdAtEpochMs ──────────────────────────────────────────

    @Test
    void normalizedPreservesPositiveCreatedAt() {
        InstanceSpawnSlotDefinition s = slot("slot-1", "template-1").normalized();
        assertEquals(1000L, s.createdAtEpochMs());
    }

    @Test
    void normalizedZeroCreatedAtUsesCurrentTimeAccordingToCurrentBehavior() {
        InstanceSpawnSlotDefinition s = new InstanceSpawnSlotDefinition(
            "slot-1", "template-1", 0, 0, 0, 0f, 0f, 0f, 0L
        ).normalized();
        assertTrue(s.createdAtEpochMs() > 0L,
            "createdAtEpochMs <= 0 is replaced by System.currentTimeMillis()");
    }

    @Test
    void normalizedNegativeCreatedAtUsesCurrentTimeAccordingToCurrentBehavior() {
        InstanceSpawnSlotDefinition s = new InstanceSpawnSlotDefinition(
            "slot-1", "template-1", 0, 0, 0, 0f, 0f, 0f, -500L
        ).normalized();
        assertTrue(s.createdAtEpochMs() > 0L,
            "Negative createdAtEpochMs is replaced by System.currentTimeMillis()");
    }

    // ── normalizeSlotId ───────────────────────────────────────────────────────

    @Test
    void normalizeSlotIdTrimsAndLowercases() {
        assertEquals("slot-a", InstanceSpawnSlotDefinition.normalizeSlotId("  SLOT-A  "));
    }
}
