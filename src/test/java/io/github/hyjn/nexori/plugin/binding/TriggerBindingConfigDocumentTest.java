package io.github.hyjn.nexori.plugin.binding;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

final class TriggerBindingConfigDocumentTest {

    private static TriggerBindingDefinition binding() {
        return new TriggerBindingDefinition(
            "binding-1", TriggerBindingKind.PORTAL_COLLISION_ENTER, "portal-src",
            TriggerBindingAction.TRAVEL, "", "remote.srv:25565", "target-a", "", "{}", true
        );
    }

    @Test
    void configDocumentSchemaVersionIsCurrent() {
        assertEquals(2, TriggerBindingConfigDocument.CURRENT_SCHEMA_VERSION);
    }

    @Test
    void configDocumentNullBindingsBecomesEmptyListAccordingToCurrentBehavior() {
        TriggerBindingConfigDocument doc = new TriggerBindingConfigDocument(2, null);
        assertTrue(doc.triggerBindings().isEmpty(),
            "Compact constructor normalizes null triggerBindings to List.of()");
    }

    @Test
    void configDocumentPreservesBindingsList() {
        TriggerBindingConfigDocument doc = new TriggerBindingConfigDocument(2, List.of(binding()));
        assertEquals(1, doc.triggerBindings().size());
        assertEquals("binding-1", doc.triggerBindings().get(0).id());
    }

    @Test
    void configDocumentNullElementInListThrowsAccordingToCurrentBehavior() {
        List<TriggerBindingDefinition> withNull = new ArrayList<>();
        withNull.add(null);
        assertThrows(NullPointerException.class,
            () -> new TriggerBindingConfigDocument(2, withNull),
            "List.copyOf() throws NullPointerException when the list contains a null element");
    }
}
