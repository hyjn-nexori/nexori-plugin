package io.github.hyjn.nexori.plugin.backend;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.tick.EntityTickingSystem;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import io.github.hyjn.nexori.plugin.api.minigame.NexoriAfkContinuationDecision;

import javax.annotation.Nonnull;
import java.util.List;
import java.util.function.Consumer;

public final class BackendAfkContinuationCheckTickSystem extends EntityTickingSystem<EntityStore> {

    private final BackendAfkContinuationCheckService backendAfkContinuationCheckService;
    private final Consumer<NexoriAfkContinuationDecision> cancelDecisionConsumer;

    public BackendAfkContinuationCheckTickSystem(@Nonnull BackendAfkContinuationCheckService backendAfkContinuationCheckService) {
        this(backendAfkContinuationCheckService, ignored -> {
        });
    }

    public BackendAfkContinuationCheckTickSystem(
        @Nonnull BackendAfkContinuationCheckService backendAfkContinuationCheckService,
        @Nonnull Consumer<NexoriAfkContinuationDecision> cancelDecisionConsumer
    ) {
        this.backendAfkContinuationCheckService = backendAfkContinuationCheckService;
        this.cancelDecisionConsumer = cancelDecisionConsumer;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void tick(
        float delta,
        int entityId,
        @Nonnull ArchetypeChunk<EntityStore> chunk,
        @Nonnull Store<EntityStore> store,
        @Nonnull CommandBuffer<EntityStore> commandBuffer
    ) {
        Ref<EntityStore> ref = chunk.getReferenceTo(entityId);
        if (ref == null) {
            return;
        }
        List<NexoriAfkContinuationDecision> cancelDecisions = backendAfkContinuationCheckService.handleTick(System.currentTimeMillis());
        for (NexoriAfkContinuationDecision decision : cancelDecisions) {
            cancelDecisionConsumer.accept(decision);
        }
    }
}
