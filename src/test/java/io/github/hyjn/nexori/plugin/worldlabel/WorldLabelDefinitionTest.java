package io.github.hyjn.nexori.plugin.worldlabel;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class WorldLabelDefinitionTest {

    @Test
    void normalizedTrimsAndLowercasesLabelId() {
        WorldLabelDefinition def = new WorldLabelDefinition(
            "  LOBBY-LABEL  ", "world", 0, 0, 0, "text"
        ).normalized();
        assertEquals("lobby-label", def.labelId());
    }

    @Test
    void normalizedTrimsAndLowercasesWorldName() {
        WorldLabelDefinition def = new WorldLabelDefinition(
            "label", "  OVERWORLD  ", 0, 0, 0, "text"
        ).normalized();
        assertEquals("overworld", def.worldName());
    }

    @Test
    void normalizedTrimsText() {
        WorldLabelDefinition def = new WorldLabelDefinition(
            "label", "world", 0, 0, 0, "  Hello World  "
        ).normalized();
        assertEquals("Hello World", def.text());
    }

    @Test
    void normalizedNullLabelIdBecomesBlankAccordingToCurrentBehavior() {
        WorldLabelDefinition def = new WorldLabelDefinition(
            null, "world", 0, 0, 0, "text"
        ).normalized();
        assertEquals("", def.labelId(),
            "normalized() guards: labelId == null ? \"\" : labelId.trim().toLowerCase()");
    }

    @Test
    void normalizedNullWorldNameBecomesBlankAccordingToCurrentBehavior() {
        WorldLabelDefinition def = new WorldLabelDefinition(
            "label", null, 0, 0, 0, "text"
        ).normalized();
        assertEquals("", def.worldName(),
            "normalized() guards: worldName == null ? \"\" : worldName.trim().toLowerCase()");
    }

    @Test
    void normalizedNullTextBecomesBlankAccordingToCurrentBehavior() {
        WorldLabelDefinition def = new WorldLabelDefinition(
            "label", "world", 0, 0, 0, null
        ).normalized();
        assertEquals("", def.text(),
            "normalized() guards: text == null ? \"\" : text.trim()");
    }

    @Test
    void normalizedPreservesCoordinates() {
        WorldLabelDefinition def = new WorldLabelDefinition(
            "label", "world", 1.5, 64.0, -32.75, "text"
        ).normalized();
        assertEquals(1.5, def.x());
        assertEquals(64.0, def.y());
        assertEquals(-32.75, def.z());
    }
}
