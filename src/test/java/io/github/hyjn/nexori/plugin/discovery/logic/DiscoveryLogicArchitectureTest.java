package io.github.hyjn.nexori.plugin.discovery.logic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class DiscoveryLogicArchitectureTest {

    private String source(String filename) throws IOException {
        return Files.readString(Path.of(
            "src/main/java/io/github/hyjn/nexori/plugin/discovery/logic/" + filename));
    }

    // ── DestinationTargetSummaryBuilder ───────────────────────────────────────

    @Test
    void summaryBuilderDoesNotImportHytaleLogger() throws IOException {
        assertFalse(source("DestinationTargetSummaryBuilder.java").contains("HytaleLogger"));
    }

    @Test
    void summaryBuilderDoesNotImportStore() throws IOException {
        assertFalse(source("DestinationTargetSummaryBuilder.java").contains("Store"));
    }

    @Test
    void summaryBuilderDoesNotImportFilesystem() throws IOException {
        String src = source("DestinationTargetSummaryBuilder.java");
        assertFalse(src.contains("java.nio.file") || src.contains("java.io.File"));
    }

    @Test
    void summaryBuilderDoesNotImportDiagnosticsService() throws IOException {
        assertFalse(source("DestinationTargetSummaryBuilder.java").contains("DiagnosticsService"));
    }

    // ── DestinationTargetSyncApplyPlanner ─────────────────────────────────────

    @Test
    void syncApplyPlannerDoesNotImportHytaleLogger() throws IOException {
        assertFalse(source("DestinationTargetSyncApplyPlanner.java").contains("HytaleLogger"));
    }

    @Test
    void syncApplyPlannerDoesNotImportStore() throws IOException {
        assertFalse(source("DestinationTargetSyncApplyPlanner.java").contains("Store"));
    }

    @Test
    void syncApplyPlannerDoesNotImportFilesystem() throws IOException {
        String src = source("DestinationTargetSyncApplyPlanner.java");
        assertFalse(src.contains("java.nio.file") || src.contains("java.io.File"));
    }

    @Test
    void syncApplyPlannerDoesNotImportSecureReferralService() throws IOException {
        assertFalse(source("DestinationTargetSyncApplyPlanner.java").contains("SecureReferralService"));
    }

    @Test
    void syncApplyPlannerDoesNotImportDiagnosticsService() throws IOException {
        assertFalse(source("DestinationTargetSyncApplyPlanner.java").contains("DiagnosticsService"));
    }
}
