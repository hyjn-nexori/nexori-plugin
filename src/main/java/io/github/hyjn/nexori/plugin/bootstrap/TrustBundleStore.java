package io.github.hyjn.nexori.plugin.bootstrap;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

public final class TrustBundleStore {

    private static final Gson GSON = new GsonBuilder()
        .setPrettyPrinting()
        .create();

    private final Path file;
    private TrustBundle currentBundle = TrustBundle.initial();

    public TrustBundleStore(@Nonnull Path file) {
        this.file = file;
    }

    @Nonnull
    public synchronized TrustBundle loadOrCreate() throws IOException {
        ensureParent();
        if (!Files.exists(file)) {
            persist(currentBundle);
            return currentBundle;
        }

        String json = Files.readString(file, StandardCharsets.UTF_8);
        if (json.isBlank()) {
            persist(currentBundle);
            return currentBundle;
        }

        TrustBundle loaded = GSON.fromJson(json, TrustBundle.class);
        this.currentBundle = loaded == null ? TrustBundle.initial() : loaded;
        return currentBundle;
    }

    @Nonnull
    public synchronized TrustBundle getCurrentBundle() {
        return currentBundle == null ? TrustBundle.initial() : currentBundle;
    }

    @Nonnull
    public synchronized TrustBundle saveVerifiedMembers(
        @Nonnull ServerIdentity localIdentity,
        @Nonnull String localConnectionAddress,
        @Nonnull List<BundleMember> remoteMembers,
        long bundleVersion
    ) {
        try {
            Map<String, BundleMember> membersByServerId = new LinkedHashMap<>();
            String localServerId = localIdentity.serverId().toString();
            membersByServerId.put(localServerId, new BundleMember(
                localIdentity.serverId().toString(),
                localConnectionAddress == null ? "" : localConnectionAddress,
                localIdentity.fingerprint(),
                localIdentity.publicKeyBase64(),
                Instant.now().toEpochMilli()
            ));
            for (BundleMember remoteMember : remoteMembers) {
                if (localServerId.equals(remoteMember.serverId())) {
                    continue;
                }
                membersByServerId.put(remoteMember.serverId(), remoteMember);
            }

            List<BundleMember> members = new ArrayList<>(membersByServerId.values());
            members.sort(Comparator.comparing(BundleMember::serverId));

            String bundleHash = fingerprintFor(GSON.toJson(members));
            TrustBundle bundle = new TrustBundle(
                bundleVersion,
                bundleHash,
                Instant.now().toEpochMilli(),
                List.copyOf(members)
            );
            persist(bundle);
            return bundle;
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to persist Nexori trust bundle", exception);
        }
    }

    @Nonnull
    public synchronized TrustBundle installBundle(@Nonnull TrustBundle bundle) {
        try {
            String expectedHash = fingerprintFor(GSON.toJson(bundle.members()));
            if (!expectedHash.equals(bundle.bundleHash())) {
                throw new IllegalArgumentException("Received Nexori bundle hash does not match its member payload.");
            }
            persist(bundle);
            return bundle;
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to install Nexori trust bundle", exception);
        }
    }

    private void persist(@Nonnull TrustBundle bundle) throws IOException {
        ensureParent();
        String json = GSON.toJson(bundle);
        Path tmp = file.resolveSibling(file.getFileName() + ".tmp");
        try {
            Files.writeString(tmp, json, StandardCharsets.UTF_8);
            Files.move(tmp, file, StandardCopyOption.REPLACE_EXISTING, StandardCopyOption.ATOMIC_MOVE);
        } catch (IOException exception) {
            Files.writeString(file, json, StandardCharsets.UTF_8);
            Files.deleteIfExists(tmp);
        }
        this.currentBundle = bundle;
    }

    private String fingerprintFor(@Nonnull String rawJson) throws GeneralSecurityException {
        MessageDigest digest = MessageDigest.getInstance("SHA-256");
        return HexFormat.of().formatHex(digest.digest(rawJson.getBytes(StandardCharsets.UTF_8)));
    }

    private void ensureParent() throws IOException {
        Path parent = file.getParent();
        if (parent != null) {
            Files.createDirectories(parent);
        }
    }
}
