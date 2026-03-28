package io.github.hyjn.nexori.plugin;

import io.github.hyjn.nexori.plugin.bootstrap.BootstrapState;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapCoordinator;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapRunStore;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapStateStore;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundleStore;
import io.github.hyjn.nexori.plugin.command.NexoriCommand;
import io.github.hyjn.nexori.plugin.command.NexoriStartCommand;
import io.github.hyjn.nexori.plugin.command.NexoriTravelCommand;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.identity.ServerIdentityManager;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeerService;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeerStore;
import io.github.hyjn.nexori.plugin.secure.SecureReferralService;
import io.github.hyjn.nexori.plugin.travel.SecureTravelService;
import io.github.hyjn.nexori.plugin.ui.NexoriMenuCommand;

import com.hypixel.hytale.server.core.event.events.player.PlayerConnectEvent;
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
    private SecureReferralService secureReferralService;
    private SecureTravelService secureTravelService;
    private ServerIdentity localIdentity;

    public NexoriPlugin(@Nonnull JavaPluginInit init) {
        super(init);
    }

    @Override
    protected void setup() {
        try {
            Files.createDirectories(this.getDataDirectory());

            this.identityManager = new ServerIdentityManager(this.getDataDirectory().resolve("identity"));
            this.localIdentity = this.identityManager.loadOrCreate();

            this.bootstrapStateStore = new BootstrapStateStore(this.getDataDirectory().resolve("state"));
            BootstrapState bootstrapState = this.bootstrapStateStore.loadOrCreate();
            this.configuredPeerService = new ConfiguredPeerService(
                new ConfiguredPeerStore(this.getDataDirectory().resolve("config").resolve("configured-peers.json"))
            );
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
                bootstrapRunStore,
                this.trustBundleStore
            );
            this.secureReferralService = new SecureReferralService(
                this.getLogger(),
                this.identityManager,
                this.localIdentity,
                this.trustBundleStore
            );
            this.secureTravelService = new SecureTravelService(
                this.getLogger(),
                this.localIdentity,
                this.trustBundleStore,
                this.secureReferralService
            );
            this.secureReferralService.registerHandler(this.secureTravelService);

            this.getCommandRegistry().registerCommand(new NexoriCommand(this));
            this.getCommandRegistry().registerCommand(new NexoriStartCommand(this.bootstrapCoordinator, this.getBasePermission() + ".admin"));
            this.getCommandRegistry().registerCommand(new NexoriTravelCommand(this.secureTravelService));
            this.getCommandRegistry().registerCommand(new NexoriMenuCommand(this, this.configuredPeerService, this.bootstrapCoordinator));
            this.getEventRegistry().register(PlayerSetupConnectEvent.class, this.bootstrapCoordinator::handlePlayerSetupConnect);
            this.getEventRegistry().register(PlayerSetupConnectEvent.class, this.secureReferralService::handlePlayerSetupConnect);
            this.getEventRegistry().register(PlayerConnectEvent.class, this.bootstrapCoordinator::handlePlayerConnect);
            this.getEventRegistry().register(PlayerConnectEvent.class, this.secureTravelService::handlePlayerConnect);

            this.getLogger().atInfo().log(
                "Nexori ready. serverId=" + this.localIdentity.serverId()
                    + " fingerprint=" + this.localIdentity.fingerprint()
                    + " bootstrapOpen=" + bootstrapState.hasActiveSession()
                    + " configuredPeers=" + this.configuredPeerService.list().size()
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
}
