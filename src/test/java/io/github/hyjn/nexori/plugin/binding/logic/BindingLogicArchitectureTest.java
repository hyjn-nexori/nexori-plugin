package io.github.hyjn.nexori.plugin.binding.logic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class BindingLogicArchitectureTest {

    private String source(String filename) throws IOException {
        return Files.readString(Path.of(
            "src/main/java/io/github/hyjn/nexori/plugin/binding/logic/" + filename));
    }

    @Test
    void selectionPolicyDoesNotImportHytaleLogger() throws IOException {
        assertFalse(source("TriggerBindingSelectionPolicy.java").contains("HytaleLogger"));
    }

    @Test
    void selectionPolicyDoesNotImportPlayerRef() throws IOException {
        assertFalse(source("TriggerBindingSelectionPolicy.java").contains("PlayerRef"));
    }

    @Test
    void selectionPolicyDoesNotImportStore() throws IOException {
        assertFalse(source("TriggerBindingSelectionPolicy.java").contains("Store"));
    }

    @Test
    void selectionPolicyDoesNotImportFilesystem() throws IOException {
        String src = source("TriggerBindingSelectionPolicy.java");
        assertFalse(src.contains("java.nio.file") || src.contains("java.io.File"));
    }

    @Test
    void validationPolicyDoesNotImportHytaleLogger() throws IOException {
        assertFalse(source("TriggerBindingValidationPolicy.java").contains("HytaleLogger"));
    }

    @Test
    void validationPolicyDoesNotImportPlayerRef() throws IOException {
        assertFalse(source("TriggerBindingValidationPolicy.java").contains("PlayerRef"));
    }

    @Test
    void validationPolicyDoesNotImportStore() throws IOException {
        assertFalse(source("TriggerBindingValidationPolicy.java").contains("Store"));
    }

    @Test
    void validationPolicyDoesNotCallCurrentTimeMillis() throws IOException {
        assertFalse(source("TriggerBindingValidationPolicy.java").contains("currentTimeMillis"));
    }

    @Test
    void validationPolicyDoesNotUseRandomUUID() throws IOException {
        assertFalse(source("TriggerBindingValidationPolicy.java").contains("randomUUID"));
    }

    // ── PortalBindingApplyPlanner ─────────────────────────────────────────────

    @Test
    void applyPlannerDoesNotImportHytaleLogger() throws IOException {
        assertFalse(source("PortalBindingApplyPlanner.java").contains("HytaleLogger"));
    }

    @Test
    void applyPlannerDoesNotImportStore() throws IOException {
        assertFalse(source("PortalBindingApplyPlanner.java").contains("Store"));
    }

    @Test
    void applyPlannerDoesNotImportFilesystem() throws IOException {
        String src = source("PortalBindingApplyPlanner.java");
        assertFalse(src.contains("java.nio.file") || src.contains("java.io.File"));
    }

    @Test
    void applyPlannerDoesNotImportSecureReferralService() throws IOException {
        assertFalse(source("PortalBindingApplyPlanner.java").contains("SecureReferralService"));
    }

    @Test
    void applyPlannerDoesNotImportDiagnosticsService() throws IOException {
        assertFalse(source("PortalBindingApplyPlanner.java").contains("DiagnosticsService"));
    }
}
