package io.github.hyjn.nexori.plugin.binding;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;

final class TriggerBindingActionTest {

    @Test
    void enumValuesAreStable() {
        TriggerBindingAction[] values = TriggerBindingAction.values();
        assertEquals(4, values.length);
        assertEquals(TriggerBindingAction.TRAVEL, values[0]);
        assertEquals(TriggerBindingAction.LOCAL_TARGET, values[1]);
        assertEquals(TriggerBindingAction.JOIN_QUEUE, values[2]);
        assertEquals(TriggerBindingAction.LEAVE_QUEUE, values[3]);
    }
}
