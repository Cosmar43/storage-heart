package com.storageheart;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.entity.entities.Player;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.ItemContainer;
import com.hypixel.hytale.server.core.inventory.container.SortType;
import com.hypixel.hytale.server.core.inventory.transaction.ItemStackTransaction;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.storageheart.core.StorageNetwork;

import java.util.ArrayList;
import java.util.List;

/**
 * Adaptador entre la API de Hytale (server 0.7.2) y el nucleo puro.
 *
 * <p>Usa la API vigente, sin deprecados: secciones de inventario como
 * componentes ({@code InventoryComponent.Storage}), orden nativo
 * ({@code ItemContainer.sortItems}) y entrega ({@code Player.giveItem}).
 * Si un futuro update rompe algo, solo se toca esta clase.
 */
public final class StorageNetworkService {

    private static final int SLOTS_PER_UNIT = 40;

    private final StorageNetwork network;

    public StorageNetworkService(int unitCount) {
        this.network = new StorageNetwork(unitCount, SLOTS_PER_UNIT);
    }

    public StorageNetwork network() {
        return network;
    }

    /** Contenedor de la seccion Storage (36 slots) del jugador. */
    public static ItemContainer storageOf(Store<EntityStore> store, Ref<EntityStore> ref) {
        InventoryComponent.Storage comp =
                store.getComponent(ref, InventoryComponent.Storage.getComponentType());
        return comp.getInventory();
    }

    /** Deposita todo el Storage del jugador en la red. */
    public DepositResult depositAll(Store<EntityStore> store, Ref<EntityStore> ref) {
        ItemContainer source = storageOf(store, ref);
        List<StorageNetwork.Entry> incoming = new ArrayList<>();
        source.forEach((slot, stack) -> {
            if (!ItemStack.isEmpty(stack)) {
                incoming.add(new StorageNetwork.Entry(stack.getItemId(), stack.getQuantity()));
            }
        });

        List<StorageNetwork.Entry> remainder = network.depositAll(incoming);
        int moved = incoming.stream().mapToInt(StorageNetwork.Entry::quantity).sum()
                - remainder.stream().mapToInt(StorageNetwork.Entry::quantity).sum();
        drainUpTo(source, moved);
        return new DepositResult(moved, remainder);
    }

    public record DepositResult(int moved, List<StorageNetwork.Entry> remainder) {
    }

    private static void drainUpTo(ItemContainer source, int quantity) {
        int left = quantity;
        for (short slot = 0; slot < source.getCapacity() && left > 0; slot++) {
            ItemStack stack = source.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            int take = Math.min(left, stack.getQuantity());
            source.removeItemStackFromSlot(slot, take);
            left -= take;
        }
    }

    /** Entrega items al jugador (hotbar primero). Devuelve lo que no cupo. */
    public static ItemStack give(ItemStack stack, Ref<EntityStore> ref, Store<EntityStore> store) {
        ItemStackTransaction tx = Player.giveItem(stack, ref, store);
        return tx.getRemainder();
    }

    /** Ordena el Storage del jugador con el comparador nativo del servidor. */
    public static void sortStorage(Store<EntityStore> store, Ref<EntityStore> ref, SortType type) {
        storageOf(store, ref).sortItems(type);
    }

    /** Crea pilas de Hytale para entregar al jugador al retirar. */
    public static ItemStack toStack(String itemId, int quantity) {
        return new ItemStack(itemId, quantity);
    }
}
