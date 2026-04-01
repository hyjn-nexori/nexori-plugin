package io.github.hyjn.nexori.plugin.diagnostics.collect;

import com.google.gson.Gson;
import com.google.gson.GsonBuilder;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.logger.HytaleLogger;
import com.hypixel.hytale.math.vector.Transform;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.modules.entity.teleport.Teleport;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.protocol.HostAddress;
import io.github.hyjn.nexori.plugin.bootstrap.BundleMember;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundleStore;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsEvent;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsSegmentMetadata;
import io.github.hyjn.nexori.plugin.diagnostics.collect.payload.DiagnosticsCollectChunkRequestPayload;
import io.github.hyjn.nexori.plugin.diagnostics.collect.payload.DiagnosticsCollectChunkResponsePayload;
import io.github.hyjn.nexori.plugin.diagnostics.collect.payload.DiagnosticsCollectErrorPayload;
import io.github.hyjn.nexori.plugin.diagnostics.collect.payload.DiagnosticsCollectManifestRequestPayload;
import io.github.hyjn.nexori.plugin.diagnostics.collect.payload.DiagnosticsCollectManifestResponsePayload;
import io.github.hyjn.nexori.plugin.diagnostics.protocol.DiagnosticsProtocol;
import io.github.hyjn.nexori.plugin.secure.SecureReferralHandler;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;
import io.github.hyjn.nexori.plugin.secure.VerifiedSecureReferral;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.DirectoryStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.time.Instant;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

public final class DiagnosticsCollectService {

    private static final Gson GSON = new GsonBuilder().disableHtmlEscaping().create();
    private static final Duration REFERRAL_TTL = Duration.ofSeconds(30);
    // A collect lock becomes stale only for in-flight work. READY sessions stay resumable without expiring.
    private static final long STALE_LOCK_MILLIS = Duration.ofMinutes(5).toMillis();
    private static final long MAX_SESSION_BYTES = 32L * 1024L * 1024L;
    private static final long MAX_SESSION_EVENTS = 25_000L;
    private static final int TARGET_OPERATIONAL_ENCODED_BYTES = 3500;

    private final HytaleLogger logger;
    private final String localServerId;
    private final TrustBundleStore trustBundleStore;
    private final SecureReferralService secureReferralService;
    private final DiagnosticsCollectSessionStore sessionStore;
    private final Path journalDir;
    private final Map<UUID, PendingCollectReturn> pendingReturns = new ConcurrentHashMap<>();
    private final SecureReferralHandler manifestRequestHandler = new ManifestRequestHandler();
    private final SecureReferralHandler manifestResponseHandler = new ManifestResponseHandler();
    private final SecureReferralHandler chunkRequestHandler = new ChunkRequestHandler();
    private final SecureReferralHandler chunkResponseHandler = new ChunkResponseHandler();
    private final SecureReferralHandler errorHandler = new ErrorHandler();

    public DiagnosticsCollectService(
        @Nonnull HytaleLogger logger,
        @Nonnull Path pluginDataDirectory,
        @Nonnull UUID localServerId,
        @Nonnull TrustBundleStore trustBundleStore,
        @Nonnull SecureReferralService secureReferralService
    ) throws IOException {
        this.logger = logger;
        this.localServerId = localServerId.toString();
        this.trustBundleStore = trustBundleStore;
        this.secureReferralService = secureReferralService;
        this.sessionStore = new DiagnosticsCollectSessionStore(pluginDataDirectory);
        this.journalDir = pluginDataDirectory.resolve("state").resolve("diagnostics").resolve("journal");
        Files.createDirectories(journalDir);
    }

    @Nonnull
    public SecureReferralHandler manifestRequestHandler() { return manifestRequestHandler; }

    @Nonnull
    public SecureReferralHandler manifestResponseHandler() { return manifestResponseHandler; }

    @Nonnull
    public SecureReferralHandler chunkRequestHandler() { return chunkRequestHandler; }

    @Nonnull
    public SecureReferralHandler chunkResponseHandler() { return chunkResponseHandler; }

    @Nonnull
    public SecureReferralHandler errorHandler() { return errorHandler; }

    @Nonnull
    public synchronized CollectUiView loadUiView() {
        Optional<DiagnosticsCollectActiveLock> lock = sessionStore.loadActiveLock();
        Optional<DiagnosticsCollectSession> lockedSession = lock.flatMap(activeLock -> sessionStore.loadSession(activeLock.sessionId()));
        Optional<DiagnosticsCollectSession> latestSession = lockedSession.isPresent() ? lockedSession : sessionStore.loadLatestSession();
        boolean staleLock = lock.isPresent() && isStale(lock.get());
        return new CollectUiView(
            latestSession.orElse(null),
            lock.orElse(null),
            staleLock,
            buildRemoteRows(latestSession.orElse(null))
        );
    }

    public synchronized void plan(
        @Nonnull PlayerRef playerRef,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        @Nonnull DiagnosticsCollectWindowPreset windowPreset
    ) throws IOException, GeneralSecurityException {
        Optional<DiagnosticsCollectActiveLock> lock = sessionStore.loadActiveLock();
        if (lock.isPresent()) {
            if (isStale(lock.get())) {
                throw new IllegalStateException("The current diagnostics collect session is stale. Use Force Resume or Force Unlock before starting another one.");
            }
            throw new IllegalStateException("A diagnostics collect session is already active on this server.");
        }

        List<RemotePeer> remotes = trustedRemotePeers();
        String sessionId = UUID.randomUUID().toString();
        DiagnosticsCollectWindow window = windowPreset.resolve(System.currentTimeMillis());
        List<DiagnosticsCollectServerProgress> servers = remotes.stream()
            .map(remote -> new DiagnosticsCollectServerProgress(
                remote.serverId(),
                remote.connectionAddress(),
                DiagnosticsCollectStatus.PENDING,
                0L,
                0L,
                0L,
                0L,
                null,
                null,
                List.of()
            ))
            .toList();

        DiagnosticsCollectSession session = new DiagnosticsCollectSession(
            DiagnosticsCollectSession.SCHEMA_VERSION,
            sessionId,
            DiagnosticsCollectStatus.PLANNING,
            System.currentTimeMillis(),
            System.currentTimeMillis(),
            windowPreset.id(),
            window.startEpochMs(),
            window.endEpochMs(),
            playerRef.getUuid().toString(),
            DiagnosticsCollectOriginSnapshot.capture(originWorldName, originTransform),
            servers,
            remotes.isEmpty() ? null : remotes.getFirst().serverId(),
            null,
            0L,
            0L,
            0L,
            0L,
            null
        );
        persistSession(session);
        sessionStore.saveActiveLock(new DiagnosticsCollectActiveLock(sessionId, DiagnosticsCollectStatus.PLANNING, session.updatedAtEpochMs()));

        if (remotes.isEmpty()) {
            completeSession(sessionId);
            return;
        }

        sendManifestRequest(session, remotes.getFirst(), 0, playerRef);
    }

    public synchronized void startOrResume(
        @Nonnull PlayerRef playerRef,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform,
        boolean forceResume
    ) throws IOException, GeneralSecurityException {
        DiagnosticsCollectSession session = resolveSessionForResume(forceResume);
        DiagnosticsCollectSession resumed = new DiagnosticsCollectSession(
            session.schemaVersion(),
            session.sessionId(),
            session.status() == DiagnosticsCollectStatus.PLANNING ? DiagnosticsCollectStatus.PLANNING : DiagnosticsCollectStatus.RUNNING,
            session.createdAtEpochMs(),
            System.currentTimeMillis(),
            session.windowPresetId(),
            session.windowStartEpochMs(),
            session.windowEndEpochMs(),
            playerRef.getUuid().toString(),
            DiagnosticsCollectOriginSnapshot.capture(originWorldName, originTransform),
            session.servers(),
            session.currentServerId(),
            null,
            session.estimatedTotalBytes(),
            session.downloadedBytes(),
            session.estimatedTotalEvents(),
            session.downloadedEvents(),
            null
        );
        persistSession(resumed);
        sessionStore.saveActiveLock(new DiagnosticsCollectActiveLock(resumed.sessionId(), resumed.status(), resumed.updatedAtEpochMs()));
        continueSession(playerRef, resumed);
    }

    public synchronized void retryFailed(
        @Nonnull PlayerRef playerRef,
        @Nonnull String originWorldName,
        @Nonnull Transform originTransform
    ) throws IOException, GeneralSecurityException {
        startOrResume(playerRef, originWorldName, originTransform, false);
    }

    public synchronized void cancel() {
        Optional<DiagnosticsCollectActiveLock> lock = sessionStore.loadActiveLock();
        if (lock.isEmpty()) {
            return;
        }
        DiagnosticsCollectSession session = sessionStore.loadSession(lock.get().sessionId()).orElse(null);
        if (session == null) {
            sessionStore.clearActiveLock();
            return;
        }
        DiagnosticsCollectSession cancelled = new DiagnosticsCollectSession(
            session.schemaVersion(),
            session.sessionId(),
            DiagnosticsCollectStatus.CANCELLED,
            session.createdAtEpochMs(),
            System.currentTimeMillis(),
            session.windowPresetId(),
            session.windowStartEpochMs(),
            session.windowEndEpochMs(),
            session.originPlayerUuid(),
            session.originSnapshot(),
            session.servers(),
            session.currentServerId(),
            null,
            session.estimatedTotalBytes(),
            session.downloadedBytes(),
            session.estimatedTotalEvents(),
            session.downloadedEvents(),
            "The diagnostics collect session was cancelled."
        );
        persistSession(cancelled);
        sessionStore.clearActiveLock();
    }

    public synchronized void forceUnlock() {
        Optional<DiagnosticsCollectActiveLock> lock = sessionStore.loadActiveLock();
        if (lock.isEmpty()) {
            return;
        }
        DiagnosticsCollectSession session = sessionStore.loadSession(lock.get().sessionId()).orElse(null);
        if (session != null) {
            DiagnosticsCollectSession failed = new DiagnosticsCollectSession(
                session.schemaVersion(),
                session.sessionId(),
                DiagnosticsCollectStatus.FAILED,
                session.createdAtEpochMs(),
                System.currentTimeMillis(),
                session.windowPresetId(),
                session.windowStartEpochMs(),
                session.windowEndEpochMs(),
                session.originPlayerUuid(),
                session.originSnapshot(),
                session.servers(),
                session.currentServerId(),
                null,
                session.estimatedTotalBytes(),
                session.downloadedBytes(),
                session.estimatedTotalEvents(),
                session.downloadedEvents(),
                "The diagnostics collect session was force-unlocked by an admin."
            );
            persistSession(failed);
        }
        sessionStore.clearActiveLock();
    }

    public void handlePlayerReady(@Nonnull PlayerReadyEvent event) {
        PlayerRef playerRef = event.getPlayerRef().getStore().getComponent(
            event.getPlayerRef(),
            Universe.get().getPlayerRefComponentType()
        );
        if (playerRef == null) {
            return;
        }

        PendingCollectReturn pendingReturn = pendingReturns.remove(playerRef.getUuid());
        if (pendingReturn == null) {
            return;
        }

        finalizeReturn(event, pendingReturn.originSnapshot(), pendingReturn.message(), pendingReturn.reopenUi());
    }

    private void finalizeReturn(
        @Nonnull PlayerReadyEvent event,
        @Nonnull DiagnosticsCollectOriginSnapshot originSnapshot,
        @Nonnull String message,
        boolean reopenUi
    ) {
        World world = Universe.get().getWorld(originSnapshot.worldName());
        Teleport teleport = world == null
            ? Teleport.createForPlayer(originSnapshot.toTransform())
            : Teleport.createForPlayer(world, originSnapshot.toTransform());
        Ref<EntityStore> ref = event.getPlayerRef();
        Store<EntityStore> store = ref.getStore();
        store.addComponent(ref, Teleport.getComponentType(), teleport);
        event.getPlayer().sendMessage(Message.raw(message));
        if (reopenUi) {
            event.getPlayer().sendMessage(Message.raw("Open the Nexori diagnostics page again to review the latest collect status."));
        }
    }

    @Nonnull
    private DiagnosticsCollectSession resolveSessionForResume(boolean forceResume) {
        Optional<DiagnosticsCollectActiveLock> lock = sessionStore.loadActiveLock();
        if (lock.isPresent()) {
            if (!isStale(lock.get()) || forceResume) {
                return sessionStore.loadSession(lock.get().sessionId())
                    .orElseThrow(() -> new IllegalStateException("The active diagnostics collect session is missing."));
            }
            throw new IllegalStateException("The diagnostics collect session is stale. Use Force Resume or Force Unlock.");
        }

        DiagnosticsCollectSession latest = sessionStore.loadLatestSession()
            .orElseThrow(() -> new IllegalStateException("No diagnostics collect session is available to resume."));
        if (latest.status() != DiagnosticsCollectStatus.READY && latest.status() != DiagnosticsCollectStatus.FAILED) {
            throw new IllegalStateException("The latest diagnostics collect session cannot be resumed.");
        }
        return latest;
    }

    private void continueSession(@Nonnull PlayerRef playerRef, @Nonnull DiagnosticsCollectSession session) throws IOException, GeneralSecurityException {
        sessionStore.saveActiveLock(new DiagnosticsCollectActiveLock(session.sessionId(), session.status(), System.currentTimeMillis()));
        if (session.status() == DiagnosticsCollectStatus.PLANNING) {
            continuePlanning(playerRef, session);
            return;
        }
        if (session.status() == DiagnosticsCollectStatus.READY) {
            DiagnosticsCollectSession running = rewriteSessionStatus(session, DiagnosticsCollectStatus.RUNNING, null);
            persistSession(running);
            continueRunning(playerRef, running);
            return;
        }
        if (session.status() == DiagnosticsCollectStatus.RUNNING) {
            continueRunning(playerRef, session);
            return;
        }
        if (session.status() == DiagnosticsCollectStatus.FAILED) {
            DiagnosticsCollectSession running = rewriteSessionStatus(session, DiagnosticsCollectStatus.RUNNING, null);
            persistSession(running);
            continueRunning(playerRef, running);
            return;
        }
        throw new IllegalStateException("The diagnostics collect session is not in a resumable state.");
    }

    private void continueSession(@Nonnull PlayerSetupConnectEvent event, @Nonnull DiagnosticsCollectSession session) throws IOException, GeneralSecurityException {
        sessionStore.saveActiveLock(new DiagnosticsCollectActiveLock(session.sessionId(), session.status(), System.currentTimeMillis()));
        if (session.status() == DiagnosticsCollectStatus.PLANNING) {
            continuePlanning(event, session);
            return;
        }
        if (session.status() == DiagnosticsCollectStatus.READY) {
            DiagnosticsCollectSession running = rewriteSessionStatus(session, DiagnosticsCollectStatus.RUNNING, null);
            persistSession(running);
            continueRunning(event, running);
            return;
        }
        if (session.status() == DiagnosticsCollectStatus.RUNNING) {
            continueRunning(event, session);
            return;
        }
        if (session.status() == DiagnosticsCollectStatus.FAILED) {
            DiagnosticsCollectSession running = rewriteSessionStatus(session, DiagnosticsCollectStatus.RUNNING, null);
            persistSession(running);
            continueRunning(event, running);
            return;
        }
        throw new IllegalStateException("The diagnostics collect session is not in a resumable state.");
    }

    private void continuePlanning(@Nonnull PlayerRef playerRef, @Nonnull DiagnosticsCollectSession session) throws IOException, GeneralSecurityException {
        for (DiagnosticsCollectServerProgress server : session.servers()) {
            Optional<DiagnosticsCollectManifest> manifest = sessionStore.loadManifest(session.sessionId(), server.remoteServerId());
            if (manifest.isPresent()) {
                if (!manifest.get().complete()) {
                    RemotePeer remote = findRemotePeer(server.remoteServerId())
                        .orElseThrow(() -> new IllegalStateException("Trusted server " + server.remoteConnectionAddress() + " is no longer available."));
                    sendManifestRequest(session, remote, manifest.get().nextEntryIndex(), playerRef);
                    return;
                }
                continue;
            }
            RemotePeer remote = findRemotePeer(server.remoteServerId())
                .orElseThrow(() -> new IllegalStateException("Trusted server " + server.remoteConnectionAddress() + " is no longer available."));
            sendManifestRequest(session, remote, 0, playerRef);
            return;
        }
        DiagnosticsCollectSession ready = rewriteSessionStatus(session, DiagnosticsCollectStatus.READY, null);
        persistSession(ready);
        finalizePendingReturn(playerRef.getUuid(), ready, "Diagnostics collect plan is ready. Review the estimated bytes and start when you want to import them.", true);
    }

    private void continuePlanning(@Nonnull PlayerSetupConnectEvent event, @Nonnull DiagnosticsCollectSession session) throws IOException, GeneralSecurityException {
        for (DiagnosticsCollectServerProgress server : session.servers()) {
            Optional<DiagnosticsCollectManifest> manifest = sessionStore.loadManifest(session.sessionId(), server.remoteServerId());
            if (manifest.isPresent()) {
                if (!manifest.get().complete()) {
                    RemotePeer remote = findRemotePeer(server.remoteServerId())
                        .orElseThrow(() -> new IllegalStateException("Trusted server " + server.remoteConnectionAddress() + " is no longer available."));
                    sendManifestRequest(session, remote, manifest.get().nextEntryIndex(), event);
                    return;
                }
                continue;
            }
            RemotePeer remote = findRemotePeer(server.remoteServerId())
                .orElseThrow(() -> new IllegalStateException("Trusted server " + server.remoteConnectionAddress() + " is no longer available."));
            sendManifestRequest(session, remote, 0, event);
            return;
        }
        DiagnosticsCollectSession ready = rewriteSessionStatus(session, DiagnosticsCollectStatus.READY, null);
        persistSession(ready);
        finalizePendingReturn(event.getUuid(), ready, "Diagnostics collect plan is ready. Review the estimated bytes and start when you want to import them.", true);
    }

    private void continueRunning(@Nonnull PlayerRef playerRef, @Nonnull DiagnosticsCollectSession session) throws IOException, GeneralSecurityException {
        for (DiagnosticsCollectServerProgress server : session.servers()) {
            for (DiagnosticsCollectFileProgress file : server.files()) {
                if (file.completed()) {
                    continue;
                }
                RemotePeer remote = findRemotePeer(server.remoteServerId())
                    .orElseThrow(() -> new IllegalStateException("Trusted server " + server.remoteConnectionAddress() + " is no longer available."));
                sendChunkRequest(session, server, file, remote, playerRef);
                return;
            }
        }
        DiagnosticsCollectSession completed = completeSession(session.sessionId());
        finalizePendingReturn(playerRef.getUuid(), completed == null ? session : completed, "Diagnostics collect completed successfully.", true);
    }

    private void continueRunning(@Nonnull PlayerSetupConnectEvent event, @Nonnull DiagnosticsCollectSession session) throws IOException, GeneralSecurityException {
        for (DiagnosticsCollectServerProgress server : session.servers()) {
            for (DiagnosticsCollectFileProgress file : server.files()) {
                if (file.completed()) {
                    continue;
                }
                DiagnosticsCollectServerProgress runningServer = server.status() == DiagnosticsCollectStatus.RUNNING && file.fileId().equals(server.currentFileId())
                    ? server
                    : new DiagnosticsCollectServerProgress(
                        server.remoteServerId(),
                        server.remoteConnectionAddress(),
                        DiagnosticsCollectStatus.RUNNING,
                        server.estimatedBytes(),
                        server.downloadedBytes(),
                        server.estimatedEvents(),
                        server.downloadedEvents(),
                        file.fileId(),
                        null,
                        server.files()
                    );
                DiagnosticsCollectSession updated = runningServer == server ? session : replaceServer(session, runningServer);
                if (updated != session) {
                    updated = rewriteSessionRequest(updated, server.remoteServerId(), null, null);
                    persistSession(updated);
                }
                RemotePeer remote = findRemotePeer(server.remoteServerId())
                    .orElseThrow(() -> new IllegalStateException("Trusted server " + server.remoteConnectionAddress() + " is no longer available."));
                sendChunkRequest(updated, runningServer, file, remote, event);
                return;
            }
        }
        DiagnosticsCollectSession completed = completeSession(session.sessionId());
        finalizePendingReturn(event.getUuid(), completed == null ? session : completed, "Diagnostics collect completed successfully.", true);
    }

    private void sendManifestRequest(
        @Nonnull DiagnosticsCollectSession session,
        @Nonnull RemotePeer remote,
        int startEntryIndex,
        @Nonnull PlayerRef playerRef
    ) throws IOException, GeneralSecurityException {
        String requestId = UUID.randomUUID().toString();
        DiagnosticsCollectManifestRequestPayload payload = new DiagnosticsCollectManifestRequestPayload(
            session.sessionId(),
            requestId,
            session.windowStartEpochMs(),
            session.windowEndEpochMs(),
            startEntryIndex
        );
        DiagnosticsCollectSession updated = rewriteSessionRequest(session, remote.serverId(), requestId, null);
        persistSession(updated);
        secureReferralService.referPlayer(
            playerRef,
            remote.host(),
            remote.port(),
            DiagnosticsProtocol.COLLECT_MANIFEST_REQUEST,
            payload,
            REFERRAL_TTL
        );
    }

    private void sendManifestRequest(
        @Nonnull DiagnosticsCollectSession session,
        @Nonnull RemotePeer remote,
        int startEntryIndex,
        @Nonnull PlayerSetupConnectEvent event
    ) throws IOException, GeneralSecurityException {
        String requestId = UUID.randomUUID().toString();
        DiagnosticsCollectManifestRequestPayload payload = new DiagnosticsCollectManifestRequestPayload(
            session.sessionId(),
            requestId,
            session.windowStartEpochMs(),
            session.windowEndEpochMs(),
            startEntryIndex
        );
        DiagnosticsCollectSession updated = rewriteSessionRequest(session, remote.serverId(), requestId, null);
        persistSession(updated);
        byte[] encoded = secureReferralService.createPayload(
            event.getUuid(),
            event.getUsername(),
            DiagnosticsProtocol.COLLECT_MANIFEST_REQUEST,
            payload,
            REFERRAL_TTL
        );
        event.referToServer(remote.host(), remote.port(), encoded);
    }

    private void sendChunkRequest(
        @Nonnull DiagnosticsCollectSession session,
        @Nonnull DiagnosticsCollectServerProgress server,
        @Nonnull DiagnosticsCollectFileProgress file,
        @Nonnull RemotePeer remote,
        @Nonnull PlayerRef playerRef
    ) throws IOException, GeneralSecurityException {
        String requestId = UUID.randomUUID().toString();
        DiagnosticsCollectChunkRequestPayload payload = new DiagnosticsCollectChunkRequestPayload(
            session.sessionId(),
            requestId,
            file.fileId(),
            file.closed(),
            file.expectedFileSha256(),
            file.snapshotLineCount(),
            file.snapshotByteSize(),
            file.snapshotEndedAtEpochMs(),
            file.snapshotPrefixSha256(),
            file.windowStartLineInclusive(),
            file.windowEndLineExclusive(),
            file.nextLineInclusive()
        );
        DiagnosticsCollectSession updated = rewriteSessionRequest(session, server.remoteServerId(), requestId, null);
        persistSession(updated);
        secureReferralService.referPlayer(
            playerRef,
            remote.host(),
            remote.port(),
            DiagnosticsProtocol.COLLECT_CHUNK_REQUEST,
            payload,
            REFERRAL_TTL
        );
    }

    private void sendChunkRequest(
        @Nonnull DiagnosticsCollectSession session,
        @Nonnull DiagnosticsCollectServerProgress server,
        @Nonnull DiagnosticsCollectFileProgress file,
        @Nonnull RemotePeer remote,
        @Nonnull PlayerSetupConnectEvent event
    ) throws IOException, GeneralSecurityException {
        String requestId = UUID.randomUUID().toString();
        DiagnosticsCollectChunkRequestPayload payload = new DiagnosticsCollectChunkRequestPayload(
            session.sessionId(),
            requestId,
            file.fileId(),
            file.closed(),
            file.expectedFileSha256(),
            file.snapshotLineCount(),
            file.snapshotByteSize(),
            file.snapshotEndedAtEpochMs(),
            file.snapshotPrefixSha256(),
            file.windowStartLineInclusive(),
            file.windowEndLineExclusive(),
            file.nextLineInclusive()
        );
        DiagnosticsCollectSession updated = rewriteSessionRequest(session, server.remoteServerId(), requestId, null);
        persistSession(updated);
        byte[] encoded = secureReferralService.createPayload(
            event.getUuid(),
            event.getUsername(),
            DiagnosticsProtocol.COLLECT_CHUNK_REQUEST,
            payload,
            REFERRAL_TTL
        );
        event.referToServer(remote.host(), remote.port(), encoded);
    }

    private void handleManifestRequest(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        DiagnosticsCollectManifestRequestPayload payload = referral.decodePayload(
            secureReferralService.gson(),
            DiagnosticsCollectManifestRequestPayload.class
        );
        HostAddress referralSource = event.getReferralSource();
        if (referralSource == null || referralSource.host == null) {
            return;
        }

        try {
            List<DiagnosticsCollectManifestEntry> allEntries = buildManifestEntries(payload.windowStartEpochMs(), payload.windowEndEpochMs());
            int nextIndex = payload.startEntryIndex();
            List<DiagnosticsCollectManifestEntry> page = new ArrayList<>();
            byte[] encoded = null;
            while (nextIndex < allEntries.size()) {
                page.add(allEntries.get(nextIndex));
                DiagnosticsCollectManifestResponsePayload candidate = new DiagnosticsCollectManifestResponsePayload(
                    payload.sessionId(),
                    payload.requestId(),
                    localServerId,
                    localConnectionAddress(),
                    payload.startEntryIndex(),
                    nextIndex + 1,
                    nextIndex + 1 < allEntries.size(),
                    List.copyOf(page)
                );
                byte[] candidateBytes = secureReferralService.createPayload(
                    event.getUuid(),
                    event.getUsername(),
                    DiagnosticsProtocol.COLLECT_MANIFEST_RESPONSE,
                    candidate,
                    REFERRAL_TTL
                );
                if (candidateBytes.length > TARGET_OPERATIONAL_ENCODED_BYTES && page.size() > 1) {
                    page.removeLast();
                    nextIndex -= 1;
                    break;
                }
                if (candidateBytes.length > TARGET_OPERATIONAL_ENCODED_BYTES) {
                    throw new IllegalStateException("A single diagnostics manifest entry exceeded the collect payload budget.");
                }
                encoded = candidateBytes;
                nextIndex += 1;
            }
            if (encoded == null) {
                DiagnosticsCollectManifestResponsePayload empty = new DiagnosticsCollectManifestResponsePayload(
                    payload.sessionId(),
                    payload.requestId(),
                    localServerId,
                    localConnectionAddress(),
                    payload.startEntryIndex(),
                    payload.startEntryIndex(),
                    false,
                    List.of()
                );
                encoded = secureReferralService.createPayload(
                    event.getUuid(),
                    event.getUsername(),
                    DiagnosticsProtocol.COLLECT_MANIFEST_RESPONSE,
                    empty,
                    REFERRAL_TTL
                );
            }
            event.referToServer(referralSource.host, referralSource.port, encoded);
        } catch (IOException | GeneralSecurityException | IllegalStateException exception) {
            sendOperationalError(event, payload.sessionId(), payload.requestId(), null, "DIAGNOSTICS_COLLECT_MANIFEST_FAILED", safeMessage(exception));
        }
    }

    private void handleManifestResponse(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        DiagnosticsCollectManifestResponsePayload payload = referral.decodePayload(
            secureReferralService.gson(),
            DiagnosticsCollectManifestResponsePayload.class
        );
        DiagnosticsCollectSession session = sessionStore.loadSession(payload.sessionId()).orElse(null);
        if (session == null || session.currentRequestId() == null || !session.currentRequestId().equals(payload.requestId())) {
            return;
        }

        DiagnosticsCollectServerProgress server = findServer(session, payload.sourceServerId());
        if (server == null) {
            return;
        }

        DiagnosticsCollectManifest existing = sessionStore.loadManifest(session.sessionId(), server.remoteServerId())
            .orElse(new DiagnosticsCollectManifest(
                server.remoteServerId(),
                payload.sourceConnectionAddress(),
                session.windowStartEpochMs(),
                session.windowEndEpochMs(),
                List.of(),
                payload.startEntryIndex(),
                false,
                System.currentTimeMillis()
            ));
        List<DiagnosticsCollectManifestEntry> combinedEntries = new ArrayList<>(existing.entries());
        combinedEntries.addAll(payload.entries());
        DiagnosticsCollectManifest manifest = new DiagnosticsCollectManifest(
            existing.remoteServerId(),
            payload.sourceConnectionAddress(),
            existing.windowStartEpochMs(),
            existing.windowEndEpochMs(),
            List.copyOf(combinedEntries),
            payload.nextEntryIndex(),
            !payload.hasMoreEntries(),
            System.currentTimeMillis()
        );
        sessionStore.saveManifest(session.sessionId(), server.remoteServerId(), manifest);

        if (payload.hasMoreEntries()) {
            DiagnosticsCollectSession updated = rewriteSessionRequest(session, server.remoteServerId(), null, null);
            persistSession(updated);
            try {
                continuePlanning(event, updated);
            } catch (IOException | GeneralSecurityException | RuntimeException exception) {
                String error = "Diagnostics collect failed while planning: " + safeMessage(exception);
                failSession(updated.sessionId(), error);
                finalizePendingReturn(event.getUuid(), updated, error, true);
            }
            return;
        }

        DiagnosticsCollectServerProgress updatedServer = progressFromManifest(server, manifest);
        DiagnosticsCollectSession updated = replaceServer(session, updatedServer);
        updated = rewriteSessionRequest(updated, null, null, null);
        updated = recomputeSessionTotals(updated);
        if (updated.estimatedTotalBytes() > MAX_SESSION_BYTES || updated.estimatedTotalEvents() > MAX_SESSION_EVENTS) {
            failSession(updated.sessionId(), "Diagnostics collect plan exceeds the v1 safety limits for bytes or events. Narrow the time window and try again.");
            finalizePendingReturn(event.getUuid(), updated, "Diagnostics collect plan exceeds the current safety limits. Narrow the time window and try again.", true);
            return;
        }
        persistSession(updated);
        try {
            continuePlanning(event, updated);
        } catch (IOException | GeneralSecurityException | RuntimeException exception) {
            String error = "Diagnostics collect failed while planning: " + safeMessage(exception);
            failSession(updated.sessionId(), error);
            finalizePendingReturn(event.getUuid(), updated, error, true);
        }
    }

    private void handleChunkRequest(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        DiagnosticsCollectChunkRequestPayload payload = referral.decodePayload(
            secureReferralService.gson(),
            DiagnosticsCollectChunkRequestPayload.class
        );
        HostAddress referralSource = event.getReferralSource();
        if (referralSource == null || referralSource.host == null) {
            return;
        }
        try {
            ChunkBuildResult result = buildChunkResponse(event, payload);
            event.referToServer(referralSource.host, referralSource.port, result.encodedPayload());
        } catch (IOException | GeneralSecurityException | IllegalStateException exception) {
            sendOperationalError(event, payload.sessionId(), payload.requestId(), payload.fileId(), "DIAGNOSTICS_COLLECT_CHUNK_FAILED", safeMessage(exception));
        }
    }

    private void handleChunkResponse(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        DiagnosticsCollectChunkResponsePayload payload = referral.decodePayload(
            secureReferralService.gson(),
            DiagnosticsCollectChunkResponsePayload.class
        );
        DiagnosticsCollectSession session = sessionStore.loadSession(payload.sessionId()).orElse(null);
        if (session == null || session.currentRequestId() == null || !session.currentRequestId().equals(payload.requestId())) {
            return;
        }

        DiagnosticsCollectServerProgress server = findServer(session, payload.sourceServerId());
        DiagnosticsCollectFileProgress file = server == null ? null : findFile(server, payload.fileId());
        if (server == null || file == null) {
            return;
        }
        if (payload.startLineInclusive() != file.nextLineInclusive()) {
            failSession(session.sessionId(), "The diagnostics collect chunk sequence did not match the expected checkpoint for " + payload.fileId() + ".");
            finalizePendingReturn(event.getUuid(), session, "Diagnostics collect failed because a chunk arrived out of order.", true);
            return;
        }

        try {
            List<String> lines = splitAndValidateChunk(payload.ndjsonBlock());
            if (lines.size() != payload.chunkEventCount()) {
                throw new IllegalStateException("The diagnostics collect chunk event count did not match the transmitted block.");
            }
            String actualHash = sha256OfString(payload.ndjsonBlock());
            if (!actualHash.equals(payload.chunkSha256())) {
                throw new IllegalStateException("The diagnostics collect chunk hash did not match the transmitted block.");
            }

            Path rawPart = sessionStore.rawPartFile(session.sessionId(), server.remoteServerId(), file.fileId());
            Files.writeString(rawPart, payload.ndjsonBlock(), StandardCharsets.UTF_8, StandardOpenOption.CREATE, StandardOpenOption.APPEND);

            DiagnosticsCollectFileProgress updatedFile = file.withNextLineInclusive(payload.endLineExclusive(), payload.chunkIndex());
            DiagnosticsCollectServerProgress updatedServer = replaceFile(server, updatedFile);
            boolean serverCompleted = updatedServer.files().stream().allMatch(DiagnosticsCollectFileProgress::completed);
            updatedServer = new DiagnosticsCollectServerProgress(
                updatedServer.remoteServerId(),
                updatedServer.remoteConnectionAddress(),
                serverCompleted ? DiagnosticsCollectStatus.COMPLETED : DiagnosticsCollectStatus.RUNNING,
                updatedServer.estimatedBytes(),
                updatedServer.downloadedBytes() + payload.chunkRawBytes(),
                updatedServer.estimatedEvents(),
                updatedServer.downloadedEvents() + payload.chunkEventCount(),
                serverCompleted ? null : updatedFile.fileId(),
                null,
                updatedServer.files()
            );
            DiagnosticsCollectSession updated = replaceServer(session, updatedServer);
            updated = rewriteSessionRequest(updated, server.remoteServerId(), null, null);
            updated = recomputeSessionTotals(updated);
            // Write safety order for imported raw files:
            // 1. validate the chunk and append only to .part
            // 2. persist the updated checkpoint/progress
            // 3. rename .part -> .jsonl only when that file's requested window is fully imported
            persistSession(updated);
            if (updatedFile.completed()) {
                sessionStore.finalizeRawFile(session.sessionId(), server.remoteServerId(), file.fileId());
            }
            try {
                continueRunning(event, updated);
            } catch (IOException | GeneralSecurityException | RuntimeException exception) {
                String error = "Diagnostics collect failed while continuing: " + safeMessage(exception);
                failSession(updated.sessionId(), error);
                finalizePendingReturn(event.getUuid(), updated, error, true);
            }
        } catch (IOException | IllegalStateException exception) {
            String error = "Diagnostics collect failed while importing " + payload.fileId() + ": " + safeMessage(exception);
            markFileFailed(session, server, file, error);
            finalizePendingReturn(event.getUuid(), session, error, true);
        }
    }

    private void handleOperationalError(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) {
        DiagnosticsCollectErrorPayload payload = referral.decodePayload(
            secureReferralService.gson(),
            DiagnosticsCollectErrorPayload.class
        );
        DiagnosticsCollectSession session = sessionStore.loadSession(payload.sessionId()).orElse(null);
        if (session == null || session.currentRequestId() == null || !session.currentRequestId().equals(payload.requestId())) {
            return;
        }
        failCurrentServerSession(session.sessionId(), payload.message());
        finalizePendingReturn(event.getUuid(), session, payload.message(), true);
    }

    private void sendOperationalError(
        @Nonnull PlayerSetupConnectEvent event,
        @Nonnull String sessionId,
        @Nonnull String requestId,
        @Nullable String fileId,
        @Nonnull String code,
        @Nonnull String message
    ) {
        HostAddress referralSource = event.getReferralSource();
        if (referralSource == null || referralSource.host == null) {
            return;
        }
        try {
            byte[] payload = secureReferralService.createPayload(
                event.getUuid(),
                event.getUsername(),
                DiagnosticsProtocol.COLLECT_ERROR,
                new DiagnosticsCollectErrorPayload(sessionId, requestId, fileId, code, message),
                REFERRAL_TTL
            );
            event.referToServer(referralSource.host, referralSource.port, payload);
        } catch (IOException | GeneralSecurityException ignored) {
        }
    }

    @Nonnull
    private ChunkBuildResult buildChunkResponse(
        @Nonnull PlayerSetupConnectEvent event,
        @Nonnull DiagnosticsCollectChunkRequestPayload payload
    ) throws IOException, GeneralSecurityException {
        SegmentWindowLines windowLines = loadWindowLines(payload.fileId(), payload.snapshotLineCount());
        validateChunkRequestAgainstSnapshot(payload, windowLines);
        int relativeStart = Math.toIntExact(payload.nextLineInclusive() - payload.windowStartLineInclusive());
        int relativeEnd = Math.toIntExact(payload.windowEndLineExclusive() - payload.windowStartLineInclusive());
        if (relativeStart < 0 || relativeStart > relativeEnd || relativeEnd > windowLines.lines().size()) {
            throw new IllegalStateException("The diagnostics collect chunk boundaries are outside the requested file window.");
        }

        StringBuilder ndjson = new StringBuilder();
        byte[] encoded = null;
        long endLineExclusive = payload.nextLineInclusive();
        long chunkRawBytes = 0L;
        for (int index = relativeStart; index < relativeEnd; index++) {
            int previousLength = ndjson.length();
            ndjson.append(windowLines.lines().get(index)).append('\n');
            String block = ndjson.toString();
            DiagnosticsCollectChunkResponsePayload candidate = new DiagnosticsCollectChunkResponsePayload(
                payload.sessionId(),
                payload.requestId(),
                localServerId,
                localConnectionAddress(),
                payload.fileId(),
                relativeStart,
                payload.nextLineInclusive(),
                payload.windowStartLineInclusive() + index + 1L,
                index - relativeStart + 1L,
                block.getBytes(StandardCharsets.UTF_8).length,
                sha256OfString(block),
                block,
                index + 1 < relativeEnd
            );
            byte[] candidateBytes = secureReferralService.createPayload(
                event.getUuid(),
                event.getUsername(),
                DiagnosticsProtocol.COLLECT_CHUNK_RESPONSE,
                candidate,
                REFERRAL_TTL
            );
            if (candidateBytes.length > TARGET_OPERATIONAL_ENCODED_BYTES && previousLength > 0) {
                ndjson.setLength(previousLength);
                break;
            }
            if (candidateBytes.length > TARGET_OPERATIONAL_ENCODED_BYTES) {
                throw new IllegalStateException("A single diagnostics event was too large to fit inside the collect referral budget.");
            }
            encoded = candidateBytes;
            endLineExclusive = payload.windowStartLineInclusive() + index + 1L;
            chunkRawBytes = block.getBytes(StandardCharsets.UTF_8).length;
        }
        if (encoded == null) {
            throw new IllegalStateException("No diagnostics events could fit inside the collect chunk.");
        }
        return new ChunkBuildResult(encoded);
    }

    @Nonnull
    private List<DiagnosticsCollectManifestEntry> buildManifestEntries(long windowStartEpochMs, long windowEndEpochMs) {
        List<DiagnosticsCollectManifestEntry> entries = new ArrayList<>();
        for (SegmentRef segment : listSegments()) {
            if (segment.metadata().endedAtEpochMs() < windowStartEpochMs || segment.metadata().startedAtEpochMs() > windowEndEpochMs) {
                continue;
            }
            scanManifestEntry(segment, windowStartEpochMs, windowEndEpochMs).ifPresent(entries::add);
        }
        entries.sort(Comparator
            .comparingLong(DiagnosticsCollectManifestEntry::startedAtEpochMs)
            .thenComparing(DiagnosticsCollectManifestEntry::fileId));
        return entries;
    }

    @Nonnull
    private Optional<DiagnosticsCollectManifestEntry> scanManifestEntry(
        @Nonnull SegmentRef segment,
        long windowStartEpochMs,
        long windowEndEpochMs
    ) {
        try {
            List<String> lines = Files.readAllLines(segment.file(), StandardCharsets.UTF_8);
            if (lines.isEmpty()) {
                return Optional.empty();
            }

            /*
             * v1 scans candidate segments line-by-line to compute exact window boundaries.
             * This is acceptable because capture already caps a segment at 1 MiB / 5000 lines.
             * If diagnostics volume grows later, this boundary scan is the first place to optimize.
             */
            long windowStartLineInclusive = -1L;
            long windowEndLineExclusive = -1L;
            long windowByteCount = 0L;
            long windowEventCount = 0L;
            long snapshotEndedAtEpochMs = 0L;
            for (int index = 0; index < lines.size(); index++) {
                DiagnosticsEvent event = GSON.fromJson(lines.get(index), DiagnosticsEvent.class);
                if (event == null) {
                    continue;
                }
                long eventTime = event.occurredAtEpochMs();
                int lineBytes = lines.get(index).getBytes(StandardCharsets.UTF_8).length + 1;
                if (eventTime >= windowStartEpochMs && eventTime <= windowEndEpochMs) {
                    if (windowStartLineInclusive < 0L) {
                        windowStartLineInclusive = index;
                    }
                    windowEndLineExclusive = index + 1L;
                    windowEventCount += 1L;
                    windowByteCount += lineBytes;
                    snapshotEndedAtEpochMs = eventTime;
                }
            }
            if (windowStartLineInclusive < 0L || windowEndLineExclusive < 0L || windowEventCount <= 0L) {
                return Optional.empty();
            }

            boolean closed = !isActiveSegment(segment.file());
            if (closed) {
                return Optional.of(new DiagnosticsCollectManifestEntry(
                    segment.metadata().fileId(),
                    segment.metadata().fileName(),
                    true,
                    segment.metadata().sha256(),
                    segment.metadata().lineCount(),
                    segment.metadata().byteSize(),
                    segment.metadata().startedAtEpochMs(),
                    segment.metadata().endedAtEpochMs(),
                    windowStartLineInclusive,
                    windowEndLineExclusive,
                    windowEventCount,
                    windowByteCount,
                    null,
                    null,
                    null,
                    null
                ));
            }

            int snapshotLineCount = Math.toIntExact(windowEndLineExclusive);
            return Optional.of(new DiagnosticsCollectManifestEntry(
                segment.metadata().fileId(),
                segment.metadata().fileName(),
                false,
                null,
                segment.metadata().lineCount(),
                segment.metadata().byteSize(),
                segment.metadata().startedAtEpochMs(),
                segment.metadata().endedAtEpochMs(),
                windowStartLineInclusive,
                windowEndLineExclusive,
                windowEventCount,
                windowByteCount,
                (long) snapshotLineCount,
                prefixByteSize(lines, snapshotLineCount),
                snapshotEndedAtEpochMs,
                sha256OfString(prefixAsString(lines, snapshotLineCount))
            ));
        } catch (IOException exception) {
            logger.atWarning().withCause(exception).log("Failed to scan Nexori diagnostics segment boundaries for collect.");
            return Optional.empty();
        }
    }

    private void validateChunkRequestAgainstSnapshot(
        @Nonnull DiagnosticsCollectChunkRequestPayload payload,
        @Nonnull SegmentWindowLines windowLines
    ) {
        if (payload.closed()) {
            if (payload.expectedFileSha256() == null || !payload.expectedFileSha256().equals(windowLines.fileSha256())) {
                throw new IllegalStateException("The requested diagnostics segment hash no longer matches the sealed file.");
            }
            return;
        }
        if (payload.snapshotLineCount() == null
            || payload.snapshotByteSize() == null
            || payload.snapshotEndedAtEpochMs() == null
            || payload.snapshotPrefixSha256() == null) {
            throw new IllegalStateException("The requested diagnostics active segment snapshot boundary was incomplete.");
        }
        if (!payload.snapshotLineCount().equals(windowLines.snapshotLineCount())
            || !payload.snapshotByteSize().equals(windowLines.snapshotByteSize())
            || !payload.snapshotEndedAtEpochMs().equals(windowLines.snapshotEndedAtEpochMs())
            || !payload.snapshotPrefixSha256().equals(windowLines.snapshotPrefixSha256())) {
            throw new IllegalStateException("The requested diagnostics active segment no longer matches the frozen collect snapshot boundary.");
        }
    }

    @Nonnull
    private SegmentWindowLines loadWindowLines(@Nonnull String fileId, @Nullable Long requestedSnapshotLineCount) throws IOException {
        SegmentRef segment = listSegments().stream()
            .filter(ref -> ref.metadata().fileId().equals(fileId))
            .findFirst()
            .orElseThrow(() -> new IllegalStateException("The diagnostics segment " + fileId + " no longer exists on the remote server."));
        List<String> lines = Files.readAllLines(segment.file(), StandardCharsets.UTF_8);
        boolean closed = !isActiveSegment(segment.file());
        int snapshotLineCount = requestedSnapshotLineCount == null ? lines.size() : Math.toIntExact(requestedSnapshotLineCount);
        if (snapshotLineCount < 0 || snapshotLineCount > lines.size()) {
            throw new IllegalStateException("The requested diagnostics snapshot boundary exceeds the current segment size.");
        }
        long snapshotEndedAtEpochMs = 0L;
        if (snapshotLineCount > 0) {
            DiagnosticsEvent boundary = GSON.fromJson(lines.get(snapshotLineCount - 1), DiagnosticsEvent.class);
            if (boundary != null) {
                snapshotEndedAtEpochMs = boundary.occurredAtEpochMs();
            }
        }
        return new SegmentWindowLines(
            segment.file(),
            lines,
            closed,
            segment.metadata().sha256(),
            snapshotLineCount,
            prefixByteSize(lines, snapshotLineCount),
            snapshotEndedAtEpochMs,
            sha256OfString(prefixAsString(lines, snapshotLineCount))
        );
    }

    private void markFileFailed(
        @Nonnull DiagnosticsCollectSession session,
        @Nonnull DiagnosticsCollectServerProgress server,
        @Nonnull DiagnosticsCollectFileProgress file,
        @Nonnull String error
    ) {
        DiagnosticsCollectFileProgress updatedFile = file.withError(error, file.retryCount() + 1);
        DiagnosticsCollectServerProgress updatedServer = replaceFile(server, updatedFile);
        updatedServer = new DiagnosticsCollectServerProgress(
            updatedServer.remoteServerId(),
            updatedServer.remoteConnectionAddress(),
            DiagnosticsCollectStatus.FAILED,
            updatedServer.estimatedBytes(),
            updatedServer.downloadedBytes(),
            updatedServer.estimatedEvents(),
            updatedServer.downloadedEvents(),
            updatedServer.currentFileId(),
            error,
            updatedServer.files()
        );
        DiagnosticsCollectSession updated = replaceServer(session, updatedServer);
        updated = rewriteSessionStatus(updated, DiagnosticsCollectStatus.FAILED, error);
        persistSession(updated);
        sessionStore.clearActiveLock();
    }

    private void failSession(@Nonnull String sessionId, @Nonnull String error) {
        DiagnosticsCollectSession session = sessionStore.loadSession(sessionId).orElse(null);
        if (session == null) {
            sessionStore.clearActiveLock();
            return;
        }
        DiagnosticsCollectSession failed = rewriteSessionStatus(session, DiagnosticsCollectStatus.FAILED, error);
        persistSession(failed);
        sessionStore.clearActiveLock();
    }

    private void failCurrentServerSession(@Nonnull String sessionId, @Nonnull String error) {
        DiagnosticsCollectSession session = sessionStore.loadSession(sessionId).orElse(null);
        if (session == null) {
            sessionStore.clearActiveLock();
            return;
        }
        DiagnosticsCollectSession updated = session;
        if (session.currentServerId() != null) {
            DiagnosticsCollectServerProgress currentServer = findServer(session, session.currentServerId());
            if (currentServer != null) {
                DiagnosticsCollectServerProgress failedServer = new DiagnosticsCollectServerProgress(
                    currentServer.remoteServerId(),
                    currentServer.remoteConnectionAddress(),
                    DiagnosticsCollectStatus.FAILED,
                    currentServer.estimatedBytes(),
                    currentServer.downloadedBytes(),
                    currentServer.estimatedEvents(),
                    currentServer.downloadedEvents(),
                    currentServer.currentFileId(),
                    error,
                    currentServer.files()
                );
                updated = replaceServer(session, failedServer);
            }
        }
        DiagnosticsCollectSession failed = rewriteSessionStatus(updated, DiagnosticsCollectStatus.FAILED, error);
        persistSession(failed);
        sessionStore.clearActiveLock();
    }

    @Nullable
    private DiagnosticsCollectSession completeSession(@Nonnull String sessionId) {
        DiagnosticsCollectSession session = sessionStore.loadSession(sessionId).orElse(null);
        if (session == null) {
            sessionStore.clearActiveLock();
            return null;
        }
        List<DiagnosticsCollectServerProgress> completedServers = session.servers().stream()
            .map(server -> new DiagnosticsCollectServerProgress(
                server.remoteServerId(),
                server.remoteConnectionAddress(),
                DiagnosticsCollectStatus.COMPLETED,
                server.estimatedBytes(),
                server.downloadedBytes(),
                server.estimatedEvents(),
                server.downloadedEvents(),
                null,
                null,
                server.files()
            ))
            .toList();
        DiagnosticsCollectSession completed = new DiagnosticsCollectSession(
            session.schemaVersion(),
            session.sessionId(),
            DiagnosticsCollectStatus.COMPLETED,
            session.createdAtEpochMs(),
            System.currentTimeMillis(),
            session.windowPresetId(),
            session.windowStartEpochMs(),
            session.windowEndEpochMs(),
            session.originPlayerUuid(),
            session.originSnapshot(),
            completedServers,
            null,
            null,
            session.estimatedTotalBytes(),
            session.downloadedBytes(),
            session.estimatedTotalEvents(),
            session.downloadedEvents(),
            null
        );
        persistSession(completed);
        sessionStore.clearActiveLock();
        return completed;
    }

    @Nonnull
    private DiagnosticsCollectSession rewriteSessionStatus(
        @Nonnull DiagnosticsCollectSession session,
        @Nonnull DiagnosticsCollectStatus status,
        @Nullable String lastError
    ) {
        return new DiagnosticsCollectSession(
            session.schemaVersion(),
            session.sessionId(),
            status,
            session.createdAtEpochMs(),
            System.currentTimeMillis(),
            session.windowPresetId(),
            session.windowStartEpochMs(),
            session.windowEndEpochMs(),
            session.originPlayerUuid(),
            session.originSnapshot(),
            session.servers(),
            session.currentServerId(),
            null,
            session.estimatedTotalBytes(),
            session.downloadedBytes(),
            session.estimatedTotalEvents(),
            session.downloadedEvents(),
            lastError
        );
    }

    @Nonnull
    private DiagnosticsCollectSession rewriteSessionRequest(
        @Nonnull DiagnosticsCollectSession session,
        @Nullable String currentServerId,
        @Nullable String currentRequestId,
        @Nullable String lastError
    ) {
        return new DiagnosticsCollectSession(
            session.schemaVersion(),
            session.sessionId(),
            session.status(),
            session.createdAtEpochMs(),
            System.currentTimeMillis(),
            session.windowPresetId(),
            session.windowStartEpochMs(),
            session.windowEndEpochMs(),
            session.originPlayerUuid(),
            session.originSnapshot(),
            session.servers(),
            currentServerId,
            currentRequestId,
            session.estimatedTotalBytes(),
            session.downloadedBytes(),
            session.estimatedTotalEvents(),
            session.downloadedEvents(),
            lastError
        );
    }

    @Nonnull
    private DiagnosticsCollectSession recomputeSessionTotals(@Nonnull DiagnosticsCollectSession session) {
        long estimatedBytes = 0L;
        long downloadedBytes = 0L;
        long estimatedEvents = 0L;
        long downloadedEvents = 0L;
        for (DiagnosticsCollectServerProgress server : session.servers()) {
            estimatedBytes += server.estimatedBytes();
            downloadedBytes += server.downloadedBytes();
            estimatedEvents += server.estimatedEvents();
            downloadedEvents += server.downloadedEvents();
        }
        return new DiagnosticsCollectSession(
            session.schemaVersion(),
            session.sessionId(),
            session.status(),
            session.createdAtEpochMs(),
            System.currentTimeMillis(),
            session.windowPresetId(),
            session.windowStartEpochMs(),
            session.windowEndEpochMs(),
            session.originPlayerUuid(),
            session.originSnapshot(),
            session.servers(),
            session.currentServerId(),
            session.currentRequestId(),
            estimatedBytes,
            downloadedBytes,
            estimatedEvents,
            downloadedEvents,
            session.lastError()
        );
    }

    @Nonnull
    private DiagnosticsCollectSession replaceServer(
        @Nonnull DiagnosticsCollectSession session,
        @Nonnull DiagnosticsCollectServerProgress updatedServer
    ) {
        List<DiagnosticsCollectServerProgress> servers = new ArrayList<>(session.servers());
        for (int index = 0; index < servers.size(); index++) {
            if (servers.get(index).remoteServerId().equals(updatedServer.remoteServerId())) {
                servers.set(index, updatedServer);
                DiagnosticsCollectSession updated = new DiagnosticsCollectSession(
                    session.schemaVersion(),
                    session.sessionId(),
                    session.status(),
                    session.createdAtEpochMs(),
                    System.currentTimeMillis(),
                    session.windowPresetId(),
                    session.windowStartEpochMs(),
                    session.windowEndEpochMs(),
                    session.originPlayerUuid(),
                    session.originSnapshot(),
                    List.copyOf(servers),
                    session.currentServerId(),
                    session.currentRequestId(),
                    session.estimatedTotalBytes(),
                    session.downloadedBytes(),
                    session.estimatedTotalEvents(),
                    session.downloadedEvents(),
                    session.lastError()
                );
                sessionStore.saveServerProgress(updated.sessionId(), updatedServer);
                return updated;
            }
        }
        throw new IllegalStateException("Could not find diagnostics collect server progress for " + updatedServer.remoteServerId() + ".");
    }

    @Nonnull
    private DiagnosticsCollectServerProgress replaceFile(
        @Nonnull DiagnosticsCollectServerProgress server,
        @Nonnull DiagnosticsCollectFileProgress updatedFile
    ) {
        List<DiagnosticsCollectFileProgress> files = new ArrayList<>(server.files());
        for (int index = 0; index < files.size(); index++) {
            if (files.get(index).fileId().equals(updatedFile.fileId())) {
                files.set(index, updatedFile);
                return new DiagnosticsCollectServerProgress(
                    server.remoteServerId(),
                    server.remoteConnectionAddress(),
                    server.status(),
                    server.estimatedBytes(),
                    server.downloadedBytes(),
                    server.estimatedEvents(),
                    server.downloadedEvents(),
                    updatedFile.fileId(),
                    server.lastError(),
                    List.copyOf(files)
                );
            }
        }
        throw new IllegalStateException("Could not find diagnostics collect file progress for " + updatedFile.fileId() + ".");
    }

    @Nonnull
    private DiagnosticsCollectServerProgress progressFromManifest(
        @Nonnull DiagnosticsCollectServerProgress server,
        @Nonnull DiagnosticsCollectManifest manifest
    ) {
        List<DiagnosticsCollectFileProgress> files = manifest.entries().stream()
            .map(entry -> new DiagnosticsCollectFileProgress(
                entry.fileId(),
                entry.fileName(),
                entry.closed(),
                entry.fileSha256(),
                entry.snapshotLineCount(),
                entry.snapshotByteSize(),
                entry.snapshotEndedAtEpochMs(),
                entry.snapshotPrefixSha256(),
                entry.startedAtEpochMs(),
                entry.endedAtEpochMs(),
                entry.windowStartLineInclusive(),
                entry.windowEndLineExclusive(),
                entry.windowEventCount(),
                entry.windowByteCount(),
                entry.windowStartLineInclusive(),
                0,
                entry.windowStartLineInclusive() >= entry.windowEndLineExclusive(),
                0,
                null
            ))
            .toList();
        long estimatedBytes = manifest.entries().stream().mapToLong(DiagnosticsCollectManifestEntry::windowByteCount).sum();
        long estimatedEvents = manifest.entries().stream().mapToLong(DiagnosticsCollectManifestEntry::windowEventCount).sum();
        return new DiagnosticsCollectServerProgress(
            server.remoteServerId(),
            manifest.remoteConnectionAddress(),
            DiagnosticsCollectStatus.READY,
            estimatedBytes,
            server.downloadedBytes(),
            estimatedEvents,
            server.downloadedEvents(),
            files.isEmpty() ? null : files.getFirst().fileId(),
            null,
            files
        );
    }

    private void persistSession(@Nonnull DiagnosticsCollectSession session) {
        sessionStore.saveSession(session);
        for (DiagnosticsCollectServerProgress server : session.servers()) {
            sessionStore.saveServerProgress(session.sessionId(), server);
        }
    }

    private void finalizePendingReturn(
        @Nonnull UUID playerUuid,
        @Nonnull DiagnosticsCollectSession session,
        @Nonnull String message,
        boolean reopenUi
    ) {
        pendingReturns.put(playerUuid, new PendingCollectReturn(
            session.sessionId(),
            session.originSnapshot(),
            message,
            reopenUi
        ));
    }

    private boolean isStale(@Nonnull DiagnosticsCollectActiveLock lock) {
        if (lock.status() != DiagnosticsCollectStatus.PLANNING && lock.status() != DiagnosticsCollectStatus.RUNNING) {
            return false;
        }
        return lock.updatedAtEpochMs() < System.currentTimeMillis() - STALE_LOCK_MILLIS;
    }

    @Nonnull
    private List<CollectServerRow> buildRemoteRows(@Nullable DiagnosticsCollectSession session) {
        LinkedHashMap<String, CollectServerRow> rows = new LinkedHashMap<>();
        for (RemotePeer remote : trustedRemotePeers()) {
            rows.put(remote.serverId(), new CollectServerRow(remote.serverId(), remote.connectionAddress(), DiagnosticsCollectStatus.PENDING, 0L, 0L, 0L, 0L, null));
        }
        if (session != null) {
            for (DiagnosticsCollectServerProgress server : session.servers()) {
                rows.put(server.remoteServerId(), new CollectServerRow(
                    server.remoteServerId(),
                    server.remoteConnectionAddress(),
                    server.status(),
                    server.estimatedBytes(),
                    server.downloadedBytes(),
                    server.estimatedEvents(),
                    server.downloadedEvents(),
                    server.lastError()
                ));
            }
        }
        return List.copyOf(rows.values());
    }

    @Nonnull
    private List<RemotePeer> trustedRemotePeers() {
        List<RemotePeer> peers = new ArrayList<>();
        for (BundleMember member : trustBundleStore.getCurrentBundle().members()) {
            if (localServerId.equals(member.serverId())
                || member.connectionAddress() == null
                || member.connectionAddress().isBlank()) {
                continue;
            }
            String[] pieces = member.connectionAddress().split(":", 2);
            if (pieces.length != 2) {
                continue;
            }
            try {
                peers.add(new RemotePeer(member.serverId(), member.connectionAddress(), pieces[0], Integer.parseInt(pieces[1])));
            } catch (NumberFormatException ignored) {
            }
        }
        peers.sort(Comparator.comparing(RemotePeer::connectionAddress));
        return peers;
    }

    @Nonnull
    private Optional<RemotePeer> findRemotePeer(@Nonnull String remoteServerId) {
        return trustedRemotePeers().stream()
            .filter(peer -> peer.serverId().equals(remoteServerId))
            .findFirst();
    }

    @Nullable
    private DiagnosticsCollectServerProgress findServer(@Nonnull DiagnosticsCollectSession session, @Nonnull String remoteServerId) {
        for (DiagnosticsCollectServerProgress server : session.servers()) {
            if (server.remoteServerId().equals(remoteServerId)) {
                return server;
            }
        }
        return null;
    }

    @Nullable
    private DiagnosticsCollectFileProgress findFile(@Nonnull DiagnosticsCollectServerProgress server, @Nonnull String fileId) {
        for (DiagnosticsCollectFileProgress file : server.files()) {
            if (file.fileId().equals(fileId)) {
                return file;
            }
        }
        return null;
    }

    private boolean isActiveSegment(@Nonnull Path file) throws IOException {
        List<SegmentRef> segments = listSegments();
        return !segments.isEmpty() && segments.getLast().file().equals(file);
    }

    @Nonnull
    private List<SegmentRef> listSegments() {
        List<SegmentRef> refs = new ArrayList<>();
        try (DirectoryStream<Path> stream = Files.newDirectoryStream(journalDir, "*.meta.json")) {
            for (Path sidecar : stream) {
                DiagnosticsSegmentMetadata metadata = GSON.fromJson(Files.readString(sidecar, StandardCharsets.UTF_8), DiagnosticsSegmentMetadata.class);
                if (metadata == null) {
                    continue;
                }
                Path file = journalDir.resolve(metadata.fileName());
                if (Files.exists(file)) {
                    refs.add(new SegmentRef(file, metadata));
                }
            }
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to list local Nexori diagnostics segments.", exception);
        }
        refs.sort(Comparator
            .comparingLong((SegmentRef ref) -> ref.metadata().startedAtEpochMs())
            .thenComparing(ref -> ref.file().getFileName().toString()));
        return refs;
    }

    private long prefixByteSize(@Nonnull List<String> lines, int lineCount) {
        long total = 0L;
        int bounded = Math.max(0, Math.min(lineCount, lines.size()));
        for (int index = 0; index < bounded; index++) {
            total += lines.get(index).getBytes(StandardCharsets.UTF_8).length + 1L;
        }
        return total;
    }

    @Nonnull
    private String prefixAsString(@Nonnull List<String> lines, int lineCount) {
        StringBuilder out = new StringBuilder();
        int bounded = Math.max(0, Math.min(lineCount, lines.size()));
        for (int index = 0; index < bounded; index++) {
            out.append(lines.get(index)).append('\n');
        }
        return out.toString();
    }

    @Nonnull
    private List<String> splitAndValidateChunk(@Nonnull String ndjsonBlock) {
        List<String> lines = new ArrayList<>();
        if (ndjsonBlock.isBlank()) {
            return lines;
        }
        for (String line : ndjsonBlock.split("\n")) {
            if (line == null || line.isBlank()) {
                continue;
            }
            DiagnosticsEvent event = GSON.fromJson(line, DiagnosticsEvent.class);
            if (event == null || event.eventId().isBlank()) {
                throw new IllegalStateException("The diagnostics collect chunk contained an invalid event line.");
            }
            lines.add(line);
        }
        return lines;
    }

    @Nonnull
    private String sha256OfString(@Nonnull String raw) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] hash = digest.digest(raw.getBytes(StandardCharsets.UTF_8));
            StringBuilder out = new StringBuilder("sha256:");
            for (byte value : hash) {
                out.append(String.format("%02x", value));
            }
            return out.toString();
        } catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available.", exception);
        }
    }

    @Nonnull
    private String localConnectionAddress() {
        return trustBundleStore.getCurrentBundle().members().stream()
            .filter(member -> localServerId.equals(member.serverId()))
            .map(BundleMember::connectionAddress)
            .filter(value -> value != null && !value.isBlank())
            .findFirst()
            .orElse("");
    }

    @Nonnull
    private String safeMessage(@Nonnull Throwable throwable) {
        return throwable.getMessage() == null || throwable.getMessage().isBlank()
            ? throwable.getClass().getSimpleName()
            : throwable.getMessage();
    }

    public record CollectUiView(
        @Nullable DiagnosticsCollectSession session,
        @Nullable DiagnosticsCollectActiveLock activeLock,
        boolean staleLock,
        @Nonnull List<CollectServerRow> servers
    ) {
    }

    public record CollectServerRow(
        @Nonnull String remoteServerId,
        @Nonnull String remoteConnectionAddress,
        @Nonnull DiagnosticsCollectStatus status,
        long estimatedBytes,
        long downloadedBytes,
        long estimatedEvents,
        long downloadedEvents,
        @Nullable String lastError
    ) {
    }

    private record RemotePeer(@Nonnull String serverId, @Nonnull String connectionAddress, @Nonnull String host, int port) {
    }

    private record PendingCollectReturn(
        @Nonnull String sessionId,
        @Nonnull DiagnosticsCollectOriginSnapshot originSnapshot,
        @Nonnull String message,
        boolean reopenUi
    ) {
    }

    private record SegmentRef(@Nonnull Path file, @Nonnull DiagnosticsSegmentMetadata metadata) {
    }

    private record SegmentWindowLines(
        @Nonnull Path file,
        @Nonnull List<String> lines,
        boolean closed,
        @Nonnull String fileSha256,
        long snapshotLineCount,
        long snapshotByteSize,
        long snapshotEndedAtEpochMs,
        @Nonnull String snapshotPrefixSha256
    ) {
    }

    private record ChunkBuildResult(@Nonnull byte[] encodedPayload) {
    }

    private final class ManifestRequestHandler implements SecureReferralHandler {
        @Nonnull @Override public String payloadType() { return DiagnosticsProtocol.COLLECT_MANIFEST_REQUEST; }
        @Override public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) { handleManifestRequest(event, referral); }
    }

    private final class ManifestResponseHandler implements SecureReferralHandler {
        @Nonnull @Override public String payloadType() { return DiagnosticsProtocol.COLLECT_MANIFEST_RESPONSE; }
        @Override public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) { handleManifestResponse(event, referral); }
    }

    private final class ChunkRequestHandler implements SecureReferralHandler {
        @Nonnull @Override public String payloadType() { return DiagnosticsProtocol.COLLECT_CHUNK_REQUEST; }
        @Override public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) { handleChunkRequest(event, referral); }
    }

    private final class ChunkResponseHandler implements SecureReferralHandler {
        @Nonnull @Override public String payloadType() { return DiagnosticsProtocol.COLLECT_CHUNK_RESPONSE; }
        @Override public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) { handleChunkResponse(event, referral); }
    }

    private final class ErrorHandler implements SecureReferralHandler {
        @Nonnull @Override public String payloadType() { return DiagnosticsProtocol.COLLECT_ERROR; }
        @Override public void handle(@Nonnull PlayerSetupConnectEvent event, @Nonnull VerifiedSecureReferral referral) { handleOperationalError(event, referral); }
    }
}
