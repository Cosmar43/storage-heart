package com.storageheart.link;

import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.storageheart.StorageHeartPlugin;
import com.storageheart.StorageNetworkService;
import com.storageheart.core.BlockPos;
import com.storageheart.core.StorageNetwork;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Operaciones agregadas sobre los cofres reales de una red (la página custom
 * escribe en los cofres en vivo, sin contenedor virtual intermedio).
 */
public final class NetworkModel {

    private NetworkModel() {
    }

    public static List<SimpleItemContainer> chests(StorageHeartPlugin plugin, World world, BlockPos heart) {
        List<SimpleItemContainer> out = new ArrayList<>();
        for (BlockPos pos : plugin.linkService().links().chestsOf(heart)) {
            SimpleItemContainer c = LinkService.chestAt(world, pos.x(), pos.y(), pos.z());
            if (c != null) {
                out.add(c);
            }
        }
        return out;
    }

    public static List<StorageNetwork.Entry> entries(StorageHeartPlugin plugin, World world, BlockPos heart) {
        Map<String, Integer> totals = new LinkedHashMap<>();
        for (SimpleItemContainer c : chests(plugin, world, heart)) {
            c.forEach((slot, stack) -> {
                if (!ItemStack.isEmpty(stack)) {
                    totals.merge(stack.getItemId(), stack.getQuantity(), Integer::sum);
                }
            });
        }
        List<StorageNetwork.Entry> out = new ArrayList<>();
        totals.forEach((id, qty) -> out.add(new StorageNetwork.Entry(id, qty)));
        return out;
    }

    public static int totalSlots(StorageHeartPlugin plugin, World world, BlockPos heart) {
        int total = 0;
        for (SimpleItemContainer c : chests(plugin, world, heart)) {
            total += c.getCapacity();
        }
        return total;
    }

    /** Quita hasta qty del item de los cofres. Devuelve lo retirado. */
    public static int withdraw(StorageHeartPlugin plugin, World world, BlockPos heart, String itemId, int qty) {
        int left = qty;
        int taken = 0;
        for (SimpleItemContainer c : chests(plugin, world, heart)) {
            for (short slot = 0; slot < c.getCapacity() && left > 0; slot++) {
                ItemStack stack = c.getItemStack(slot);
                if (ItemStack.isEmpty(stack) || !itemId.equals(stack.getItemId())) {
                    continue;
                }
                int take = Math.min(left, stack.getQuantity());
                c.removeItemStackFromSlot(slot, take);
                left -= take;
                taken += take;
            }
            if (left <= 0) {
                break;
            }
        }
        return taken;
    }

    /** Mete una entrada suelta en los cofres (para restos). */
    public static void depositEntry(StorageHeartPlugin plugin, World world, BlockPos heart, StorageNetwork.Entry e) {
        ItemStack rest = StorageNetworkService.toStack(e.itemId(), e.quantity());
        for (SimpleItemContainer c : chests(plugin, world, heart)) {
            if (ItemStack.isEmpty(rest)) {
                break;
            }
            rest = c.addItemStack(rest).getRemainder();
        }
    }

    /** Mueve todo el Storage del jugador a los cofres. Devuelve items movidos. */
    public static int depositAll(StorageHeartPlugin plugin, World world, BlockPos heart,
            Ref<EntityStore> ref, Store<EntityStore> store) {
        return depositFiltered(plugin, world, heart, ref, store, null);
    }

    /**
     * Mueve del Storage solo los tipos ya presentes en la red (quick-stack).
     * Devuelve items movidos.
     */
    public static int depositMatching(StorageHeartPlugin plugin, World world, BlockPos heart,
            Ref<EntityStore> ref, Store<EntityStore> store) {
        java.util.Set<String> known = new java.util.HashSet<>();
        for (StorageNetwork.Entry e : entries(plugin, world, heart)) {
            known.add(e.itemId());
        }
        if (known.isEmpty()) {
            return 0;
        }
        return depositFiltered(plugin, world, heart, ref, store, known);
    }

    /**
     * Llena el contenedor virtual 1:1 con las pilas de los cofres (sin
     * combinar: lo que ves es lo que hay). Devuelve pilas copiadas.
     */
    public static int fillVirtual(StorageHeartPlugin plugin, World world, BlockPos heart,
            SimpleItemContainer virtual) {
        int v = 0;
        for (SimpleItemContainer c : chests(plugin, world, heart)) {
            for (short s = 0; s < c.getCapacity() && v < virtual.getCapacity(); s++) {
                ItemStack stack = c.getItemStack(s);
                if (!ItemStack.isEmpty(stack)) {
                    virtual.setItemStackForSlot((short) v++, stack.cleanCopy());
                }
            }
        }
        return v;
    }

    /**
     * Devuelve lo editado en el virtual a los cofres (vacía y reparte).
     * Las pilas siempre caben: salieron de esos mismos huecos.
     */
    public static void syncBack(StorageHeartPlugin plugin, World world, BlockPos heart,
            SimpleItemContainer virtual) {
        List<ItemStack> stacks = new ArrayList<>();
        for (short s = 0; s < virtual.getCapacity(); s++) {
            ItemStack stack = virtual.getItemStack(s);
            if (!ItemStack.isEmpty(stack)) {
                stacks.add(stack.cleanCopy());
            }
        }
        List<SimpleItemContainer> chests = chests(plugin, world, heart);
        for (SimpleItemContainer c : chests) {
            c.clear();
        }
        int i = 0;
        int lost = 0;
        for (ItemStack stack : stacks) {
            ItemStack rest = stack;
            for (int k = 0; k < chests.size() && !ItemStack.isEmpty(rest); k++) {
                rest = chests.get((i + k) % chests.size()).addItemStack(rest).getRemainder();
            }
            if (!ItemStack.isEmpty(rest)) {
                lost += rest.getQuantity();
            }
            i++;
        }
        plugin.getLogger().at(java.util.logging.Level.INFO).log(
                "StorageHeart: ventana cerrada, " + stacks.size() + " pilas devueltas a "
                        + chests.size() + " cofres" + (lost > 0 ? " (PERDIDOS " + lost + ")" : "") + ".");
    }

    /**
     * Depósito por hueco: coloca cada pila y descuenta de ese mismo hueco lo
     * colocado (sin el drenaje secuencial global que confundía tipos).
     * Ámbito: almacén+mochila (la hotbar NO entra en todo/repetidos, a
     * propósito). La llave y el bloque Corazón nunca se depositan solos
     * (solo a propósito con "mano").
     */
    private static int depositFiltered(StorageHeartPlugin plugin, World world, BlockPos heart,
            Ref<EntityStore> ref, Store<EntityStore> store, java.util.Set<String> onlyIds) {
        List<SimpleItemContainer> chests = chests(plugin, world, heart);
        if (chests.isEmpty()) {
            return 0;
        }
        String keyId = plugin.getConfig().getLinkKeyItemId();
        String heartId = plugin.getConfig().getHeartBlockId();
        var source = com.hypixel.hytale.server.core.inventory.InventoryComponent.getCombined(
                store, ref,
                com.hypixel.hytale.server.core.inventory.InventoryComponent.Storage.getComponentType(),
                com.hypixel.hytale.server.core.inventory.InventoryComponent.Backpack.getComponentType());
        int moved = 0;
        int start = 0;
        for (short slot = 0; slot < source.getCapacity(); slot++) {
            ItemStack stack = source.getItemStack(slot);
            if (ItemStack.isEmpty(stack)) {
                continue;
            }
            if (onlyIds == null && (keyId.equals(stack.getItemId()) || heartId.equals(stack.getItemId()))) {
                continue;
            }
            if (onlyIds != null && !onlyIds.contains(stack.getItemId())) {
                continue;
            }
            ItemStack rest = stack.cleanCopy();
            for (int k = 0; k < chests.size() && !ItemStack.isEmpty(rest); k++) {
                rest = chests.get((start + k) % chests.size()).addItemStack(rest).getRemainder();
            }
            int placed = stack.getQuantity() - (ItemStack.isEmpty(rest) ? 0 : rest.getQuantity());
            if (placed > 0) {
                source.removeItemStackFromSlot(slot, placed);
                moved += placed;
            }
            start++;
        }
        return moved;
    }

    /**
     * Deposita la pila de la mano (flujo por item sin arrastrar: sujetas la
     * pila y pulsas el botón). Localiza su hueco por id+cantidad+metadatos.
     */
    public static int depositHeld(StorageHeartPlugin plugin, World world, BlockPos heart,
            Ref<EntityStore> ref, Store<EntityStore> store) {
        ItemStack hand = com.hypixel.hytale.server.core.inventory.InventoryComponent
                .getItemInHand(store, ref);
        if (ItemStack.isEmpty(hand)) {
            return 0;
        }
        List<SimpleItemContainer> chests = chests(plugin, world, heart);
        if (chests.isEmpty()) {
            return 0;
        }
        var source = com.hypixel.hytale.server.core.inventory.InventoryComponent.getCombined(
                store, ref, com.hypixel.hytale.server.core.inventory.InventoryComponent.HOTBAR_STORAGE_BACKPACK);
        short slot = -1;
        for (short s = 0; s < source.getCapacity(); s++) {
            ItemStack stack = source.getItemStack(s);
            if (ItemStack.isEmpty(stack) || !stack.getItemId().equals(hand.getItemId())
                    || stack.getQuantity() != hand.getQuantity()) {
                continue;
            }
            if (!java.util.Objects.equals(stack.getMetadata(), hand.getMetadata())) {
                continue;
            }
            slot = s;
            break;
        }
        if (slot < 0) {
            return 0;
        }
        ItemStack rest = hand.cleanCopy();
        for (int k = 0; k < chests.size() && !ItemStack.isEmpty(rest); k++) {
            rest = chests.get(k).addItemStack(rest).getRemainder();
        }
        int placed = hand.getQuantity() - (ItemStack.isEmpty(rest) ? 0 : rest.getQuantity());
        if (placed > 0) {
            source.removeItemStackFromSlot(slot, placed);
        }
        return placed;
    }
}
