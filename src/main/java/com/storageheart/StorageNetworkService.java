package com.storageheart;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;

/**
 * Adaptador mínimo de inventario del jugador (server 0.7.2).
 *
 * <p>La red es física (cofres reales): aquí solo queda la entrega de items.
 */
public final class StorageNetworkService {

    private StorageNetworkService() {
    }

    /** Entrega items al jugador (hotbar primero). Devuelve lo que no cupo. */
    public static ItemStack give(ItemStack stack, Ref<EntityStore> ref, Store<EntityStore> store) {
        ItemStackTransaction tx = Player.giveItem(stack, ref, store);
        return tx.getRemainder();
    }
}
