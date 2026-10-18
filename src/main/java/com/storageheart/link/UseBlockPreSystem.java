package com.storageheart.link;

import com.hypixel.hytale.component.ArchetypeChunk;
import com.hypixel.hytale.component.CommandBuffer;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.component.query.Query;
import com.hypixel.hytale.component.system.EntityEventSystem;
import com.hypixel.hytale.server.core.event.events.ecs.UseBlockEvent;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.storageheart.StorageHeartPlugin;

import java.util.logging.Level;

/**
 * Cancela el uso vanilla del bloque ANTES de que el motor ejecute
 * {@code Open_Container}. Sin esta cancelación la ventana del cofre normal
 * aparece y hay que cerrarla con retardo (parpadeo).
 *
 * <p>El sistema anterior usaba {@code EntityHolderEventSystem} con el tipo
 * abstracto {@code UseBlockEvent}; por eso nunca recibía nada. El evento real
 * es {@code UseBlockEvent.Pre} y se despacha por entidad, como hace el
 * sistema vanilla {@code BlackboardSystems$UseBlockEventSystem} con
 * {@code UseBlockEvent$Post}.
 */
public final class UseBlockPreSystem extends EntityEventSystem<EntityStore, UseBlockEvent.Pre> {

    private final StorageHeartPlugin plugin;

    public UseBlockPreSystem(StorageHeartPlugin plugin) {
        super(UseBlockEvent.Pre.class);
        this.plugin = plugin;
    }

    @Override
    public Query<EntityStore> getQuery() {
        return Query.any();
    }

    @Override
    public void handle(int index, ArchetypeChunk<EntityStore> chunk, Store<EntityStore> store,
            CommandBuffer<EntityStore> buffer, UseBlockEvent.Pre event) {
        try {
            if (plugin.linkService() != null && plugin.linkService().handlePreUse(event)) {
                event.setCancelled(true);
            }
        } catch (Exception e) {
            // Nunca tumbar el tick del mundo por un fallo del mod: si algo
            // falla, el uso vanilla sigue su curso.
            plugin.getLogger().at(Level.WARNING).withCause(e).log(
                    "StorageHeart: UseBlockEvent.Pre falló; se deja pasar el uso vanilla.");
        }
    }
}
