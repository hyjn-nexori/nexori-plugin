package io.github.hyjn.nexori.plugin;

import io.github.hyjn.nexori.plugin.bootstrap.BootstrapState;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapCoordinator;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapRunStore;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapStateStore;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundleStore;
import io.github.hyjn.nexori.plugin.assets.PluginAssetPackRegistrar;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingService;
import io.github.hyjn.nexori.plugin.binding.TriggerBindingStore;
import io.github.hyjn.nexori.plugin.command.NexoriCommand;
import io.github.hyjn.nexori.plugin.command.NexoriBackupsCommand;
import io.github.hyjn.nexori.plugin.command.NexoriBackupLimitCommand;
import io.github.hyjn.nexori.plugin.command.NexoriDiscoverCommand;
import io.github.hyjn.nexori.plugin.command.NexoriDiscoveredTargetsCommand;
import io.github.hyjn.nexori.plugin.command.NexoriPortalBindCommand;
import io.github.hyjn.nexori.plugin.command.NexoriPortalGiveCommand;
import io.github.hyjn.nexori.plugin.command.NexoriPortalListCommand;
import io.github.hyjn.nexori.plugin.command.NexoriPortalShowCommand;
import io.github.hyjn.nexori.plugin.command.NexoriPortalUnbindCommand;
import io.github.hyjn.nexori.plugin.command.NexoriRecoverCommand;
import io.github.hyjn.nexori.plugin.command.NexoriRecoveryModeCommand;
import io.github.hyjn.nexori.plugin.command.NexoriRecoveryPageCommand;
import io.github.hyjn.nexori.plugin.command.NexoriStartCommand;
import io.github.hyjn.nexori.plugin.command.NexoriTargetAddCommand;
import io.github.hyjn.nexori.plugin.command.NexoriTargetHelpCommand;
import io.github.hyjn.nexori.plugin.command.NexoriTargetListCommand;
import io.github.hyjn.nexori.plugin.command.NexoriTargetRemoveCommand;
import io.github.hyjn.nexori.plugin.command.NexoriTargetShowCommand;
import io.github.hyjn.nexori.plugin.command.NexoriTargetWizardCommand;
import io.github.hyjn.nexori.plugin.command.NexoriTravelCommand;
import io.github.hyjn.nexori.plugin.discovery.DestinationTargetDiscoveryService;
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
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeerService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeerStore;
import io.github.hyjn.nexori.plugin.peers.LocalConnectionAddressService;
import io.github.hyjn.nexori.plugin.policy.ServerPolicyCacheService;
import io.github.hyjn.nexori.plugin.policy.ServerPolicyCacheStore;
import io.github.hyjn.nexori.plugin.policy.ServerPolicySyncService;
import io.github.hyjn.nexori.plugin.portal.NexoriPortalBreakSystem;
import io.github.hyjn.nexori.plugin.portal.NexoriPortalInteractionService;
import io.github.hyjn.nexori.plugin.portal.NexoriPortalPlaceSystem;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceService;
import io.github.hyjn.nexori.plugin.portal.PortalInstanceStore;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;
import io.github.hyjn.nexori.plugin.target.DestinationTargetDefaults;
import io.github.hyjn.nexori.plugin.target.DestinationTargetService;
import io.github.hyjn.nexori.plugin.target.DestinationTargetStore;
import io.github.hyjn.nexori.plugin.travel.SecureTravelService;
import io.github.hyjn.nexori.plugin.ui.NexoriMenuCommand;
import io.github.hyjn.nexori.plugin.ui.NexoriMenuHyUiCommand;
import io.github.hyjn.nexori.plugin.ui.PortalSetupDraftService;
import io.github.hyjn.nexori.plugin.ui.TargetSetupDraftService;

import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerReadyEvent;
import com.hypixel.hytale.server.core.event.events.player.PlayerSetupConnectEvent;
import com.hypixel.hytale.server.core.plugin.JavaPlugin;
import com.hypixel.hytale.server.core.plugin.JavaPluginInit;

import javax.annotation.Nonnull;
import java.io.IOException;
import java.nio.file.Files;
import java.security.GeneralSecurityException;

public class NexoriPlugin extends JavaPlugin {

    private ServerIdentityManager identityManager;
    private BootstrapStateStore bootstrapStateStore;
    private ConfiguredPeerService configuredPeerService;
    private BootstrapCoordinator bootstrapCoordinator;
    private TrustBundleStore trustBundleStore;
    private LocalConnectionAddressService localConnectionAddressService;
    private DestinationTargetService destinationTargetService;
    private DiscoveredDestinationTargetCacheService discoveredDestinationTargetCacheService;
    private TriggerBindingService triggerBindingService;
    private PortalInstanceService portalInstanceService;
    private SecureReferralService secureReferralService;
    private InventoryTransferService inventoryTransferService;
    private ServerPolicyCacheService serverPolicyCacheService;
    private ServerPolicySyncService serverPolicySyncService;
    private SecureTravelService secureTravelService;
    private DestinationTargetDiscoveryService destinationTargetDiscoveryService;
    private NexoriPortalInteractionService portalInteractionService;
    private PortalSetupDraftService portalSetupDraftService;
    private TargetSetupDraftService targetSetupDraftService;
    private ServerIdentity localIdentity;

    public NexoriPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        try {
            Files.createDirectories(this.getDataDirectory());
            PluginAssetPackRegistrar.registerSelfAsAssetPack(this);

            this.identityManager = new ServerIdentityManager(this.getDataDirectory().resolve("identity"));
            this.localIdentity = this.identityManager.loadOrCreate();

            this.bootstrapStateStore = new BootstrapStateStore(this.getDataDirectory().resolve("state"));
            BootstrapState bootstrapState = this.bootstrapStateStore.loadOrCreate();
            this.configuredPeerService = new ConfiguredPeerService(
                new ConfiguredPeerStore(this.getDataDirectory().resolve("config").resolve("configured-peers.json"))
            );
            this.localConnectionAddressService = new LocalConnectionAddressService(
                this.getDataDirectory().resolve("config").resolve("local-connection-address.txt")
            );
            this.destinationTargetService = new DestinationTargetService(
                new DestinationTargetStore(this.getDataDirectory().resolve("config").resolve("destination-targets.json"))
            );
            this.discoveredDestinationTargetCacheService = new DiscoveredDestinationTargetCacheService(
                new DiscoveredDestinationTargetCacheStore(this.getDataDirectory().resolve("config").resolve("discovered-destination-targets.json"))
            );
            this.triggerBindingService = new TriggerBindingService(
                new TriggerBindingStore(this.getDataDirectory().resolve("config").resolve("trigger-bindings.json"))
            );
            this.portalInstanceService = new PortalInstanceService(
                new PortalInstanceStore(this.getDataDirectory().resolve("config").resolve("portal-instances.json")),
                this.destinationTargetService,
                this.triggerBindingService
            );
            new DestinationTargetDefaults(this.getDataDirectory(), this.destinationTargetService).ensureDefaults(this.getLogger());
            this.trustBundleStore = new TrustBundleStore(this.getDataDirectory().resolve("state").resolve("trust-bundle.json"));
            this.trustBundleStore.loadOrCreate();
            BootstrapRunStore bootstrapRunStore = new BootstrapRunStore(this.getDataDirectory().resolve("state").resolve("bootstrap-run.json"));
            bootstrapRunStore.load();
            this.bootstrapCoordinator = new BootstrapCoordinator(
                this.getLogger(),
                this.identityManager,
                this.localIdentity,
                this.bootstrapStateStore,
                this.configuredPeerService,
                this.localConnectionAddressService,
                bootstrapRunStore,
                this.trustBundleStore
            );
            this.secureReferralService = new SecureReferralService(
                this.getLogger(),
                this.identityManager,
                this.localIdentity,
                this.trustBundleStore
            );
            this.inventoryTransferService = new InventoryTransferService(
                this.getLogger(),
                new InventoryTransferBackupStore(this.getDataDirectory().resolve("state").resolve("inventory-transfer-backups.json")),
                new InventoryTransferReceiptStore(this.getDataDirectory().resolve("state").resolve("inventory-transfer-receipts.json")),
                new InventoryTransferPolicyStore(this.getDataDirectory().resolve("config").resolve("inventory-transfer-policy.json")),
                new PlayerSaveRepository(this.getLogger()),
                new InventorySnapshotService(),
                this.secureReferralService
            );
            this.serverPolicyCacheService = new ServerPolicyCacheService(
                new ServerPolicyCacheStore(this.getDataDirectory().resolve("config").resolve("discovered-server-policies.json"))
            );
            this.serverPolicySyncService = new ServerPolicySyncService(
                this.getLogger(),
                this.trustBundleStore,
                this.inventoryTransferService,
                this.serverPolicyCacheService,
                this.secureReferralService
            );
            this.secureTravelService = new SecureTravelService(
                this.getLogger(),
                this.localIdentity,
                this.trustBundleStore,
                this.destinationTargetService,
                this.secureReferralService,
                this.inventoryTransferService
            );
            this.destinationTargetDiscoveryService = new DestinationTargetDiscoveryService(
                this.getLogger(),
                this.trustBundleStore,
                this.destinationTargetService,
                this.discoveredDestinationTargetCacheService,
                this.secureReferralService
            );
            this.portalSetupDraftService = new PortalSetupDraftService();
            this.targetSetupDraftService = new TargetSetupDraftService();
            this.portalInteractionService = new NexoriPortalInteractionService(
                this,
                this.getLogger(),
                this.portalInstanceService,
                this.triggerBindingService,
                this.configuredPeerService,
                this.discoveredDestinationTargetCacheService,
                this.destinationTargetDiscoveryService,
                this.portalSetupDraftService,
                this.secureTravelService,
                this.getBasePermission() + ".admin"
            );
            this.secureReferralService.registerHandler(this.secureTravelService);
            this.secureReferralService.registerHandler(this.destinationTargetDiscoveryService.requestHandler());
            this.secureReferralService.registerHandler(this.destinationTargetDiscoveryService.responseHandler());
            this.secureReferralService.registerHandler(this.inventoryTransferService.queryHandler());
            this.secureReferralService.registerHandler(this.inventoryTransferService.replyHandler());
            this.secureReferralService.registerHandler(this.serverPolicySyncService.fetchRequestHandler());
            this.secureReferralService.registerHandler(this.serverPolicySyncService.applyRequestHandler());
            this.secureReferralService.registerHandler(this.serverPolicySyncService.responseHandler());
            this.portalInteractionService.registerPageSupplier();

            this.getCommandRegistry().registerCommand(new NexoriCommand(this));
            this.getCommandRegistry().registerCommand(new NexoriBackupLimitCommand(this, this.inventoryTransferService));
            this.getCommandRegistry().registerCommand(new NexoriRecoveryModeCommand(this, this.inventoryTransferService));
            this.getCommandRegistry().registerCommand(new NexoriBackupsCommand(this.inventoryTransferService));
            this.getCommandRegistry().registerCommand(new NexoriDiscoverCommand(this, this.destinationTargetDiscoveryService));
            this.getCommandRegistry().registerCommand(new NexoriDiscoveredTargetsCommand(this.discoveredDestinationTargetCacheService));
            this.getCommandRegistry().registerCommand(new NexoriPortalGiveCommand(this));
            this.getCommandRegistry().registerCommand(new NexoriPortalListCommand(this.portalInstanceService, this.triggerBindingService));
            this.getCommandRegistry().registerCommand(new NexoriPortalShowCommand(this.portalInstanceService, this.triggerBindingService));
            this.getCommandRegistry().registerCommand(new NexoriPortalBindCommand(this, this.portalInstanceService, this.triggerBindingService));
            this.getCommandRegistry().registerCommand(new NexoriPortalUnbindCommand(this, this.triggerBindingService));
            this.getCommandRegistry().registerCommand(new NexoriRecoverCommand(this.inventoryTransferService));
            this.getCommandRegistry().registerCommand(new NexoriRecoveryPageCommand(this.inventoryTransferService));
            this.getCommandRegistry().registerCommand(new NexoriTargetHelpCommand());
            this.getCommandRegistry().registerCommand(new NexoriTargetListCommand(this));
            this.getCommandRegistry().registerCommand(new NexoriTargetShowCommand(this));
            this.getCommandRegistry().registerCommand(new NexoriTargetWizardCommand(this));
            this.getCommandRegistry().registerCommand(new NexoriTargetAddCommand(this));
            this.getCommandRegistry().registerCommand(new NexoriTargetRemoveCommand(this));
            this.getCommandRegistry().registerCommand(new NexoriStartCommand(this.bootstrapCoordinator, this.getBasePermission() + ".admin"));
            this.getCommandRegistry().registerCommand(new NexoriTravelCommand(this.secureTravelService));
            this.getCommandRegistry().registerCommand(new NexoriMenuCommand(this));
            this.getCommandRegistry().registerCommand(new NexoriMenuHyUiCommand(this));
            this.getEventRegistry().register(PlayerSetupConnectEvent.class, this.bootstrapCoordinator::handlePlayerSetupConnect);
            this.getEventRegistry().register(PlayerSetupConnectEvent.class, this.secureReferralService::handlePlayerSetupConnect);
            this.getEventRegistry().register(PlayerConnectEvent.class, this.bootstrapCoordinator::handlePlayerConnect);
            this.getEventRegistry().register(PlayerConnectEvent.class, this.secureTravelService::handlePlayerConnect);
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this.secureTravelService::handlePlayerReady);
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this.destinationTargetDiscoveryService::handlePlayerReady);
            this.getEventRegistry().registerGlobal(PlayerReadyEvent.class, this.serverPolicySyncService::handlePlayerReady);
            this.getEntityStoreRegistry().registerSystem(new NexoriPortalPlaceSystem(this.getLogger(), this.portalInstanceService));
            this.getEntityStoreRegistry().registerSystem(new NexoriPortalBreakSystem(this.getLogger(), this.portalInstanceService));

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

    public ServerIdentityManager getIdentityManager() {
        return identityManager;
    }

    public BootstrapStateStore getBootstrapStateStore() {
        return bootstrapStateStore;
    }

    public ConfiguredPeerService getConfiguredPeerService() {
        return configuredPeerService;
    }

    public LocalConnectionAddressService getLocalConnectionAddressService() {
        return localConnectionAddressService;
    }

    public BootstrapCoordinator getBootstrapCoordinator() {
        return bootstrapCoordinator;
    }

    public ServerIdentity getLocalIdentity() {
        return localIdentity;
    }

    public SecureReferralService getSecureReferralService() {
        return secureReferralService;
    }

    public SecureTravelService getSecureTravelService() {
        return secureTravelService;
    }

    public InventoryTransferService getInventoryTransferService() {
        return inventoryTransferService;
    }

    public DestinationTargetService getDestinationTargetService() {
        return destinationTargetService;
    }

    public DestinationTargetDiscoveryService getDestinationTargetDiscoveryService() {
        return destinationTargetDiscoveryService;
    }

    public DiscoveredDestinationTargetCacheService getDiscoveredDestinationTargetCacheService() {
        return discoveredDestinationTargetCacheService;
    }

    public TriggerBindingService getTriggerBindingService() {
        return triggerBindingService;
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
}
