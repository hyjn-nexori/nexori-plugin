package io.github.hyjn.nexori.plugin;

import io.github.hyjn.nexori.plugin.api.minigame.NexoriMinigameApi;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkContinuationDecision;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriPlayerAfkChangedEvent;
import io.github.hyjn.nexori.plugin.accessgate.NexoriAccessGateService;
import io.github.hyjn.nexori.plugin.accessgate.NexoriAccessGateStore;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapState;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapCoordinator;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapPersistenceMigrationService;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapRunStore;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapStateStore;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundleStore;
import io.github.hyjn.nexori.plugin.assets.PluginAssetPackRegistrar;
import io.github.hyjn.nexori.plugin.backend.BackendAssignmentStore;
import io.github.hyjn.nexori.plugin.backend.BackendMatchmakingConfig;
import io.github.hyjn.nexori.plugin.backend.BackendAfkContinuationCheckService;
import io.github.hyjn.nexori.plugin.backend.BackendAfkContinuationCheckTickSystem;
import io.github.hyjn.nexori.plugin.backend.BackendMatchmakingConfigStore;
import io.github.hyjn.nexori.plugin.backend.BackendMatchAdmissionStateReportingService;
import io.github.hyjn.nexori.plugin.backend.BackendMatchAdmissionStateReportingTickSystem;
import io.github.hyjn.nexori.plugin.backend.BackendResultReportingService;
import io.github.hyjn.nexori.plugin.backend.BackendResultReportingTickSystem;
import io.github.hyjn.nexori.plugin.backend.BackendResultStore;
import io.github.hyjn.nexori.plugin.backend.BackendSyncService;
import io.github.hyjn.nexori.plugin.backend.BackendSyncTickSystem;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingService;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingStore;
import io.github.hyjn.nexori.plugin.binding.PortalBindingSyncService;
import io.github.hyjn.nexori.plugin.catalogsync.NetworkCatalogSyncService;
import io.github.hyjn.nexori.plugin.command.NexoriCommand;
import io.github.hyjn.nexori.plugin.command.NexoriArenaListCommand;
import io.github.hyjn.nexori.plugin.command.NexoriArenaUpsertCommand;
import io.github.hyjn.nexori.plugin.command.NexoriBackupsCommand;
import io.github.hyjn.nexori.plugin.command.NexoriBackupLimitCommand;
import io.github.hyjn.nexori.plugin.command.NexoriDiscoverCommand;
import io.github.hyjn.nexori.plugin.command.NexoriDiscoveredTargetsCommand;
import io.github.hyjn.nexori.plugin.command.NexoriInstanceListCommand;
import io.github.hyjn.nexori.plugin.command.NexoriMatchEndCommand;
import io.github.hyjn.nexori.plugin.command.NexoriMatchSessionStatusCommand;
import io.github.hyjn.nexori.plugin.command.NexoriMatchStatusCommand;
import io.github.hyjn.nexori.plugin.command.NexoriPortalBindCommand;
import io.github.hyjn.nexori.plugin.command.NexoriPortalGiveCommand;
import io.github.hyjn.nexori.plugin.command.NexoriPortalLocalBindCommand;
import io.github.hyjn.nexori.plugin.command.NexoriPortalListCommand;
import io.github.hyjn.nexori.plugin.command.NexoriPortalQueueBindCommand;
import io.github.hyjn.nexori.plugin.command.NexoriPortalShowCommand;
import io.github.hyjn.nexori.plugin.command.NexoriPortalUnbindCommand;
import io.github.hyjn.nexori.plugin.command.NexoriRecoverCommand;
import io.github.hyjn.nexori.plugin.command.NexoriRecoveryModeCommand;
import io.github.hyjn.nexori.plugin.command.NexoriRecoveryPageCommand;
import io.github.hyjn.nexori.plugin.command.NexoriQueueListCommand;
import io.github.hyjn.nexori.plugin.command.NexoriQueueLeaveCommand;
import io.github.hyjn.nexori.plugin.command.NexoriQueueStatusCommand;
import io.github.hyjn.nexori.plugin.command.NexoriQueueUpsertCommand;
import io.github.hyjn.nexori.plugin.command.NexoriStartCommand;
import io.github.hyjn.nexori.plugin.command.NexoriTargetAddCommand;
import io.github.hyjn.nexori.plugin.command.NexoriTargetHelpCommand;
import io.github.hyjn.nexori.plugin.command.NexoriTargetListCommand;
import io.github.hyjn.nexori.plugin.command.NexoriTargetRemoveCommand;
import io.github.hyjn.nexori.plugin.command.NexoriTargetShowCommand;
import io.github.hyjn.nexori.plugin.command.NexoriTravelCommand;
import io.github.hyjn.nexori.plugin.command.NexoriWorldLabelCleanupCommand;
import io.github.hyjn.nexori.plugin.diagnostics.DiagnosticsService;
import io.github.hyjn.nexori.plugin.diagnostics.collect.DiagnosticsCollectService;
import io.github.hyjn.nexori.plugin.diagnostics.reporting.DiagnosticsOwnerReportService;
import io.github.hyjn.nexori.plugin.discovery.DestinationTargetDiscoveryService;
import io.github.hyjn.nexori.plugin.discovery.DestinationTargetDiscoverySyncService;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetCacheService;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetCacheStore;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.identity.ServerIdentityManager;
import io.github.hyjn.nexori.plugin.inventory.InventorySnapshotService;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferBackupStore;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferPolicyStore;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferReceiptStore;
import io.github.hyjn.nexori.plugin.inventory.InventoryTransferService;
import io.github.hyjn.nexori.plugin.inventory.PlayerSaveRepository;
import io.github.hyjn.nexori.plugin.hud.NexoriStatusHudService;
import io.github.hyjn.nexori.plugin.hud.NexoriStatusHudTickSystem;
import io.github.hyjn.nexori.plugin.minigame.ArenaService;
import io.github.hyjn.nexori.plugin.minigame.ArenaStore;
import io.github.hyjn.nexori.plugin.minigame.AfkActivityService;
import io.github.hyjn.nexori.plugin.minigame.AfkInventoryPacketActivityAdapter;
import io.github.hyjn.nexori.plugin.minigame.AfkPlayerInputActivitySystem;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchService;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchTickSystem;
import io.github.hyjn.nexori.plugin.minigame.ArenaMatchWindowTickSystem;
import io.github.hyjn.nexori.plugin.minigame.InstanceSpawnSlotService;
import io.github.hyjn.nexori.plugin.minigame.InstanceSpawnSlotStore;
import io.github.hyjn.nexori.plugin.minigame.MatchSessionService;
import io.github.hyjn.nexori.plugin.minigame.MatchSessionStore;
import io.github.hyjn.nexori.plugin.minigame.NexoriAssignedSpawnProvider;
import io.github.hyjn.nexori.plugin.minigame.NexoriAfkActivityDispatcher;
import io.github.hyjn.nexori.plugin.minigame.NexoriMatchLifecycleDispatcher;
import io.github.hyjn.nexori.plugin.minigame.NexoriMinigameApiBridge;
import io.github.hyjn.nexori.plugin.minigame.QueueCoordinatorService;
import io.github.hyjn.nexori.plugin.minigame.QueueCoordinatorTickSystem;
import io.github.hyjn.nexori.plugin.minigame.QueueService;
import io.github.hyjn.nexori.plugin.minigame.QueueStore;
import io.github.hyjn.nexori.plugin.minigame.spectator.SpectatorPacketGuard;
import io.github.hyjn.nexori.plugin.minigame.spectator.SpectatorPickupSpatialFilterSystem;
import io.github.hyjn.nexori.plugin.minigame.spectator.SpectatorPickupSpatialGuard;
import io.github.hyjn.nexori.plugin.minigame.spectator.SpectatorPickupSpatialRestoreSystem;
import io.github.hyjn.nexori.plugin.minigame.spectator.SpectatorRuntimeService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeerService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeerMigrationService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeerMigrationStore;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeerStore;
import io.github.hyjn.nexori.plugin.peers.LocalConnectionAddressService;
import io.github.hyjn.nexori.plugin.policy.ServerPolicyCacheService;
import io.github.hyjn.nexori.plugin.policy.ServerPolicyCacheStore;
import io.github.hyjn.nexori.plugin.policy.ServerRuleGroupService;
import io.github.hyjn.nexori.plugin.policy.ServerRuleGroupStore;
import io.github.hyjn.nexori.plugin.policy.ServerPolicySyncService;
import io.github.hyjn.nexori.plugin.portal.NexoriPortalBreakSystem;
import io.github.hyjn.nexori.plugin.portal.NexoriPortalInteractionService;
import io.github.hyjn.nexori.plugin.portal.NexoriPortalPlaceSystem;
import io.github.hyjn.nexori.plugin.portal.PortalWorldLabelSource;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceService;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceStore;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefaults;
import io.github.hyjn.nexori.plugin.target.DestinationTargetService;
import io.github.hyjn.nexori.plugin.target.DestinationTargetStore;
import io.github.hyjn.nexori.plugin.travel.SecureTravelService;
import io.github.hyjn.nexori.plugin.ui.NexoriMenuCommand;
import io.github.hyjn.nexori.plugin.ui.PortalSetupDraftService;
import io.github.hyjn.nexori.plugin.ui.TargetSetupDraftService;
import io.github.hyjn.nexori.plugin.ui.menu.NexoriMenuV2Page;
import io.github.hyjn.nexori.plugin.ui.menu.state.NexoriMenuV2State;
import io.github.hyjn.nexori.plugin.worldlabel.WorldLabelService;
import io.github.hyjn.nexori.plugin.worldlabel.WorldLabelTickSystem;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.AbstractCommand;
import com.hypixel.hytale.server.core.command.system.CommandManager;
import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerDisconnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupDisconnectEvent;
import com.hypixel.hytale.server.core.modules.entity.EntityModule;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;
import com.hypixel.hytale.server.core.universe.world.spawn.ISpawnProvider;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;
import java.util.Iterator;
import java.util.Map;

/**
 * Main Nexori plugin entrypoint.
 * This class wires persistent stores, runtime services, commands, events, systems, and public integration surfaces.
 */
public class NexoriPlugin extends JavaPlugin {

    private ServerIdentityManager identityManager;
    private BootstrapStateStore bootstrapStateStore;
    private ConfiguredPeerService configuredPeerService;
    private BootstrapCoordinator bootstrapCoordinator;
    private TrustBundleStore trustBundleStore;
    private LocalConnectionAddressService localConnectionAddressService;
    private ConfiguredPeerMigrationService configuredPeerMigrationService;
    private DestinationTargetService destinationTargetService;
    private DiscoveredDestinationTargetCacheService discoveredDestinationTargetCacheService;
    private TriggerBindingService triggerBindingService;
    private PortalBindingSyncService portalBindingSyncService;
    private NetworkCatalogSyncService networkCatalogSyncService;
    private PortalInstanceService portalInstanceService;
    private SecureReferralService secureReferralService;
    private InventoryTransferService inventoryTransferService;
    private ServerPolicyCacheService serverPolicyCacheService;
    private ServerPolicySyncService serverPolicySyncService;
    private ServerRuleGroupService serverRuleGroupService;
    private SecureTravelService secureTravelService;
    private DestinationTargetDiscoveryService destinationTargetDiscoveryService;
    private DestinationTargetDiscoverySyncService destinationTargetDiscoverySyncService;
    private NexoriPortalInteractionService portalInteractionService;
    private PortalSetupDraftService portalSetupDraftService;
    private TargetSetupDraftService targetSetupDraftService;
    private ServerIdentity localIdentity;
    private DiagnosticsService diagnosticsService;
    private DiagnosticsCollectService diagnosticsCollectService;
    private DiagnosticsOwnerReportService diagnosticsOwnerReportService;
    private ArenaService arenaService;
    private InstanceSpawnSlotService instanceSpawnSlotService;
    private QueueService queueService;
    private QueueCoordinatorService queueCoordinatorService;
    private MatchSessionService matchSessionService;
    private ArenaMatchService arenaMatchService;
    private AfkActivityService afkActivityService;
    private SpectatorRuntimeService spectatorRuntimeService;
    private NexoriAfkActivityDispatcher afkActivityDispatcher;
    private NexoriMatchLifecycleDispatcher matchLifecycleDispatcher;
    private NexoriMinigameApi minigameApi;
    private NexoriStatusHudService nexoriStatusHudService;
    private WorldLabelService worldLabelService;
    private NexoriAccessGateService accessGateService;
    private BackendMatchmakingConfigStore backendMatchmakingConfigStore;
    private BackendMatchAdmissionStateReportingService backendMatchAdmissionStateReportingService;
    private BackendSyncService backendSyncService;
    private BackendResultReportingService backendResultReportingService;
    private BackendAfkContinuationCheckService backendAfkContinuationCheckService;

    public NexoriPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        try {
            // Prepare plugin storage and asset registration first so the rest of the setup can rely on them.
            Files.createDirectories(this.getDataDirectory());
            PluginAssetPackRegistrar.registerSelfAsAssetPack(this);

            // Core identity and diagnostics services come first because most later systems depend on them.
            this.identityManager = new ServerIdentityManager(this.getDataDirectory().resolve("identity"));
            this.localIdentity = this.identityManager.loadOrCreate();
            this.diagnosticsService = new DiagnosticsService(
                this.getLogger(),
                this.getDataDirectory(),
                this.localIdentity,
                () -> this.localConnectionAddressService == null ? "" : this.localConnectionAddressService.getConnectionAddressOrBlank(),
                this.getClass().getPackage().getImplementationVersion()
            );

            // Persistent configuration and runtime stores back every owner-facing workspace.
            this.bootstrapStateStore = new BootstrapStateStore(this.getDataDirectory().resolve("state"));
            BootstrapState bootstrapState = this.bootstrapStateStore.loadOrCreate();
            this.configuredPeerMigrationService = new ConfiguredPeerMigrationService(
                new ConfiguredPeerMigrationStore(this.getDataDirectory().resolve("state").resolve("configured-peer-migrations.json"))
            );
            this.configuredPeerService = new ConfiguredPeerService(
                new ConfiguredPeerStore(this.getDataDirectory().resolve("config").resolve("configured-peers.json")),
                this.configuredPeerMigrationService,
                this.diagnosticsService
            );
            this.localConnectionAddressService = new LocalConnectionAddressService(
                this.getDataDirectory().resolve("config").resolve("local-connection-address.txt")
            );
            this.destinationTargetService = new DestinationTargetService(
                new DestinationTargetStore(this.getDataDirectory().resolve("config").resolve("destination-targets.json")),
                this.diagnosticsService
            );
            this.arenaService = new ArenaService(
                new ArenaStore(this.getDataDirectory().resolve("config").resolve("arenas.json")),
                this.localConnectionAddressService
            );
            this.instanceSpawnSlotService = new InstanceSpawnSlotService(
                new InstanceSpawnSlotStore(this.getDataDirectory().resolve("config").resolve("instance-spawn-slots.json"))
            );
            this.getCodecRegistry(ISpawnProvider.CODEC).register(
                "NexoriAssigned",
                NexoriAssignedSpawnProvider.class,
                NexoriAssignedSpawnProvider.CODEC
            );
            this.queueService = new QueueService(
                new QueueStore(this.getDataDirectory().resolve("config").resolve("queues.json")),
                this.arenaService,
                this.getLogger()
            );
            this.matchSessionService = new MatchSessionService(
                new MatchSessionStore(this.getDataDirectory().resolve("state").resolve("match-sessions.json"))
            );
            this.discoveredDestinationTargetCacheService = new DiscoveredDestinationTargetCacheService(
                new DiscoveredDestinationTargetCacheStore(this.getDataDirectory().resolve("config").resolve("discovered-destination-targets.json"))
            );
            this.triggerBindingService = new TriggerBindingService(
                new TriggerBindingStore(this.getDataDirectory().resolve("config").resolve("trigger-bindings.json")),
                this.diagnosticsService
            );
            this.portalInstanceService = new PortalInstanceService(
                new PortalInstanceStore(this.getDataDirectory().resolve("config").resolve("portal-instances.json")),
                this.destinationTargetService,
                this.triggerBindingService,
                this.diagnosticsService
            );
            new DestinationTargetDefaults(this.getDataDirectory(), this.destinationTargetService).ensureDefaults(this.getLogger());
            this.trustBundleStore = new TrustBundleStore(this.getDataDirectory().resolve("state").resolve("trust-bundle.json"));
            this.trustBundleStore.loadOrCreate();
            BootstrapRunStore bootstrapRunStore = new BootstrapRunStore(this.getDataDirectory().resolve("state").resolve("bootstrap-run.json"));
            bootstrapRunStore.load();
            // Bootstrap and referral layers establish the secure server-to-server foundation for the tenant.
            this.bootstrapCoordinator = new BootstrapCoordinator(
                this.getLogger(),
                this.identityManager,
                this.localIdentity,
                this.bootstrapStateStore,
                this.configuredPeerService,
                this.configuredPeerMigrationService,
                this.localConnectionAddressService,
                bootstrapRunStore,
                this.trustBundleStore,
                new BootstrapPersistenceMigrationService(this.getLogger(), this.getDataDirectory()),
                this.diagnosticsService
            );
            this.secureReferralService = new SecureReferralService(
                this.getLogger(),
                this.identityManager,
                this.localIdentity,
                this.trustBundleStore,
                this.diagnosticsService
            );
            this.accessGateService = new NexoriAccessGateService(
                this.getLogger(),
                this.secureReferralService,
                new NexoriAccessGateStore(this.getDataDirectory().resolve("config").resolve("access-gate.json"))
            );
            this.diagnosticsCollectService = new DiagnosticsCollectService(
                this.getLogger(),
                this.getDataDirectory(),
                this.localIdentity.serverId(),
                this.trustBundleStore,
                this.secureReferralService
            );
            // Inventory, policy, and secure travel services build on top of the trust/referral layer.
            this.inventoryTransferService = new InventoryTransferService(
                this.getLogger(),
                new InventoryTransferBackupStore(this.getDataDirectory().resolve("state").resolve("inventory-transfer-backups.json")),
                new InventoryTransferReceiptStore(this.getDataDirectory().resolve("state").resolve("inventory-transfer-receipts.json")),
                new InventoryTransferPolicyStore(this.getDataDirectory().resolve("config").resolve("inventory-transfer-policy.json")),
                new PlayerSaveRepository(this.getLogger()),
                new InventorySnapshotService(),
                this.secureReferralService,
                this.diagnosticsService
            );
            this.serverPolicyCacheService = new ServerPolicyCacheService(
                new ServerPolicyCacheStore(this.getDataDirectory().resolve("config").resolve("discovered-server-policies.json"))
            );
            this.serverRuleGroupService = new ServerRuleGroupService(
                new ServerRuleGroupStore(this.getDataDirectory().resolve("config").resolve("server-rule-groups.json")),
                this.diagnosticsService
            );
            this.serverPolicySyncService = new ServerPolicySyncService(
                this.getLogger(),
                this.trustBundleStore,
                this.inventoryTransferService,
                this.serverPolicyCacheService,
                this.secureReferralService,
                this.diagnosticsService
            );
            this.secureTravelService = new SecureTravelService(
                    this.getLogger(),
                    this.getDataDirectory(),
                    this.localIdentity,
                    this.trustBundleStore,
                    this.destinationTargetService,
                    this.secureReferralService,
                    this.inventoryTransferService,
                    this.diagnosticsService
            );
            // Queue, lobby, and arena services form the runtime minigame orchestration layer.
            this.queueCoordinatorService = new QueueCoordinatorService(
                this.queueService,
                this.arenaService,
                this.matchSessionService,
                this.localConnectionAddressService,
                this.secureTravelService,
                this.getLogger()
            );
            // Discovery, HUD, world labels, and UI draft state support the owner and player-facing experience.
            this.destinationTargetDiscoveryService = new DestinationTargetDiscoveryService(
                this.getLogger(),
                this.trustBundleStore,
                this.destinationTargetService,
                this.portalInstanceService,
                this.discoveredDestinationTargetCacheService,
                this.secureReferralService,
                this.diagnosticsService
            );
            this.destinationTargetDiscoverySyncService = new DestinationTargetDiscoverySyncService(
                this.getLogger(),
                this.trustBundleStore,
                this.localConnectionAddressService,
                this.localIdentity,
                this.destinationTargetService,
                this.portalInstanceService,
                this.discoveredDestinationTargetCacheService,
                this.secureReferralService
            );
            this.portalBindingSyncService = new PortalBindingSyncService(
                this.getLogger(),
                this.trustBundleStore,
                this.localConnectionAddressService,
                this.triggerBindingService,
                this.secureReferralService
            );
            this.networkCatalogSyncService = new NetworkCatalogSyncService(
                this.getLogger(),
                this.localIdentity,
                this.trustBundleStore,
                this.secureReferralService,
                this.arenaService,
                this.queueService
            );
            this.spectatorRuntimeService = new SpectatorRuntimeService(this.getLogger());
            SpectatorPacketGuard.register(this.spectatorRuntimeService, this.getLogger());
            this.matchLifecycleDispatcher = new NexoriMatchLifecycleDispatcher(this.getLogger());
            this.afkActivityDispatcher = new NexoriAfkActivityDispatcher(this.getLogger());
            this.arenaMatchService = new ArenaMatchService(
                this.getLogger(),
                this.secureTravelService,
                this.matchSessionService,
                this.arenaService,
                this.instanceSpawnSlotService,
                this.spectatorRuntimeService,
                this.matchLifecycleDispatcher
            );
            io.github.hyjn.nexori.plugin.minigame.transfer.MinigameTransferService minigameTransferService =
                new io.github.hyjn.nexori.plugin.minigame.transfer.MinigameTransferService(
                    this.getLogger(),
                    this.instanceSpawnSlotService
                );
            this.arenaMatchService.setMinigameTransferService(minigameTransferService);
            this.afkActivityService = new AfkActivityService(
                this.getLogger(),
                this.arenaMatchService::findEffectiveAfkDetectionPolicy,
                transition -> {
                    this.afkActivityDispatcher.dispatchPlayerAfkChanged(new NexoriPlayerAfkChangedEvent(
                        transition.matchId(),
                        transition.queueId(),
                        transition.arenaId(),
                        transition.rulesEngineId(),
                        transition.playerUuid(),
                        transition.playerName(),
                        transition.afk(),
                        transition.changedAtEpochMs(),
                        transition.idleMs(),
                        transition.source()
                    ));
                    if (this.backendAfkContinuationCheckService != null) {
                        this.backendAfkContinuationCheckService.enqueue(transition);
                    }
                }
            );
            this.arenaMatchService.setMatchRuntimeClosedCallback(matchId -> {
                this.afkActivityService.removeMatch(matchId);
                if (this.backendAfkContinuationCheckService != null) {
                    this.backendAfkContinuationCheckService.removeMatch(matchId);
                }
            });
            this.backendMatchmakingConfigStore = new BackendMatchmakingConfigStore(
                this.getDataDirectory().resolve("config").resolve("backend-matchmaking.json")
            );
            BackendMatchmakingConfig backendMatchmakingConfig = this.backendMatchmakingConfigStore.loadOrCreate();
            BackendAssignmentStore backendAssignmentStore = new BackendAssignmentStore(
                this.getDataDirectory().resolve("state").resolve("backend-assignments.json")
            );
            BackendResultStore backendResultStore = new BackendResultStore(
                this.getDataDirectory().resolve("state").resolve("backend-results.json")
            );
            this.backendSyncService = new BackendSyncService(
                this.getLogger(),
                backendMatchmakingConfig,
                backendAssignmentStore,
                this.localIdentity,
                this.localConnectionAddressService,
                this.queueService,
                this.queueCoordinatorService,
                this.arenaService,
                this.arenaMatchService
            );
            this.backendMatchAdmissionStateReportingService = new BackendMatchAdmissionStateReportingService(
                this.getLogger(),
                backendMatchmakingConfig,
                this.localIdentity,
                this.localConnectionAddressService::getConnectionAddressOrBlank,
                this.arenaMatchService
            );
            this.arenaMatchService.setBackendMatchAdmissionStateReportingService(this.backendMatchAdmissionStateReportingService);
            this.backendResultReportingService = new BackendResultReportingService(
                this.getLogger(),
                backendMatchmakingConfig,
                backendResultStore,
                this.localIdentity
            );
            this.backendAfkContinuationCheckService = new BackendAfkContinuationCheckService(
                this.getLogger(),
                backendMatchmakingConfig,
                this.localIdentity
            );
            this.nexoriStatusHudService = new NexoriStatusHudService(
                this.queueCoordinatorService,
                this.arenaMatchService,
                this.afkActivityService,
                this.getLogger()
            );
            this.worldLabelService = new WorldLabelService(
                this.getLogger(),
                new PortalWorldLabelSource(this.portalInstanceService)
            );
            this.minigameApi = new NexoriMinigameApiBridge(
                this.arenaMatchService,
                this.afkActivityService,
                this.backendResultReportingService,
                this.backendAfkContinuationCheckService,
                this.matchLifecycleDispatcher,
                this.afkActivityDispatcher
            );
            this.portalSetupDraftService = new PortalSetupDraftService();
            this.targetSetupDraftService = new TargetSetupDraftService();
            this.portalInteractionService = new NexoriPortalInteractionService(
                this,
                this.getLogger(),
                this.portalInstanceService,
                this.triggerBindingService,
                this.queueCoordinatorService,
                this.secureTravelService,
                this.getBasePermission() + ".admin",
                this.diagnosticsService
            );
            this.diagnosticsOwnerReportService = new DiagnosticsOwnerReportService(
                this.getLogger(),
                this.getDataDirectory(),
                this.diagnosticsService,
                this.portalInstanceService,
                this.destinationTargetService
            );
            this.secureReferralService.registerHandler(this.secureTravelService);
            this.secureReferralService.registerHandler(this.destinationTargetDiscoveryService.requestHandler());
            this.secureReferralService.registerHandler(this.destinationTargetDiscoveryService.responseHandler());
            this.secureReferralService.registerHandler(this.destinationTargetDiscoverySyncService.applyRequestHandler());
            this.secureReferralService.registerHandler(this.destinationTargetDiscoverySyncService.responseHandler());
            this.secureReferralService.registerHandler(this.inventoryTransferService.queryHandler());
            this.secureReferralService.registerHandler(this.inventoryTransferService.replyHandler());
            this.secureReferralService.registerHandler(this.serverPolicySyncService.fetchRequestHandler());
            this.secureReferralService.registerHandler(this.serverPolicySyncService.applyRequestHandler());
            this.secureReferralService.registerHandler(this.serverPolicySyncService.responseHandler());
            this.secureReferralService.registerHandler(this.portalBindingSyncService.applyRequestHandler());
            this.secureReferralService.registerHandler(this.portalBindingSyncService.responseHandler());
            this.secureReferralService.registerHandler(this.networkCatalogSyncService.applyRequestHandler());
            this.secureReferralService.registerHandler(this.networkCatalogSyncService.resultHandler());
            this.secureReferralService.registerHandler(this.diagnosticsCollectService.manifestRequestHandler());
            this.secureReferralService.registerHandler(this.diagnosticsCollectService.manifestResponseHandler());
            this.secureReferralService.registerHandler(this.diagnosticsCollectService.chunkRequestHandler());
            this.secureReferralService.registerHandler(this.diagnosticsCollectService.chunkResponseHandler());
            this.secureReferralService.registerHandler(this.diagnosticsCollectService.errorHandler());
            this.portalInteractionService.registerPageSupplier();

            // Commands expose the owner/operator surface for setup, travel, recovery, and diagnostics.
            this.registerAdminCommand(new NexoriCommand(this));
            this.registerAdminCommand(new NexoriBackupLimitCommand(this, this.inventoryTransferService));
            this.registerAdminCommand(new NexoriRecoveryModeCommand(this, this.inventoryTransferService));
            this.registerAdminCommand(new NexoriBackupsCommand(this.inventoryTransferService));
            this.registerAdminCommand(new NexoriDiscoverCommand(this, this.destinationTargetDiscoveryService));
            this.registerAdminCommand(new NexoriDiscoveredTargetsCommand(this.discoveredDestinationTargetCacheService));
            this.registerAdminCommand(new NexoriPortalGiveCommand(this));
            this.registerAdminCommand(new NexoriPortalListCommand(this.portalInstanceService, this.triggerBindingService));
            this.registerAdminCommand(new NexoriPortalShowCommand(this.portalInstanceService, this.triggerBindingService));
            this.registerAdminCommand(new NexoriPortalBindCommand(this, this.portalInstanceService, this.triggerBindingService));
            this.registerAdminCommand(new NexoriPortalLocalBindCommand(
                this,
                this.portalInstanceService,
                this.destinationTargetService,
                this.triggerBindingService
            ));
            this.registerAdminCommand(new NexoriPortalQueueBindCommand(this, this.portalInstanceService, this.queueService, this.triggerBindingService));
            this.registerAdminCommand(new NexoriPortalUnbindCommand(this, this.triggerBindingService));
            this.registerAdminCommand(new NexoriArenaUpsertCommand(this, this.arenaService));
            this.registerAdminCommand(new NexoriArenaListCommand(this.arenaService));
            this.registerAdminCommand(new NexoriInstanceListCommand());
            this.registerAdminCommand(new NexoriQueueUpsertCommand(this, this.queueService));
            this.registerAdminCommand(new NexoriQueueListCommand(this.queueService));
            this.registerAdminCommand(new NexoriQueueStatusCommand(this.queueCoordinatorService));
            this.registerAdminCommand(new NexoriQueueLeaveCommand(this.queueCoordinatorService));
            this.registerAdminCommand(new NexoriMatchStatusCommand(this.arenaMatchService));
            this.registerAdminCommand(new NexoriMatchSessionStatusCommand(this.matchSessionService));
            this.registerAdminCommand(new NexoriMatchEndCommand(this, this.arenaMatchService));
            this.registerAdminCommand(new NexoriRecoverCommand(this.inventoryTransferService));
            this.registerAdminCommand(new NexoriRecoveryPageCommand(this.inventoryTransferService));
            this.registerAdminCommand(new NexoriTargetHelpCommand());
            this.registerAdminCommand(new NexoriTargetListCommand(this));
            this.registerAdminCommand(new NexoriTargetShowCommand(this));
            this.registerAdminCommand(new NexoriTargetAddCommand(this));
            this.registerAdminCommand(new NexoriTargetRemoveCommand(this));
            this.registerAdminCommand(new NexoriStartCommand(this.bootstrapCoordinator, this.getBasePermission() + ".admin"));
            this.registerAdminCommand(new NexoriTravelCommand(this.secureTravelService));
            this.registerAdminCommand(new NexoriWorldLabelCleanupCommand(this, this.worldLabelService));
            this.registerAdminCommand(new NexoriMenuCommand(this));
            this.removeBuiltInOpCommands();
            // Event hooks glue secure travel, queue runtime, HUD refresh, and menu resume flows into player lifecycle events.
            this.getEventRegistry().register(PlayerSetupConnectEvent.class, this.bootstrapCoordinator::handlePlayerSetupConnect);
            this.getEventRegistry().register(PlayerSetupConnectEvent.class, this.secureReferralService::handlePlayerSetupConnect);
            this.getEventRegistry().register(PlayerSetupConnectEvent.class, this.accessGateService::handlePlayerSetupConnect);
            this.getEventRegistry().register(PlayerSetupDisconnectEvent.class, this.arenaMatchService::handlePlayerSetupDisconnect);
            this.getEventRegistry().register(PlayerSetupDisconnectEvent.class, event -> this.afkActivityService.removePlayer(event.getUuid()));
            this.getEventRegistry().register(PlayerSetupDisconnectEvent.class, this.accessGateService::handlePlayerSetupDisconnect);
            this.getEventRegistry().register(PlayerConnectEvent.class, this.bootstrapCoordinator::handlePlayerConnect);
            this.getEventRegistry().register(PlayerConnectEvent.class, this.accessGateService::handlePlayerConnect);
            this.getEventRegistry().register(PlayerConnectEvent.class, this.secureTravelService::handlePlayerConnect);
            this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this.queueCoordinatorService::handlePlayerDisconnect);
            this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this.arenaMatchService::handlePlayerDisconnect);
            this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
                if (event.getPlayerRef() != null) {
                    this.afkActivityService.removePlayer(event.getPlayerRef().getUuid());
                }
            });
            this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, this.accessGateService::handlePlayerDisconnect);
            this.getEventRegistry().registerGlobal(PlayerDisconnectEvent.class, event -> {
                if (event.getPlayerRef() != null) {
                    this.nexoriStatusHudService.remove(event.getPlayerRef());
                }
            });
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this.secureTravelService::handlePlayerReady);
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
                io.github.hyjn.nexori.plugin.ui.menu.state.NexoriMenuV2State resumeState = null;
                io.github.hyjn.nexori.plugin.travel.ReadyPlayerSnapshot menuSnapshot =
                    new io.github.hyjn.nexori.plugin.travel.ReadyPlayerSnapshotResolver().resolve(event);
                if (!menuSnapshot.safe()) {
                    return;
                }
                if (menuSnapshot.world() == null) {
                    return;
                }
                com.hypixel.hytale.server.core.universe.PlayerRef playerRef = menuSnapshot.playerRef();

                String resumeStatus = this.bootstrapCoordinator.consumePendingMenuResumeStatus(playerRef.getUuid());
                if (resumeStatus.isBlank()) {
                    return;
                }

                playerRef.sendMessage(Message.raw(resumeStatus));
                resumeState = NexoriMenuV2State.initial().withStatusText(resumeStatus);
                NexoriMenuV2Page.open(menuSnapshot.entityRef(), menuSnapshot.store(), playerRef, menuSnapshot.player(), this, resumeState);
            });
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this.arenaMatchService::handlePlayerReady);
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, event -> {
                io.github.hyjn.nexori.plugin.travel.ReadyPlayerSnapshot hudSnapshot =
                    new io.github.hyjn.nexori.plugin.travel.ReadyPlayerSnapshotResolver().resolve(event);
                if (hudSnapshot.safe() && hudSnapshot.world() != null) {
                    this.nexoriStatusHudService.refresh(hudSnapshot.playerRef());
                }
            });
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this.destinationTargetDiscoveryService::handlePlayerReady);
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this.destinationTargetDiscoverySyncService::handlePlayerReady);
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this.serverPolicySyncService::handlePlayerReady);
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this.portalBindingSyncService::handlePlayerReady);
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this.networkCatalogSyncService::handlePlayerReady);
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this.diagnosticsCollectService::handlePlayerReady);
            AfkInventoryPacketActivityAdapter.register(this.afkActivityService, this.getLogger());
            // Entity systems keep portals, queues, match state, HUDs, and labels updated during world ticks.
            this.getEntityStoreRegistry().registerSystem(new AfkPlayerInputActivitySystem(this.afkActivityService));
            this.getEntityStoreRegistry().registerSystem(new NexoriPortalPlaceSystem(this.getLogger(), this.portalInstanceService));
            this.getEntityStoreRegistry().registerSystem(new NexoriPortalBreakSystem(this.getLogger(), this.portalInstanceService));
            this.getEntityStoreRegistry().registerSystem(new QueueCoordinatorTickSystem(this.queueCoordinatorService));
            this.getEntityStoreRegistry().registerSystem(new ArenaMatchTickSystem(this.arenaMatchService));
            this.getEntityStoreRegistry().registerSystem(new ArenaMatchWindowTickSystem(this.arenaMatchService));
            this.getEntityStoreRegistry().registerSystem(new BackendSyncTickSystem(this.backendSyncService));
            this.getEntityStoreRegistry().registerSystem(new BackendMatchAdmissionStateReportingTickSystem(this.backendMatchAdmissionStateReportingService));
            this.getEntityStoreRegistry().registerSystem(new BackendResultReportingTickSystem(this.backendResultReportingService));
            this.getEntityStoreRegistry().registerSystem(new BackendAfkContinuationCheckTickSystem(
                this.backendAfkContinuationCheckService,
                this::handleBackendAfkCancelDecision
            ));
            this.getEntityStoreRegistry().registerSystem(new NexoriStatusHudTickSystem(this.nexoriStatusHudService));
            this.getEntityStoreRegistry().registerSystem(new WorldLabelTickSystem(this.worldLabelService));
            SpectatorPickupSpatialGuard spectatorPickupSpatialGuard = new SpectatorPickupSpatialGuard(this.spectatorRuntimeService);
            this.getEntityStoreRegistry().registerSystem(new SpectatorPickupSpatialFilterSystem(
                spectatorPickupSpatialGuard,
                EntityModule.get().getPlayerSpatialResourceType()
            ));
            this.getEntityStoreRegistry().registerSystem(new SpectatorPickupSpatialRestoreSystem(
                spectatorPickupSpatialGuard,
                EntityModule.get().getPlayerSpatialResourceType()
            ));

            this.getLogger().atInfo().log(
                "Nexori ready. serverId=" + this.localIdentity.serverId()
                    + " fingerprint=" + this.localIdentity.fingerprint()
                    + " bootstrapOpen=" + bootstrapState.hasActiveSession()
                    + " configuredPeers=" + this.configuredPeerService.list().size()
                    + " destinationTargets=" + this.destinationTargetService.size()
                    + " portals=" + this.portalInstanceService.list().size()
            );
        } catch (IOException | GeneralSecurityException exception) {
            throw new IllegalStateException("Failed to initialize Nexori plugin", exception);
        }
    }

    /**
     * Returns the local identity manager that owns the server keypair and persisted identity metadata.
     */
    public ServerIdentityManager getIdentityManager() {
        return identityManager;
    }

    /**
     * Returns the bootstrap state store used by in-progress trust enrollment sessions.
     */
    public BootstrapStateStore getBootstrapStateStore() {
        return bootstrapStateStore;
    }

    /**
     * Returns the editable local peer service used by the Servers workspace.
     */
    public ConfiguredPeerService getConfiguredPeerService() {
        return configuredPeerService;
    }

    /**
     * Returns the service that stores this server's advertised connection address.
     */
    public LocalConnectionAddressService getLocalConnectionAddressService() {
        return localConnectionAddressService;
    }

    /**
     * Returns the coordinator that owns trust-bundle setup and reruns.
     */
    public BootstrapCoordinator getBootstrapCoordinator() {
        return bootstrapCoordinator;
    }

    /**
     * Returns this server's local Nexori identity.
     */
    public ServerIdentity getLocalIdentity() {
        return localIdentity;
    }

    /**
     * Returns the secure referral service used to encode and verify Nexori cross-server messages.
     */
    public SecureReferralService getSecureReferralService() {
        return secureReferralService;
    }

    /**
     * Returns the service that dispatches and accepts secure travel.
     */
    public SecureTravelService getSecureTravelService() {
        return secureTravelService;
    }

    /**
     * Returns the inventory transfer service used by travel profiles and recovery flows.
     */
    public InventoryTransferService getInventoryTransferService() {
        return inventoryTransferService;
    }

    public DestinationTargetService getDestinationTargetService() {
        return destinationTargetService;
    }

    public DestinationTargetDiscoveryService getDestinationTargetDiscoveryService() {
        return destinationTargetDiscoveryService;
    }

    public DestinationTargetDiscoverySyncService getDestinationTargetDiscoverySyncService() {
        return destinationTargetDiscoverySyncService;
    }

    public DiscoveredDestinationTargetCacheService getDiscoveredDestinationTargetCacheService() {
        return discoveredDestinationTargetCacheService;
    }

    public TriggerBindingService getTriggerBindingService() {
        return triggerBindingService;
    }

    public PortalBindingSyncService getPortalBindingSyncService() {
        return portalBindingSyncService;
    }

    public NetworkCatalogSyncService getNetworkCatalogSyncService() {
        return networkCatalogSyncService;
    }

    public PortalInstanceService getPortalInstanceService() {
        return portalInstanceService;
    }

    public TargetSetupDraftService getTargetSetupDraftService() {
        return targetSetupDraftService;
    }

    public PortalSetupDraftService getPortalSetupDraftService() {
        return portalSetupDraftService;
    }

    public NexoriPortalInteractionService getPortalInteractionService() {
        return portalInteractionService;
    }

    public ServerPolicyCacheService getServerPolicyCacheService() {
        return serverPolicyCacheService;
    }

    public ServerPolicySyncService getServerPolicySyncService() {
        return serverPolicySyncService;
    }

    public ServerRuleGroupService getServerRuleGroupService() {
        return serverRuleGroupService;
    }

    public DiagnosticsService getDiagnosticsService() {
        return diagnosticsService;
    }

    public TrustBundleStore getTrustBundleStore() {
        return trustBundleStore;
    }

    public DiagnosticsCollectService getDiagnosticsCollectService() {
        return diagnosticsCollectService;
    }

    public DiagnosticsOwnerReportService getDiagnosticsOwnerReportService() {
        return diagnosticsOwnerReportService;
    }

    public ArenaService getArenaService() {
        return arenaService;
    }

    public InstanceSpawnSlotService getInstanceSpawnSlotService() {
        return instanceSpawnSlotService;
    }

    public QueueService getQueueService() {
        return queueService;
    }

    public QueueCoordinatorService getQueueCoordinatorService() {
        return queueCoordinatorService;
    }

    public MatchSessionService getMatchSessionService() {
        return matchSessionService;
    }

    public ArenaMatchService getArenaMatchService() {
        return arenaMatchService;
    }

    public NexoriStatusHudService getNexoriStatusHudService() {
        return nexoriStatusHudService;
    }

    public NexoriAccessGateService getAccessGateService() {
        return accessGateService;
    }

    public BackendMatchmakingConfigStore getBackendMatchmakingConfigStore() {
        return backendMatchmakingConfigStore;
    }

    public BackendMatchAdmissionStateReportingService getBackendMatchAdmissionStateReportingService() {
        return backendMatchAdmissionStateReportingService;
    }

    public BackendSyncService getBackendSyncService() {
        return backendSyncService;
    }

    public BackendResultReportingService getBackendResultReportingService() {
        return backendResultReportingService;
    }

    public BackendAfkContinuationCheckService getBackendAfkContinuationCheckService() {
        return backendAfkContinuationCheckService;
    }

    private void handleBackendAfkCancelDecision(@Nonnull NexoriAfkContinuationDecision decision) {
        if (decision == null || this.arenaMatchService == null) {
            return;
        }
        ArenaMatchService.SubmitMatchResult result = this.arenaMatchService.cancelMatchForBackendAfk(
            decision.matchId(),
            decision.triggeringPlayerUuid(),
            decision.reasonCode(),
            decision.message()
        );
        if (result.outcome() != ArenaMatchService.SubmitMatchOutcome.ACCEPTED) {
            this.getLogger().atWarning().log(
                "Nexori backend AFK cancel could not complete matchId=" + decision.matchId()
                    + " outcome=" + result.outcome()
                    + " message=" + result.message()
            );
            return;
        }
        if (this.backendResultReportingService == null) {
            return;
        }
        BackendResultReportingService.EnqueueResult enqueueResult = this.backendResultReportingService.enqueueResult(
            result.activeMatch(),
            result.players(),
            result.metadata(),
            result.customData(),
            result.reason(),
            result.resultPayloadHash(),
            result.endedAtEpochMs()
        );
        this.getLogger().atInfo().log(
            "Nexori backend AFK cancel completed matchId=" + result.matchId()
                + " resultReport=" + enqueueResult.outcome()
                + " resultId=" + enqueueResult.resultId()
        );
    }

    /**
     * Returns Nexori's public minigame integration surface for other mods.
     * Prefer this API over touching internal runtime services directly.
     */
    public NexoriMinigameApi getMinigameApi() {
        return minigameApi;
    }

    private void registerAdminCommand(@Nonnull AbstractCommand command) {
        command.requirePermission(this.getBasePermission() + ".admin");
        this.getCommandRegistry().registerCommand(command);
    }

    private void removeBuiltInOpCommands() {
        CommandManager commandManager = CommandManager.get();
        if (commandManager == null) {
            this.getLogger().atWarning().log("Nexori could not resolve CommandManager; built-in OP commands were not removed.");
            return;
        }

        Map<String, AbstractCommand> registrations = commandManager.getCommandRegistration();
        if (registrations == null || registrations.isEmpty()) {
            this.getLogger().atWarning().log("Nexori found no registered commands to filter; built-in OP commands may remain visible.");
            return;
        }

        int removed = 0;
        Iterator<Map.Entry<String, AbstractCommand>> iterator = registrations.entrySet().iterator();
        while (iterator.hasNext()) {
            Map.Entry<String, AbstractCommand> entry = iterator.next();
            AbstractCommand command = entry.getValue();
            if (command == null) {
                continue;
            }
            String className = command.getClass().getName();
            if (className.startsWith("com.hypixel.hytale.server.core.permissions.commands.op.")) {
                iterator.remove();
                removed++;
            }
        }

        this.getLogger().atInfo().log("Nexori removed " + removed + " built-in OP command registrations.");
    }
}
