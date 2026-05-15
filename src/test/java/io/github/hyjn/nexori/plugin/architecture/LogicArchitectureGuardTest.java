package io.github.hyjn.nexori.plugin.architecture;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Global architecture guard: verifies all *.java files under any logic/ subpackage
 * do not depend on Hytale runtime, stores, logger, diagnostics, UI, filesystem, or
 * time/UUID side-effecting APIs.
 *
 * The Store guard checks import lines only (not field names, comments, or method names)
 * to avoid false positives from identifiers like "shouldStorePendingRetrySnapshot".
 *
 * Known Store import allowlist (BackendResultRecord/BackendResultPlayerRecord are
 * nested types inside BackendResultStore — a tracked design issue; these records
 * should eventually be extracted to their own classes):
 * - BackendResultPayloadBuilder.java
 * - BackendResultEnqueuePlan.java
 * - BackendResultEnqueuePlanner.java
 */
final class LogicArchitectureGuardTest {

    private static final Set<String> STORE_IMPORT_ALLOWLIST = Set.of(
        "BackendResultPayloadBuilder.java",
        "BackendResultEnqueuePlan.java",
        "BackendResultEnqueuePlanner.java"
    );

    private List<Path> allLogicFiles() throws IOException {
        Path root = Path.of("src/main/java/io/github/hyjn/nexori/plugin/");
        try (Stream<Path> walk = Files.walk(root)) {
            return walk
                .filter(p -> {
                    String normalized = p.toString().replace('\\', '/');
                    return normalized.contains("/logic/") && normalized.endsWith(".java");
                })
                .filter(p -> !p.getFileName().toString().equals("package-info.java"))
                .collect(Collectors.toList());
        }
    }

    private List<String> violations(String forbiddenPattern, Set<String> allowlist) throws IOException {
        List<String> found = new ArrayList<>();
        for (Path file : allLogicFiles()) {
            if (allowlist.contains(file.getFileName().toString())) {
                continue;
            }
            String src = Files.readString(file);
            if (src.contains(forbiddenPattern)) {
                found.add(file.getFileName().toString() + ": contains '" + forbiddenPattern + "'");
            }
        }
        return found;
    }

    private List<String> violations(String forbiddenPattern) throws IOException {
        return violations(forbiddenPattern, Set.of());
    }

    @Test
    void logicPackagesDoNotImportHytaleRuntime() throws IOException {
        List<String> found = violations("com.hypixel.hytale");
        assertTrue(found.isEmpty(),
            "Logic classes must not import Hytale runtime. Violations: " + found);
    }

    @Test
    void logicPackagesDoNotImportHytaleLogger() throws IOException {
        List<String> found = violations("HytaleLogger");
        assertTrue(found.isEmpty(),
            "Logic classes must not use HytaleLogger. Violations: " + found);
    }

    @Test
    void logicPackagesDoNotImportSecureReferralService() throws IOException {
        List<String> found = violations("SecureReferralService");
        assertTrue(found.isEmpty(),
            "Logic classes must not depend on SecureReferralService. Violations: " + found);
    }

    @Test
    void logicPackagesDoNotImportDiagnosticsService() throws IOException {
        List<String> found = violations("DiagnosticsService");
        assertTrue(found.isEmpty(),
            "Logic classes must not depend on DiagnosticsService. Violations: " + found);
    }

    @Test
    void logicPackagesDoNotImportStores() throws IOException {
        List<String> found = new ArrayList<>();
        for (Path file : allLogicFiles()) {
            if (STORE_IMPORT_ALLOWLIST.contains(file.getFileName().toString())) {
                continue;
            }
            String src = Files.readString(file);
            boolean hasStoreImport = src.lines()
                .anyMatch(line -> line.startsWith("import ") && line.contains("Store"));
            if (hasStoreImport) {
                found.add(file.getFileName().toString() + ": imports a Store class");
            }
        }
        assertTrue(found.isEmpty(),
            "Logic classes must not import Store classes (3 files allowlisted for BackendResultStore.BackendResultRecord nested types). " +
            "Violations: " + found);
    }

    @Test
    void logicPackagesDoNotImportUiOrCommandPackages() throws IOException {
        List<String> uiFound = violations("import io.github.hyjn.nexori.plugin.ui.");
        List<String> cmdFound = violations("import io.github.hyjn.nexori.plugin.command.");
        List<String> all = new ArrayList<>(uiFound);
        all.addAll(cmdFound);
        assertTrue(all.isEmpty(),
            "Logic classes must not import ui or command packages. Violations: " + all);
    }

    @Test
    void logicPackagesDoNotUseFilesystemApis() throws IOException {
        List<String> nioFound = violations("java.nio.file");
        List<String> ioFound = violations("java.io.File");
        List<String> all = new ArrayList<>(nioFound);
        all.addAll(ioFound);
        assertTrue(all.isEmpty(),
            "Logic classes must not use filesystem APIs. Violations: " + all);
    }

    @Test
    void logicPackagesDoNotUseSystemCurrentTimeMillisOrUuidRandom() throws IOException {
        List<String> timeFound = violations("System.currentTimeMillis");
        List<String> uuidFound = violations("UUID.randomUUID");
        List<String> all = new ArrayList<>(timeFound);
        all.addAll(uuidFound);
        assertTrue(all.isEmpty(),
            "Logic classes must not use System.currentTimeMillis or UUID.randomUUID (side-effecting). " +
            "Violations: " + all);
    }
}
