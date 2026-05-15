package io.github.hyjn.nexori.plugin.bootstrap;

import com.hypixel.hytale.logger.HytaleLogger;
import io.github.hyjn.nexori.plugin.bootstrap.logic.BootstrapTextMigrationPlanner;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.List;
import java.util.stream.Stream;

public final class BootstrapPersistenceMigrationService {

    private final HytaleLogger logger;
    private final Path dataDirectory;

    public BootstrapPersistenceMigrationService(@Nonnull HytaleLogger logger, @Nonnull Path dataDirectory) {
        this.logger = logger;
        this.dataDirectory = dataDirectory;
    }

    @Nonnull
    public MigrationReport apply(@Nonnull BootstrapMigrationPlan plan) throws IOException {
        if (plan.isEmpty() || !Files.exists(dataDirectory)) {
            return new MigrationReport(0, 0);
        }

        int scannedFiles = 0;
        int changedFiles = 0;
        try (Stream<Path> stream = Files.walk(dataDirectory)) {
            for (Path file : stream.filter(Files::isRegularFile).toList()) {
                scannedFiles++;
                if (rewriteFileIfNeeded(file, plan.replacements())) {
                    changedFiles++;
                }
            }
        }
        return new MigrationReport(scannedFiles, changedFiles);
    }

    private boolean rewriteFileIfNeeded(@Nonnull Path file, @Nonnull List<BootstrapTextReplacement> replacements) throws IOException {
        String original = readUtf8OrNull(file);
        if (original == null || original.isEmpty()) {
            return false;
        }

        String updated = BootstrapTextMigrationPlanner.applyReplacements(
            original, file.getFileName().toString(), replacements);

        if (updated.equals(original)) {
            return false;
        }

        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, updated, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.writeString(file, updated, StandardCharsets.UTF_8);
            Files.deleteIfExists(tmp);
        }
        logger.atInfo().log("Nexori bootstrap migration rewrote " + file + ".");
        return true;
    }

    private String readUtf8OrNull(@Nonnull Path file) throws IOException {
        byte[] bytes = Files.readAllBytes(file);
        try {
            return StandardCharsets.UTF_8.newDecoder()
                .onMalformedInput(CodingErrorAction.REPORT)
                .onUnmappableCharacter(CodingErrorAction.REPORT)
                .decode(ByteBuffer.wrap(bytes))
                .toString();
        } catch (CharacterCodingException exception) {
            return null;
        }
    }

    public record MigrationReport(int scannedFiles, int changedFiles) {
    }
}
