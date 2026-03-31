package io.github.hyjn.nexori.plugin.command;

import io.github.hyjn.nexori.plugin.NexoriPlugin;
import io.github.hyjn.nexori.plugin.bootstrap.BootstrapState;
import io.github.hyjn.nexori.plugin.bootstrap.TrustBundle;
import io.github.hyjn.nexori.plugin.identity.ServerIdentity;
import io.github.hyjn.nexori.plugin.peers.ConfiguredPeer;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.DefaultArg;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;

import javax.annotation.Nonnull;
import java.time.Duration;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Locale;

public class NexoriCommand extends CommandBase {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    private final NexoriPlugin plugin;
    private final DefaultArg<String> actionArg;
    private final OptionalArg<String> valueArg;

    public NexoriCommand(NexoriPlugin plugin) {
        super("nexori", "Shows Nexori status, manages saved peer IPs, and controls the local bootstrap window.");
        this.plugin = plugin;
        this.setPermissionGroup(GameMode.Adventure);
        this.actionArg = this.withDefaultArg("action", "Action to run.", ArgTypes.STRING, "status", "status");
        this.valueArg = this.withOptionalArg("value", "Optional value for the selected action.", ArgTypes.STRING);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        String action = ctx.get(actionArg).toLowerCase(Locale.ROOT);
        switch (action) {
            case "help" -> sendHelp(ctx);
            case "status" -> sendStatus(ctx);
            case "fingerprint" -> sendFingerprint(ctx);
            case "open" -> openBootstrap(ctx);
            case "close" -> closeBootstrap(ctx);
            case "peers", "list" -> listPeers(ctx);
            case "add" -> addPeer(ctx);
            case "remove" -> removePeer(ctx);
            case "clear" -> clearPeers(ctx);
            case "start" -> ctx.sendMessage(Message.raw("Bootstrap travel is player-only. Use /nexoristart or open /nexorimenu in game."));
            default -> {
                ctx.sendMessage(Message.raw("Unknown action '" + action + "'."));
                sendHelp(ctx);
            }
        }
    }

    private void sendHelp(CommandContext ctx) {
        ctx.sendMessage(Message.raw("Nexori commands:"));
        ctx.sendMessage(Message.raw("- /nexori status"));
        ctx.sendMessage(Message.raw("- /nexori fingerprint"));
        ctx.sendMessage(Message.raw("- /nexori peers"));
        ctx.sendMessage(Message.raw("- /nexori add <host:port>"));
        ctx.sendMessage(Message.raw("- /nexori remove <host:port>"));
        ctx.sendMessage(Message.raw("- /nexori clear"));
        ctx.sendMessage(Message.raw("- /nexori open [minutes]"));
        ctx.sendMessage(Message.raw("- /nexori close"));
        ctx.sendMessage(Message.raw("- /nexoristart"));
        ctx.sendMessage(Message.raw("- /nexoritravel <host:port> [--targetId=<id>] [--arrivalPoint=<id>] [--travelProfile=<id>]"));
        ctx.sendMessage(Message.raw("- /nexoridiscover <host:port>"));
        ctx.sendMessage(Message.raw("- /nexoridiscovered [host:port]"));
        ctx.sendMessage(Message.raw("- /nexoritarget"));
        ctx.sendMessage(Message.raw("- /nexoritargetlist"));
        ctx.sendMessage(Message.raw("- /nexoritargetadd <targetId> <kind> <world> <arrivalPoint>"));
        ctx.sendMessage(Message.raw("  Intended for COORDINATE targets only. NATURAL_SPAWN and PORTAL are auto-generated."));
        ctx.sendMessage(Message.raw("  Use /nexorimenu and open the Targets tab for the guided in-game target manager."));
        ctx.sendMessage(Message.raw("- /nexoritargetshow <targetId>"));
        ctx.sendMessage(Message.raw("- /nexoritargetremove <targetId>"));
        ctx.sendMessage(Message.raw("- /nexoriportalgive [amount]"));
        ctx.sendMessage(Message.raw("- /nexoriportallist"));
        ctx.sendMessage(Message.raw("- /nexoriportalshow <portalId>"));
        ctx.sendMessage(Message.raw("- /nexoriportalbind <portalId> <host:port> <targetId> [travelProfile]"));
        ctx.sendMessage(Message.raw("- /nexoriportalunbind <portalId>"));
        ctx.sendMessage(Message.raw("- /nexoribackuplimit <1-50>"));
        ctx.sendMessage(Message.raw("- /nexorirecoverymode <status|enable|disable>"));
        ctx.sendMessage(Message.raw("- /nexorirecovery"));
        ctx.sendMessage(Message.raw("  Opens the in-game recovery list for your recent inventory transfer backups."));
        ctx.sendMessage(Message.raw("- /nexoribackups"));
        ctx.sendMessage(Message.raw("- /nexorirecover <transferId>"));
        ctx.sendMessage(Message.raw("  Travel profiles: KEEP_INVENTORY, CLEAR_INVENTORY, APPLY_INVENTORY"));
        ctx.sendMessage(Message.raw("- /nexorimenu"));
        ctx.sendMessage(Message.raw("  Opens the main Nexori HyUI admin page for server, rules, and target setup."));
    }

    private void sendStatus(CommandContext ctx) {
        ServerIdentity identity = plugin.getLocalIdentity();
        BootstrapState bootstrapState = plugin.getBootstrapStateStore().getCurrentState();
        TrustBundle bundle = plugin.getBootstrapCoordinator().getTrustBundle();
        int peerCount = plugin.getConfiguredPeerService().list().size();
        int targetCount = plugin.getDestinationTargetService().size();
        ctx.sendMessage(Message.raw("Nexori serverId: " + identity.serverId()));
        ctx.sendMessage(Message.raw("Fingerprint: " + identity.fingerprint()));
        ctx.sendMessage(Message.raw("Saved peers: " + peerCount));
        ctx.sendMessage(Message.raw("Destination targets: " + targetCount));
        ctx.sendMessage(Message.raw("Inventory recovery: "
            + (plugin.getInventoryTransferService().isRecoveryEnabled() ? "enabled" : "disabled")));
        ctx.sendMessage(Message.raw("Backups per player: " + plugin.getInventoryTransferService().getMaxBackupsPerPlayer()));
        ctx.sendMessage(Message.raw("Bootstrap open: " + bootstrapState.hasActiveSession()));
        ctx.sendMessage(Message.raw("Bundle version: " + bootstrapState.bundleVersion()));
        ctx.sendMessage(Message.raw("Verified bundle members: " + bundle.members().size()));
        if (bootstrapState.hasActiveSession()) {
            ctx.sendMessage(Message.raw("Session ID: " + bootstrapState.sessionId()));
            ctx.sendMessage(Message.raw("Session expires: " + TIME_FORMATTER.format(Instant.ofEpochMilli(bootstrapState.sessionExpiresAtEpochMillis()))));
        }
        if (!bootstrapState.bundleHash().isBlank()) {
            ctx.sendMessage(Message.raw("Bundle hash: " + bootstrapState.bundleHash()));
        }
    }

    private void sendFingerprint(CommandContext ctx) {
        ServerIdentity identity = plugin.getLocalIdentity();
        ctx.sendMessage(Message.raw("Server fingerprint: " + identity.fingerprint()));
        ctx.sendMessage(Message.raw("Public key (base64): " + identity.publicKeyBase64()));
    }

    private void openBootstrap(CommandContext ctx) {
        if (!hasAdminPermission(ctx)) {
            return;
        }

        int minutes = 5;
        if (ctx.provided(valueArg)) {
            try {
                minutes = Integer.parseInt(ctx.get(valueArg));
            } catch (NumberFormatException exception) {
                ctx.sendMessage(Message.raw("Use a number of minutes between 1 and 30."));
                return;
            }
        }
        if (minutes < 1 || minutes > 30) {
            ctx.sendMessage(Message.raw("Bootstrap window must be between 1 and 30 minutes."));
            return;
        }

        BootstrapState state = plugin.getBootstrapStateStore().openSession(Duration.ofMinutes(minutes));
        ctx.sendMessage(Message.raw("Bootstrap opened for " + minutes + " minute(s)."));
        ctx.sendMessage(Message.raw("Session ID: " + state.sessionId()));
        ctx.sendMessage(Message.raw("Expires: " + TIME_FORMATTER.format(Instant.ofEpochMilli(state.sessionExpiresAtEpochMillis()))));
    }

    private void closeBootstrap(CommandContext ctx) {
        if (!hasAdminPermission(ctx)) {
            return;
        }

        BootstrapState state = plugin.getBootstrapStateStore().closeSession();
        ctx.sendMessage(Message.raw("Bootstrap closed. Active session now: " + state.hasActiveSession()));
    }

    private void listPeers(CommandContext ctx) {
        List<ConfiguredPeer> peers = plugin.getConfiguredPeerService().list();
        if (peers.isEmpty()) {
            ctx.sendMessage(Message.raw("No Nexori peers saved yet. Add one with /nexori add <host:port> or open /nexorimenu."));
            return;
        }

        ctx.sendMessage(Message.raw("Saved Nexori peers:"));
        for (ConfiguredPeer peer : peers) {
            ctx.sendMessage(Message.raw("- " + peer.connectionAddress()));
        }
    }

    private void addPeer(CommandContext ctx) {
        if (!hasAdminPermission(ctx)) {
            return;
        }
        if (!ctx.provided(valueArg)) {
            ctx.sendMessage(Message.raw("Use /nexori add <host:port>."));
            return;
        }

        try {
            ConfiguredPeer peer = plugin.getConfiguredPeerService().add(ctx.get(valueArg));
            ctx.sendMessage(Message.raw("Saved Nexori peer " + peer.connectionAddress() + "."));
        } catch (IllegalArgumentException exception) {
            ctx.sendMessage(Message.raw(exception.getMessage()));
        } catch (Exception exception) {
            ctx.sendMessage(Message.raw("Failed to save the Nexori peer: " + exception.getMessage()));
        }
    }

    private void removePeer(CommandContext ctx) {
        if (!hasAdminPermission(ctx)) {
            return;
        }
        if (!ctx.provided(valueArg)) {
            ctx.sendMessage(Message.raw("Use /nexori remove <host:port>."));
            return;
        }

        try {
            boolean removed = plugin.getConfiguredPeerService().remove(ctx.get(valueArg));
            ctx.sendMessage(Message.raw(removed
                ? "Removed the Nexori peer."
                : "That Nexori peer was not in the saved list."));
        } catch (IllegalArgumentException exception) {
            ctx.sendMessage(Message.raw(exception.getMessage()));
        } catch (Exception exception) {
            ctx.sendMessage(Message.raw("Failed to remove the Nexori peer: " + exception.getMessage()));
        }
    }

    private void clearPeers(CommandContext ctx) {
        if (!hasAdminPermission(ctx)) {
            return;
        }

        try {
            plugin.getConfiguredPeerService().clear();
            ctx.sendMessage(Message.raw("Cleared all saved Nexori peers on this server."));
        } catch (Exception exception) {
            ctx.sendMessage(Message.raw("Failed to clear saved Nexori peers: " + exception.getMessage()));
        }
    }

    private boolean hasAdminPermission(CommandContext ctx) {
        String permission = plugin.getBasePermission() + ".admin";
        if (ctx.sender().hasPermission(permission)) {
            return true;
        }

        ctx.sendMessage(Message.raw("You need the permission '" + permission + "' to manage Nexori bootstrap state."));
        return false;
    }
}
