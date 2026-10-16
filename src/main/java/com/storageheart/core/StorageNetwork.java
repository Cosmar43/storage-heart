package com.storageheart.core;

import java.util.ArrayList;
import java.util.Comparator;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;

/**
 * Red de almacenamiento central, inspirada en el Storage Heart de Magic Storage.
 *
 * <p>El Heart no guarda nada por si mismo: agrega la capacidad de N unidades
 * (cofres) y expone depositar / buscar / ordenar / retirar desde un unico punto.
 *
 * <p>Esta clase es Java puro, sin dependencias de Hytale, para que la logica
 * se pueda probar sin servidor. El adaptador Hytale convierte ItemStack &lt;-&gt; Entry.
 */
public final class StorageNetwork {

    /** Una pila simplificada: id + cantidad. */
    public record Entry(String itemId, int quantity) {
        public Entry {
            Objects.requireNonNull(itemId, "itemId");
            if (quantity <= 0) {
                throw new IllegalArgumentException("quantity debe ser > 0");
            }
        }
    }

    private final Map<String, Integer> totals = new LinkedHashMap<>();
    private final int slotsPerUnit;
    private final int unitCount;

    public StorageNetwork(int unitCount, int slotsPerUnit) {
        if (unitCount < 0 || slotsPerUnit <= 0) {
            throw new IllegalArgumentException("unidades/slots invalidos");
        }
        this.unitCount = unitCount;
        this.slotsPerUnit = slotsPerUnit;
    }

    /** Capacidad en pilas distintas (como las 40 de la Storage Unit base). */
    public int capacityStacks() {
        return unitCount * slotsPerUnit;
    }

    public int usedStacks() {
        return totals.size();
    }

    public boolean isFullForNewType(String itemId) {
        return !totals.containsKey(itemId) && usedStacks() >= capacityStacks();
    }

    /**
     * Deposita pilas. Devuelve lo que NO cupo (lista vacia = todo guardado).
     * Los tipos nuevos respetan el limite de huecos; los existentes siempre apilan.
     */
    public List<Entry> depositAll(List<Entry> incoming) {
        List<Entry> remainder = new ArrayList<>();
        for (Entry e : incoming) {
            if (isFullForNewType(e.itemId())) {
                remainder.add(e);
                continue;
            }
            totals.merge(e.itemId(), e.quantity(), Integer::sum);
        }
        return remainder;
    }

    /** Retira hasta quantity del item. Devuelve lo realmente retirado. */
    public int withdraw(String itemId, int quantity) {
        int available = totals.getOrDefault(itemId, 0);
        int taken = Math.min(available, quantity);
        if (taken <= 0) {
            return 0;
        }
        int left = available - taken;
        if (left == 0) {
            totals.remove(itemId);
        } else {
            totals.put(itemId, left);
        }
        return taken;
    }

    /** Busca por nombre o por @mod (prefijo antes de ':'). */
    public List<Entry> search(String query) {
        String q = query == null ? "" : query.trim().toLowerCase(Locale.ROOT);
        return totals.entrySet().stream()
                .filter(e -> q.isEmpty()
                        || e.getKey().toLowerCase(Locale.ROOT).contains(q)
                        || matchesModFilter(e.getKey(), q))
                .map(e -> new Entry(e.getKey(), e.getValue()))
                .sorted(Comparator.comparing(Entry::itemId))
                .toList();
    }

    private static boolean matchesModFilter(String itemId, String q) {
        if (!q.startsWith("@")) {
            return false;
        }
        String mod = q.substring(1);
        int colon = itemId.indexOf(':');
        String namespace = colon >= 0 ? itemId.substring(0, colon) : itemId;
        return namespace.toLowerCase(Locale.ROOT).contains(mod);
    }

    /** Vista ordenada: alfabetico o por cantidad. */
    public List<Entry> sorted(String mode) {
        Comparator<Map.Entry<String, Integer>> cmp = "cantidad".equalsIgnoreCase(mode)
                ? Map.Entry.<String, Integer>comparingByValue(Comparator.reverseOrder())
                : Map.Entry.comparingByKey();
        return totals.entrySet().stream()
                .sorted(cmp)
                .map(e -> new Entry(e.getKey(), e.getValue()))
                .toList();
    }

    public Map<String, Integer> snapshot() {
        return Map.copyOf(totals);
    }

    public void restore(Map<String, Integer> data) {
        totals.clear();
        if (data != null) {
            data.forEach((k, v) -> {
                if (k != null && v != null && v > 0) {
                    totals.put(k, v);
                }
            });
        }
    }
}
