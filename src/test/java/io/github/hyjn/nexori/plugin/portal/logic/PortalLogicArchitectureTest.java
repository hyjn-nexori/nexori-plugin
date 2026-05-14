package io.github.hyjn.nexori.plugin.portal.logic;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;

final class PortalLogicArchitectureTest {

    private String source(String filename) throws IOException {
        return Files.readString(Path.of(
            "src/main/java/io/github/hyjn/nexori/plugin/portal/logic/" + filename));
    }

    @Test
    void autoTargetPlannerDoesNotImportHytaleLogger() throws IOException {
        assertFalse(source("PortalAutoTargetPlanner.java").contains("HytaleLogger"));
    }

    @Test
    void autoTargetPlannerDoesNotImportPlayerRef() throws IOException {
        assertFalse(source("PortalAutoTargetPlanner.java").contains("PlayerRef"));
    }

    @Test
    void autoTargetPlannerDoesNotImportStore() throws IOException {
        assertFalse(source("PortalAutoTargetPlanner.java").contains("Store"));
    }

    @Test
    void autoTargetPlannerDoesNotCallCurrentTimeMillis() throws IOException {
        assertFalse(source("PortalAutoTargetPlanner.java").contains("currentTimeMillis"));
    }

    @Test
    void autoTargetPlannerDoesNotUseRandomUUID() throws IOException {
        assertFalse(source("PortalAutoTargetPlanner.java").contains("randomUUID"));
    }

    @Test
    void locationMatcherDoesNotImportHytaleLogger() throws IOException {
        assertFalse(source("PortalLocationMatcher.java").contains("HytaleLogger"));
    }

    @Test
    void locationMatcherDoesNotImportPlayerRef() throws IOException {
        assertFalse(source("PortalLocationMatcher.java").contains("PlayerRef"));
    }

    @Test
    void locationMatcherDoesNotImportStore() throws IOException {
        assertFalse(source("PortalLocationMatcher.java").contains("Store"));
    }

    @Test
    void locationMatcherDoesNotCallCurrentTimeMillis() throws IOException {
        assertFalse(source("PortalLocationMatcher.java").contains("currentTimeMillis"));
    }

    @Test
    void locationMatcherDoesNotImportFilesystem() throws IOException {
        String src = source("PortalLocationMatcher.java");
        assertFalse(src.contains("java.nio.file") || src.contains("java.io.File"));
    }

    @Test
    void portalLogicDoesNotImportHytaleApi() throws IOException {
        String planner = source("PortalAutoTargetPlanner.java");
        String matcher = source("PortalLocationMatcher.java");
        assertFalse(planner.contains("com.hypixel.hytale"), "PortalAutoTargetPlanner must not import Hytale API");
        assertFalse(matcher.contains("com.hypixel.hytale"), "PortalLocationMatcher must not import Hytale API");
    }
}
