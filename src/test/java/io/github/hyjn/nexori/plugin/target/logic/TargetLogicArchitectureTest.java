package io.github.hyjn.nexori.plugin.target.logic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class TargetLogicArchitectureTest {

    private String source(String filename) throws IOException {
        return Files.readString(Path.of(
            "src/main/java/io/github/hyjn/nexori/plugin/target/logic/" + filename));
    }

    @Test
    void resolutionPlannerDoesNotImportHytaleLogger() throws IOException {
        assertFalse(source("DestinationTargetResolutionPlanner.java").contains("HytaleLogger"));
    }

    @Test
    void resolutionPlannerDoesNotImportPlayerRef() throws IOException {
        assertFalse(source("DestinationTargetResolutionPlanner.java").contains("PlayerRef"));
    }

    @Test
    void resolutionPlannerDoesNotImportStore() throws IOException {
        assertFalse(source("DestinationTargetResolutionPlanner.java").contains("Store"));
    }

    @Test
    void resolutionPlannerDoesNotImportFilesystem() throws IOException {
        String src = source("DestinationTargetResolutionPlanner.java");
        assertFalse(src.contains("java.nio.file") || src.contains("java.io.File"));
    }

    @Test
    void resolutionPlannerDoesNotCallCurrentTimeMillis() throws IOException {
        assertFalse(source("DestinationTargetResolutionPlanner.java").contains("currentTimeMillis"));
    }

    @Test
    void resolutionPlannerDoesNotUseRandomUUID() throws IOException {
        assertFalse(source("DestinationTargetResolutionPlanner.java").contains("randomUUID"));
    }
}
