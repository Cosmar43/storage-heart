package com.storageheart.core;

import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * Vínculos corazón → cofres. Java puro, sin dependencias de Hytale.
 *
 * <p>Formato de persistencia (JSON manual, simétrico escritura/lectura):
 * {@code {"hearts":[{"world":"w","x":1,"y":2,"z":3,"chests":[{"x":..,"y":..,"z":..}]}]}}
 */
public final class LinkStore {

    private final Map<String, BlockPos> hearts = new LinkedHashMap<>();
    private final Map<String, Set<BlockPos>> links = new LinkedHashMap<>();

    private static String keyOf(BlockPos heart) {
        return heart.key();
    }

    public void registerHeart(BlockPos heart) {
        hearts.putIfAbsent(keyOf(heart), heart);
        links.computeIfAbsent(keyOf(heart), k -> new LinkedHashSet<>());
    }

    public boolean link(BlockPos heart, BlockPos chest) {
        registerHeart(heart);
        return links.get(keyOf(heart)).add(chest);
    }

    /** Devuelve true si existía y se quitó. */
    public boolean unlink(BlockPos heart, BlockPos chest) {
        Set<BlockPos> set = links.get(keyOf(heart));
        return set != null && set.remove(chest);
    }

    public boolean isLinked(BlockPos heart, BlockPos chest) {
        Set<BlockPos> set = links.get(keyOf(heart));
        return set != null && set.contains(chest);
    }

    public Set<BlockPos> chestsOf(BlockPos heart) {
        Set<BlockPos> set = links.get(keyOf(heart));
        return set == null ? Set.of() : Collections.unmodifiableSet(set);
    }

    public void dropHeart(BlockPos heart) {
        hearts.remove(keyOf(heart));
        links.remove(keyOf(heart));
    }

    public boolean knowsHeart(BlockPos heart) {
        return hearts.containsKey(keyOf(heart));
    }

    public List<BlockPos> heartsIn(String world) {
        List<BlockPos> out = new ArrayList<>();
        for (BlockPos h : hearts.values()) {
            if (h.world().equals(world)) {
                out.add(h);
            }
        }
        return out;
    }

    public String toJson() {
        StringBuilder sb = new StringBuilder("{\"hearts\":[");
        boolean first = true;
        for (BlockPos heart : hearts.values()) {
            if (!first) {
                sb.append(',');
            }
            first = false;
            sb.append("{\"world\":").append(quote(heart.world()))
                    .append(",\"x\":").append(heart.x())
                    .append(",\"y\":").append(heart.y())
                    .append(",\"z\":").append(heart.z())
                    .append(",\"chests\":[");
            boolean fc = true;
            for (BlockPos c : chestsOf(heart)) {
                if (!fc) {
                    sb.append(',');
                }
                fc = false;
                sb.append("{\"x\":").append(c.x())
                        .append(",\"y\":").append(c.y())
                        .append(",\"z\":").append(c.z()).append('}');
            }
            sb.append("]}");
        }
        return sb.append("]}").toString();
    }

    /** Lee lo que escribe {@link #toJson}. Entradas malformadas se saltan. */
    public void fromJson(String json) {
        hearts.clear();
        links.clear();
        if (json == null) {
            return;
        }
        for (String h : splitObjects(extractArray(json, "\"hearts\""))) {
            String world = extractString(h, "\"world\"");
            Integer x = extractInt(h, "\"x\"");
            Integer y = extractInt(h, "\"y\"");
            Integer z = extractInt(h, "\"z\"");
            if (world == null || x == null || y == null || z == null) {
                continue;
            }
            BlockPos heart = new BlockPos(world, x, y, z);
            registerHeart(heart);
            for (String c : splitObjects(extractArray(h, "\"chests\""))) {
                Integer cx = extractInt(c, "\"x\"");
                Integer cy = extractInt(c, "\"y\"");
                Integer cz = extractInt(c, "\"z\"");
                if (cx == null || cy == null || cz == null) {
                    continue;
                }
                link(heart, new BlockPos(world, cx, cy, cz));
            }
        }
    }

    private static String quote(String s) {
        return '"' + s.replace("\\", "\\\\").replace("\"", "\\\"") + '"';
    }

    private static String extractArray(String json, String key) {
        int k = json.indexOf(key);
        if (k < 0) {
            return "";
        }
        int start = json.indexOf('[', k);
        if (start < 0) {
            return "";
        }
        int depth = 0;
        for (int i = start; i < json.length(); i++) {
            char c = json.charAt(i);
            if (c == '[') {
                depth++;
            } else if (c == ']') {
                depth--;
                if (depth == 0) {
                    return json.substring(start + 1, i);
                }
            }
        }
        return "";
    }

    private static List<String> splitObjects(String arrayBody) {
        List<String> out = new ArrayList<>();
        int depth = 0;
        int start = -1;
        for (int i = 0; i < arrayBody.length(); i++) {
            char c = arrayBody.charAt(i);
            if (c == '{') {
                if (depth == 0) {
                    start = i;
                }
                depth++;
            } else if (c == '}') {
                depth--;
                if (depth == 0 && start >= 0) {
                    out.add(arrayBody.substring(start, i + 1));
                    start = -1;
                }
            }
        }
        return out;
    }

    private static String extractString(String obj, String key) {
        int k = obj.indexOf(key);
        if (k < 0) {
            return null;
        }
        int colon = obj.indexOf(':', k);
        int q1 = obj.indexOf('"', colon);
        int q2 = obj.indexOf('"', q1 + 1);
        if (q1 < 0 || q2 < 0) {
            return null;
        }
        return obj.substring(q1 + 1, q2).replace("\\\"", "\"").replace("\\\\", "\\");
    }

    private static Integer extractInt(String obj, String key) {
        int k = obj.indexOf(key);
        if (k < 0) {
            return null;
        }
        int colon = obj.indexOf(':', k);
        int i = colon + 1;
        while (i < obj.length() && obj.charAt(i) == ' ') {
            i++;
        }
        int j = i;
        while (j < obj.length() && (Character.isDigit(obj.charAt(j)) || obj.charAt(j) == '-')) {
            j++;
        }
        if (j == i) {
            return null;
        }
        try {
            return Integer.parseInt(obj.substring(i, j));
        } catch (NumberFormatException e) {
            return null;
        }
    }
}
