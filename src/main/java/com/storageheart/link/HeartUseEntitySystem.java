package com.storageheart.link;

import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Holder;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityHolderEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.ChunkStore;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.storageheart.StorageHeartPlugin;

import java.util.concurrent.atomic.AtomicLong;
import java.util.logging.Level;

/**
 * Escucha global de clics en bloques vía sistemas ECS (sin keyed por mundo).
 *
 * <p>Se registra en ambos stores porque el motor no documenta cuál despacha
 * {@code UseBlockEvent}: la lógica corre en EntityStore; el de ChunkStore
 * solo cuenta eventos para diagnosticar en logs.
 */
public final class HeartUseEntitySystem extends EntityHolderEventSystem<EntityStore, UseBlockEvent> {

    private final StorageHeartPlugin plugin;

    public HeartUseEntitySystem(StorageHeartPlugin plugin) {
        super(UseBlockEvent.class);
        this.plugin = plugin;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(Holder<EntityStore> holder, Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer, UseBlockEvent event) {
        plugin.linkService().onUse(store, event);
    }
}
