package com.dungeonapi;

public class DungeonConfig {

    private final String id;
    private final String schematic;
    private final String world;
    private final int x;
    private final int y;
    private final int z;

    public DungeonConfig(
            String id,
            String schematic,
            String world,
            int x,
            int y,
            int z
    ) {
        this.id = id;
        this.schematic = schematic;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
    }

    public String getId() {
        return id;
    }

    public String getSchematic() {
        return schematic;
    }

    public String getWorld() {
        return world;
    }

    public int getX() {
        return x;
    }

    public int getY() {
        return y;
    }

    public int getZ() {
        return z;
    }
}