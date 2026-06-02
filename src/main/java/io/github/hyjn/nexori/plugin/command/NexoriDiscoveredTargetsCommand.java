package io.github.hyjn.nexori.plugin.command;

import com.hypixel.hytale.protocol.GameMode;
import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.command.system.CommandContext;
import com.hypixel.hytale.server.core.command.system.arguments.system.OptionalArg;
import com.hypixel.hytale.server.core.command.system.arguments.types.ArgTypes;
import com.hypixel.hytale.server.core.command.system.basecommands.CommandBase;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetCacheService;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSet;
import io.github.hyjn.nexori.plugin.discovery.DiscoveredDestinationTargetSummary;

import javax.annotation.Nonnull;
import java.time.Instant;
import java.time.ZoneOffset;
import java.time.format.DateTimeFormatter;
import java.util.Locale;

public final class NexoriDiscoveredTargetsCommand extends CommandBase {

    private static final DateTimeFormatter TIME_FORMATTER = DateTimeFormatter.ISO_OFFSET_DATE_TIME.withZone(ZoneOffset.UTC);

    private final DiscoveredDestinationTargetCacheService cacheService;
    private final OptionalArg<String> destinationArg;

    public NexoriDiscoveredTargetsCommand(@Nonnull DiscoveredDestinationTargetCacheService cacheService) {
        super("nexoridiscovered", "Shows cached Nexori destination targets discovered from trusted servers.");
        this.cacheService = cacheService;
        this.destinationArg = withOptionalArg("destination", "Optional destination in host:port format.", ArgTypes.STRING);
    }

    @Override
    protected void executeSync(@Nonnull CommandContext ctx) {
        if (ctx.provided(destinationArg)) {
            cacheService.find(ctx.get(destinationArg)).ifPresentOrElse(discovery -> {
                ctx.sendMessage(Message.raw("Cached Nexori targets for " + discovery.connectionAddress() + ":"));
                ctx.sendMessage(Message.raw("Remote serverId: " + discovery.remoteServerId()));
                ctx.sendMessage(Message.raw("Discovered at: " + TIME_FORMATTER.format(Instant.ofEpochMilli(discovery.discoveredAtEpochMillis()))));
                for (DiscoveredDestinationTargetSummary target : discovery.targets()) {
                    ctx.sendMessage(Message.raw("- " + target.id()
                        + " kind=" + target.kind()
                        + " world=" + target.worldName()
                        + " arrivalPoint=" + (target.arrivalPointId() == null || target.arrivalPointId().isBlank() ? "<none>" : target.arrivalPointId())));
                }
                if (discovery.targets().isEmpty()) {
                    ctx.sendMessage(Message.raw("No remote destination targets were reported for this server."));
                }
            }, () -> ctx.sendMessage(Message.raw("No discovered Nexori targets are cached for that server yet.")));
            return;
        }

        var discoveries = cacheService.list();
        if (discoveries.isEmpty()) {
            ctx.sendMessage(Message.raw("No remote Nexori destination target discoveries are cached yet."));
            return;
        }

        ctx.sendMessage(Message.raw("Cached Nexori destination target discoveries:"));
        for (DiscoveredDestinationTargetSet discovery : discoveries) {
            ctx.sendMessage(Message.raw("- " + discovery.connectionAddress()
                + " targets=" + discovery.targets().size()
                + " discoveredAt=" + TIME_FORMATTER.format(Instant.ofEpochMilli(discovery.discoveredAtEpochMillis()))));
        }
    }
}
