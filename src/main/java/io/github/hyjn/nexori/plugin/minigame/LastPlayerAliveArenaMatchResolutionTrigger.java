package io.github.hyjn.nexori.plugin.minigame;

import com.hypixel.hytale.server.core.Message;
import com.hypixel.hytale.server.core.universe.PlayerRef;
import com.hypixel.hytale.server.core.universe.Universe;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.UUID;

public final class LastPlayerAliveArenaMatchResolutionTrigger {

    public static final String ID = "last_player_alive";

    private LastPlayerAliveArenaMatchResolutionTrigger() {
    }

    @Nonnull
    public static ArenaActiveMatch evaluate(@Nonnull ArenaMatchService arenaMatchService, @Nonnull ArenaActiveMatch match, long nowEpochMs) {
        if (match.hasWinner() || !match.allExpectedPlayersArrived()) {
            return match;
        }

        List<UUID> alivePlayers = match.alivePlayerUuids();
        if (alivePlayers.size() != 1) {
            return match;
        }

        UUID winnerUuid = alivePlayers.get(0);
        PlayerRef winnerRef = Universe.get().getPlayer(winnerUuid);
        if (winnerRef != null && winnerRef.isValid()) {
            winnerRef.sendMessage(Message.raw("You won this Nexori match. Returning to the lobby in 5 seconds."));
        }
        return arenaMatchService.markPlayerWinInternal(
            match,
            winnerUuid,
            ID,
            nowEpochMs
        );
    }
}
