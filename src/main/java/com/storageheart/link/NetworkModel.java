package com.storageheart.link;

import com.hypixel.hytale.component.Ref;
import com.hypixel.hytale.component.Store;
import com.hypixel.hytale.server.core.inventory.InventoryComponent;
import com.hypixel.hytale.server.core.inventory.ItemStack;
import com.hypixel.hytale.server.core.inventory.container.SimpleItemContainer;
import com.hypixel.hytale.server.core.universe.world.World;
import com.hypixel.hytale.server.core.universe.world.storage.EntityStore;
import com.storageheart.StorageHeartPlugin;
import com.storageheart.core.BlockPos;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.ArrayList;
import java.util.Collections;
import java.util.IdentityHashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.function.Predicate;

/**
 * Operaciones agregadas sobre los cofres reales de una red (la página custom
 * escribe en los cofres en vivo, sin contenedor virtual intermedio).
 *
 * <p>Las pilas se agrupan por VARIANTE (id + durabilidad + calidad + metadata),
 * no solo por id: así una espada dañada no se mezcla con una nueva y al
 * retirar se devuelve la pila real con todos sus datos.
 */
public final class NetworkModel {

    private NetworkModel() {
    }

    /** Una fila de la interfaz: variante concreta, cantidad total y pila de muestra. */
    public record Entry(String itemId, int quantity, String variantId, ItemStack sample) {
        public Entry {
            Objects.requireNonNull(itemId, "itemId");
            Objects.requireNonNull(variantId, "variantId");
            Objects.requireNonNull(sample, "sample");
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity debe ser > 0");
            }
        }
    }

    /** Resultado de retirar: cantidad y pila real (con metadata/durabilidad). */
    public record WithdrawResult(int taken, ItemStack stack) {
    }

    private static final class Variant {
        final String itemId;
        final String variantId;
        final ItemStack sample;
        int quantity;

        Variant(String itemId, String variantId, ItemStack sample, int quantity) {
            this.itemId = itemId;
            this.variantId = variantId;
            this.sample = sample;
            this.quantity = quantity;
        }
    }

    public static List<SimpleItemContainer> chests(StorageHeartPlugin plugin, World world, BlockPos heart) {
        List<SimpleItemContainer> out = new ArrayList<>();
        Set<SimpleItemContainer> seen = Collections.newSetFromMap(new IdentityHashMap<>());
        for (BlockPos pos : plugin.linkService().links().chestsOf(heart)) {
            // Nunca tratar un Corazón como cofre (datos viejos de self-link).
            if (plugin.linkService().links().knowsHeart(pos)) {
                continue;
            }
            SimpleItemContainer c = LinkService.chestAt(world, pos.x(), pos.y(), pos.z());
            if (c != null && seen.add(c)) {
                out.add(c);
            }
        }
        return out;
    }

    public static List<Entry> entries(StorageHeartPlugin plugin, World world, BlockPos heart) {
        Map<String, Variant> variants = new LinkedHashMap<>();
        for (SimpleItemContainer c : chests(plugin, world, heart)) {
            c.forEach((slot, stack) -> {
                if (!ItemStack.isEmpty(stack)) {
                    addVariant(variants, stack);
                }
            });
        }
        return toEntries(variants);
    }

    /** Agrupa una lista de pilas (p. ej. el inventario del jugador) por variante. */
    public static List<Entry> entriesOf(List<ItemStack> stacks) {
        Map<String, Variant> variants = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (!ItemStack.isEmpty(stack)) {
                addVariant(variants, stack);
            }
        }
        return toEntries(variants);
    }

    /** Agrupa solo las pilas que pasan el filtro (p. ej. sin llave/corazón). */
    public static List<Entry> entriesOf(List<ItemStack> stacks, Predicate<ItemStack> include) {
        Map<String, Variant> variants = new LinkedHashMap<>();
        for (ItemStack stack : stacks) {
            if (!ItemStack.isEmpty(stack) && include.test(stack)) {
                addVariant(variants, stack);
            }
        }
        return toEntries(variants);
    }

    private static void addVariant(Map<String, Variant> variants, ItemStack stack) {
        String id = variantId(stack);
        Variant v = variants.get(id);
        if (v == null) {
            variants.put(id, new Variant(stack.getItemId(), id,
                    stack.cleanCopy().withQuantity(1), stack.getQuantity()));
        } else {
            v.quantity += stack.getQuantity();
        }
    }

    private static List<Entry> toEntries(Map<String, Variant> variants) {
        List<Entry> out = new ArrayList<>(variants.size());
        for (Variant v : variants.values()) {
            out.add(new Entry(v.itemId, v.quantity, v.variantId, v.sample));
        }
        return out;
    }

    /**
     * Identidad estable de una variante. Incluye durabilidad, calidad y
     * metadata; dos pilas con la misma clave son apilables entre sí.
     */
    @SuppressWarnings("deprecation")
    public static String variantId(ItemStack stack) {
        String meta = "";
        try {
            var metadata = stack.getMetadata();
            if (metadata != null) {
                meta = metadata.toJson();
            }
        } catch (Exception ignored) {
            // Metadata rara: se agrupa por los campos básicos.
        }
        String key = stack.getItemId()
                + '|' + Double.doubleToLongBits(stack.getDurability())
                + '|' + Double.doubleToLongBits(stack.getMaxDurability())
                + '|' + stack.getQualityIndex()
                + '|' + meta;
        return sha256(key);
    }

    private static String sha256(String value) {
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] hash = md.digest(value.getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder(24);
            for (int i = 0; i < 12; i++) {
                sb.append(Character.forDigit((hash[i] >> 4) & 0xF, 16));
                sb.append(Character.forDigit(hash[i] & 0xF, 16));
            }
            return sb.toString();
        } catch (Exception e) {
            return Integer.toHexString(value.hashCode());
        }
    }

    /**
     * Retira hasta {@code qty} de una variante concreta. La pila devuelta
     * conserva metadata, durabilidad y calidad.
     */
    public static WithdrawResult withdrawVariant(StorageHeartPlugin plugin, World world, BlockPos heart,
            String variantId, int qty) {
        if (variantId == null || qty <= 0) {
            return new WithdrawResult(0, null);
        }
        int left = qty;
        int taken = 0;
        ItemStack sample = null;
        for (SimpleItemContainer c : chests(plugin, world, heart)) {
            for (short slot = 0; slot < c.getCapacity() && left > 0; slot++) {
                ItemStack stack = c.getItemStack(slot);
                if (ItemStack.isEmpty(stack) || !variantId.equals(variantId(stack))) {
                    continue;
                }
                int take = Math.min(left, stack.getQuantity());
                if (take <= 0) {
                    continue;
                }
                if (sample == null) {
                    sample = stack.cleanCopy().withQuantity(1);
                }
                c.removeItemStackFromSlot(slot, take);
                left -= take;
                taken += take;
            }
            if (left <= 0) {
                break;
            }
        }
        return new WithdrawResult(taken, sample == null ? null : sample.withQuantity(taken));
    }

    /** Mete una pila suelta en los cofres. Devuelve el remanente (vacío = todo cupo). */
    public static ItemStack depositStack(StorageHeartPlugin plugin, World world, BlockPos heart, ItemStack stack) {
        if (ItemStack.isEmpty(stack)) {
            return stack;
        }
        ItemStack rest = stack;
        for (SimpleItemContainer c : chests(plugin, world, heart)) {
            if (ItemStack.isEmpty(rest)) {
                break;
            }
            rest = c.addItemStack(rest).getRemainder();
        }
        return rest;
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
        for (Entry e : entries(plugin, world, heart)) {
            known.add(e.itemId());
        }
        if (known.isEmpty()) {
            return 0;
        }
        return depositFiltered(plugin, world, heart, ref, store, known);
    }

    /**
     * Depósito por hueco: coloca cada pila y descuenta de ese mismo hueco lo
     * colocado. Ámbito: almacén+mochila (la hotbar NO entra en todo/repetidos,
     * a propósito). La llave y el bloque Corazón nunca se depositan solos
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
            // Nunca auto-depositar la llave ni el Corazón, ni siquiera en
            // "repetidos" (solo "mano" lo permite a propósito).
            if (keyId.equals(stack.getItemId()) || heartId.equals(stack.getItemId())) {
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
     * pila y pulsas el botón). Usa el slot activo real de la hotbar, sin
     * comparar metadata a mano.
     */
    public static int depositHeld(StorageHeartPlugin plugin, World world, BlockPos heart,
            Ref<EntityStore> ref, Store<EntityStore> store) {
        InventoryComponent.Hotbar hotbar = store.getComponent(ref, InventoryComponent.Hotbar.getComponentType());
        if (hotbar == null) {
            return 0;
        }
        short slot = hotbar.getActiveSlot();
        ItemStack hand = hotbar.getInventory().getItemStack(slot);
        if (ItemStack.isEmpty(hand)) {
            return 0;
        }
        List<SimpleItemContainer> chests = chests(plugin, world, heart);
        if (chests.isEmpty()) {
            return 0;
        }
        ItemStack rest = hand.cleanCopy();
        for (SimpleItemContainer c : chests) {
            if (ItemStack.isEmpty(rest)) {
                break;
            }
            rest = c.addItemStack(rest).getRemainder();
        }
        int placed = hand.getQuantity() - (ItemStack.isEmpty(rest) ? 0 : rest.getQuantity());
        if (placed > 0) {
            hotbar.getInventory().removeItemStackFromSlot(slot, placed);
        }
        return placed;
    }
}
