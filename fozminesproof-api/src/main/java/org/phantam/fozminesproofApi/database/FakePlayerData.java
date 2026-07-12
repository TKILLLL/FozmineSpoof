package org.phantam.fozminesproofApi.database;

import java.util.UUID;

public class FakePlayerData {
    private final String name;
    private final UUID uuid;
    private final String world;
    private final double x, y, z;
    private final float yaw, pitch;
    private boolean isActive;

    public FakePlayerData(String name, UUID uuid, String world, double x, double y, double z, float yaw, float pitch, boolean isActive) {
        this.name = name;
        this.uuid = uuid;
        this.world = world;
        this.x = x;
        this.y = y;
        this.z = z;
        this.yaw = yaw;
        this.pitch = pitch;
        this.isActive = isActive;
    }

    public String getName() { return name; }
    public UUID getUuid() { return uuid; }
    public String getWorld() { return world; }
    public double getX() { return x; }
    public double getY() { return y; }
    public double getZ() { return z; }
    public float getYaw() { return yaw; }
    public float getPitch() { return pitch; }
    public boolean isActive() { return isActive; }
    public void setActive(boolean active) { this.isActive = active; }
}
