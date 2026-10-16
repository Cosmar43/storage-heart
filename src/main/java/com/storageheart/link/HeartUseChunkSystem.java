package com.storageheart.link;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityHolderEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.storageheart.StorageHeartPlugin;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/** Ver {@link HeartUseEntitySystem}: gemelo de diagnóstico en ChunkStore. */
public final class HeartUseChunkSystem extends EntityHolderEventSystem<ChunkStore, UseBlockEvent> {

    private static final AtomicLong SEEN = new AtomicLong();
    private final StorageHeartPlugin plugin;

    public HeartUseChunkSystem(StorageHeartPlugin plugin) {
        super(UseBlockEvent.class);
        this.plugin = plugin;
    }

    @Override
    public Query<ChunkStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(Holder<ChunkStore> holder, Store<ChunkStore> store,
            CommandBuffer<ChunkStore> buffer, UseBlockEvent event) {
        long n = SEEN.incrementAndGet();
        if (n == 1) {
            plugin.getLogger().at(Level.INFO).log(
                    "StorageHeart: UseBlockEvent también llega por ChunkStore; la lógica corre en EntityStore.");
        }
    }
}
