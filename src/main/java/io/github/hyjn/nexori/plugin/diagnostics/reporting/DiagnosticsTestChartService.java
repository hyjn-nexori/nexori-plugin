package io.github.hyjn.nexori.plugin.diagnostics.reporting;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.logger.HytaleLogger;

import javax.annotation.Nonnull;
import java.awt.Color;
import java.awt.Font;
import java.awt.GradientPaint;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.time.Instant;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Comparator;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import javax.imageio.ImageIO;

public final class DiagnosticsTestChartService {

    private static final Gson GSON = new GsonBuilder().setPrettyPrinting().disableHtmlEscaping().create();
    private static final DateTimeFormatter TS_FORMAT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
        .withZone(ZoneId.systemDefault());
    private static final int WIDTH = 760;
    private static final int HEIGHT = 220;
    private static final int KEEP_VERSIONS = 5;
    private static final Pattern VERSIONED_FILE_PATTERN = Pattern.compile("test-chart-v(\\d+)\\.png");

    private final HytaleLogger logger;
    private final Path pluginDataDirectory;
    private final Path reportsDir;
    private final Path stateFile;

    public DiagnosticsTestChartService(@Nonnull HytaleLogger logger, @Nonnull Path pluginDataDirectory) throws IOException {
        this.logger = logger;
        this.pluginDataDirectory = pluginDataDirectory;
        this.reportsDir = pluginDataDirectory.resolve("state").resolve("diagnostics").resolve("reports");
        this.stateFile = reportsDir.resolve("test-chart-state.json");
        Files.createDirectories(reportsDir);
    }

    @Nonnull
    public synchronized DiagnosticsTestChartState ensureImage() {
        DiagnosticsTestChartState current = loadState();
        if (current != null && Files.exists(reportsDir.resolve(current.currentFileName()))) {
            return current;
        }
        return regenerate();
    }

    @Nonnull
    public synchronized DiagnosticsTestChartState regenerate() {
        DiagnosticsTestChartState current = loadState();
        int nextVersion = current == null ? 1 : current.version() + 1;
        long now = System.currentTimeMillis();
        int visualValue = (int) ((now / 137L) % 100L);
        String fileName = "test-chart-v" + nextVersion + ".png";
        Path target = reportsDir.resolve(fileName);
        Path tmp = reportsDir.resolve(fileName + ".tmp");

        try {
            BufferedImage image = new BufferedImage(WIDTH, HEIGHT, BufferedImage.TYPE_INT_ARGB);
            Graphics2D g = image.createGraphics();
            try {
                g.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
                g.setPaint(new GradientPaint(0, 0, new Color(18, 32, 52), WIDTH, HEIGHT, new Color(36, 68, 106)));
                g.fillRect(0, 0, WIDTH, HEIGHT);

                g.setColor(new Color(255, 255, 255, 18));
                g.fillRoundRect(24, 24, WIDTH - 48, HEIGHT - 48, 18, 18);

                int barX = 46;
                int barY = 128;
                int barW = Math.max(40, (WIDTH - 92) * visualValue / 100);
                int barH = 28;

                g.setColor(new Color(22, 39, 58));
                g.fillRoundRect(barX, barY, WIDTH - 92, barH, 12, 12);
                g.setColor(new Color(123, 227, 166));
                g.fillRoundRect(barX, barY, barW, barH, 12, 12);

                g.setColor(new Color(241, 246, 255));
                g.setFont(new Font("SansSerif", Font.BOLD, 26));
                g.drawString("Diagnostics Test", 46, 64);

                g.setFont(new Font("SansSerif", Font.PLAIN, 16));
                g.drawString("Generated: " + TS_FORMAT.format(Instant.ofEpochMilli(now)), 46, 92);
                g.drawString("Visual value: " + visualValue, 46, 176);
            } finally {
                g.dispose();
            }

            ImageIO.write(image, "png", tmp.toFile());
            try {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
            } catch (IOException exception) {
                Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
            }

            DiagnosticsTestChartState updated = new DiagnosticsTestChartState(
                DiagnosticsTestChartState.SCHEMA_VERSION,
                nextVersion,
                fileName,
                now,
                visualValue
            );
            writeState(updated);
            cleanupOldImages();
            return updated;
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to generate the Diagnostics test chart image.", exception);
        } finally {
            try {
                Files.deleteIfExists(tmp);
            } catch (IOException ignored) {
            }
        }
    }

    @Nonnull
    public synchronized Path latestImagePath() {
        return reportsDir.resolve(ensureImage().currentFileName());
    }

    @Nonnull
    public synchronized String latestHyUiImageFilePath() {
        DiagnosticsTestChartState state = ensureImage();
        return pluginDataDirectory.getFileName()
            + "/state/diagnostics/reports/"
            + state.currentFileName();
    }

    @Nonnull
    public synchronized DiagnosticsTestChartState latestState() {
        return ensureImage();
    }

    private DiagnosticsTestChartState loadState() {
        if (!Files.exists(stateFile)) {
            return null;
        }
        try {
            String json = Files.readString(stateFile, StandardCharsets.UTF_8);
            if (json.isBlank()) {
                return null;
            }
            return GSON.fromJson(json, DiagnosticsTestChartState.class);
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to read Nexori diagnostics test chart state.");
            return null;
        }
    }

    private void writeState(@Nonnull DiagnosticsTestChartState state) throws IOException {
        String json = GSON.toJson(state);
        Path tmp = stateFile.resolveSibling(stateFile.getFileName() + ".tmp");
        Files.writeString(tmp, json, StandardCharsets.UTF_8);
        try {
            Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.move(tmp, stateFile, StandardCopyOption.REPLACE_EXISTING);
        } finally {
            Files.deleteIfExists(tmp);
        }
    }

    private void cleanupOldImages() {
        try {
            Files.list(reportsDir)
                .filter(path -> path.getFileName().toString().startsWith("test-chart-v") && path.getFileName().toString().endsWith(".png"))
                .sorted(Comparator.comparingInt(DiagnosticsTestChartService::chartVersion).reversed())
                .skip(KEEP_VERSIONS)
                .forEach(path -> {
                    try {
                        Files.deleteIfExists(path);
                    } catch (IOException ignored) {
                    }
                });
        } catch (IOException ignored) {
        }
    }

    private static int chartVersion(@Nonnull Path path) {
        Matcher matcher = VERSIONED_FILE_PATTERN.matcher(path.getFileName().toString());
        if (!matcher.matches()) {
            return -1;
        }
        try {
            return Integer.parseInt(matcher.group(1));
        } catch (NumberFormatException ignored) {
            return -1;
        }
    }
}
