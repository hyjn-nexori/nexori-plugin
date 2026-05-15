package io.github.hyjn.nexori.plugin.profile;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

final class TravelProfileTypeTest {

    @Test
    void parseNullDefaultsToKeepInventory() {
        assertEquals(TravelProfileType.KEEP_INVENTORY, TravelProfileType.parse(null));
    }

    @Test
    void parseBlankDefaultsToKeepInventory() {
        assertEquals(TravelProfileType.KEEP_INVENTORY, TravelProfileType.parse("   "));
        assertEquals(TravelProfileType.KEEP_INVENTORY, TravelProfileType.parse(""));
    }

    @Test
    void parseKeepAliases() {
        assertEquals(TravelProfileType.KEEP_INVENTORY, TravelProfileType.parse("KEEP"));
        assertEquals(TravelProfileType.KEEP_INVENTORY, TravelProfileType.parse("KEEP_INVENTORY"));
        assertEquals(TravelProfileType.KEEP_INVENTORY, TravelProfileType.parse("keep_inventory"));
    }

    @Test
    void parseClearAliases() {
        assertEquals(TravelProfileType.CLEAR_INVENTORY, TravelProfileType.parse("CLEAR"));
        assertEquals(TravelProfileType.CLEAR_INVENTORY, TravelProfileType.parse("CLEAR_INVENTORY"));
        assertEquals(TravelProfileType.CLEAR_INVENTORY, TravelProfileType.parse("clear_inventory"));
    }

    @Test
    void parseApplyAliases() {
        assertEquals(TravelProfileType.APPLY_INVENTORY, TravelProfileType.parse("APPLY"));
        assertEquals(TravelProfileType.APPLY_INVENTORY, TravelProfileType.parse("APPLY_INVENTORY"));
        assertEquals(TravelProfileType.APPLY_INVENTORY, TravelProfileType.parse("apply_inventory"));
    }

    @Test
    void parseAcceptsHyphenAndSpaces() {
        assertEquals(TravelProfileType.KEEP_INVENTORY, TravelProfileType.parse("keep-inventory"));
        assertEquals(TravelProfileType.KEEP_INVENTORY, TravelProfileType.parse("keep inventory"));
        assertEquals(TravelProfileType.CLEAR_INVENTORY, TravelProfileType.parse("clear-inventory"));
        assertEquals(TravelProfileType.APPLY_INVENTORY, TravelProfileType.parse("apply inventory"));
    }

    @Test
    void parseIsCaseInsensitive() {
        assertEquals(TravelProfileType.KEEP_INVENTORY, TravelProfileType.parse("Keep"));
        assertEquals(TravelProfileType.KEEP_INVENTORY, TravelProfileType.parse("KEEP"));
        assertEquals(TravelProfileType.KEEP_INVENTORY, TravelProfileType.parse("keep"));
    }

    @Test
    void parseUnknownThrowsAccordingToCurrentBehavior() {
        assertThrows(IllegalArgumentException.class, () -> TravelProfileType.parse("UNKNOWN"),
            "parse() falls through to switch default which throws IAE for unrecognized values");
    }

    @Test
    void idsDisplayNamesAndDescriptionsAreStable() {
        assertEquals("keep_inventory", TravelProfileType.KEEP_INVENTORY.id());
        assertEquals("Keep Inventory", TravelProfileType.KEEP_INVENTORY.displayName());

        assertEquals("clear_inventory", TravelProfileType.CLEAR_INVENTORY.id());
        assertEquals("Clear Inventory", TravelProfileType.CLEAR_INVENTORY.displayName());

        assertEquals("apply_inventory", TravelProfileType.APPLY_INVENTORY.id());
        assertEquals("Apply Inventory", TravelProfileType.APPLY_INVENTORY.displayName());
    }
}
