package com.storageheart.core;

import java.util.Objects;

/** Posición de bloque en un mundo. Clave de corazones y cofres vinculados. */
public record BlockPos(String world, int x, int y, int z) {
    public BlockPos {
        Objects.requireNonNull(world, "world");
    }

    public String key() {
        return world + "|" + x + "," + y + "," + z;
    }

    public double distanceTo(BlockPos other) {
        if (!world.equals(other.world)) {
            return Double.POSITIVE_INFINITY;
        }
        double dx = x - other.x;
        double dy = y - other.y;
        double dz = z - other.z;
        return Math.sqrt(dx * dx + dy * dy + dz * dz);
    }
}
