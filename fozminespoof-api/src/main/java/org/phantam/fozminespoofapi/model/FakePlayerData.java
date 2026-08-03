package org.phantam.fozminespoofapi.model;

import org.bukkit.Location;

import java.util.Objects;
import java.util.UUID;

/**
 * Immutable data object representing a fake player (NPC) persisted in database.
 * <p>
 * This class is designed to be thread-safe and provides a Builder for convenient creation.
 * All fields are final and cannot be modified after construction.
 */
public final class FakePlayerData {

    private final String name;
    private final UUID uuid;
    private final String worldName;
    private final double x;
    private final double y;
    private final double z;
    private final float yaw;
    private final float pitch;
    private final boolean active;

    private FakePlayerData(Builder builder) {
        this.name = builder.name;
        this.uuid = builder.uuid;
        this.worldName = builder.worldName;
        this.x = builder.x;
        this.y = builder.y;
        this.z = builder.z;
        this.yaw = builder.yaw;
        this.pitch = builder.pitch;
        this.active = builder.active;
    }

    public static Builder builder() {
        return new Builder();
    }

    public String getName() {
        return name;
    }

    public UUID getUuid() {
        return uuid;
    }

    public String getWorldName() {
        return worldName;
    }

    public double getX() {
        return x;
    }

    public double getY() {
        return y;
    }

    public double getZ() {
        return z;
    }

    public float getYaw() {
        return yaw;
    }

    public float getPitch() {
        return pitch;
    }

    public boolean isActive() {
        return active;
    }

    /**
     * Creates a new instance with the active flag toggled.
     * Useful for changing active state without modifying the original object.
     *
     * @param newActive new active state
     * @return a new FakePlayerData with updated active flag
     */
    public FakePlayerData withActive(boolean newActive) {
        return new Builder()
                .name(this.name)
                .uuid(this.uuid)
                .world(this.worldName)
                .location(this.x, this.y, this.z, this.yaw, this.pitch)
                .active(newActive)
                .build();
    }

    /**
     * Converts this data object to a Bukkit Location.
     * <p>
     * Note: This method does not validate if the world actually exists.
     *
     * @return a Location object based on stored coordinates
     */
    public Location toLocation() {
        // World lookup is deferred to caller to avoid dependency on Bukkit world manager.
        return new Location(null, x, y, z, yaw, pitch);
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (!(o instanceof FakePlayerData)) return false;
        FakePlayerData that = (FakePlayerData) o;
        return Objects.equals(uuid, that.uuid) && Objects.equals(name, that.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name, uuid);
    }

    @Override
    public String toString() {
        return "FakePlayerData{" +
                "name='" + name + '\'' +
                ", uuid=" + uuid +
                ", worldName='" + worldName + '\'' +
                ", active=" + active +
                '}';
    }

    /**
     * Builder pattern for constructing immutable FakePlayerData instances.
     */
    public static final class Builder {
        private String name;
        private UUID uuid;
        private String worldName;
        private double x;
        private double y;
        private double z;
        private float yaw;
        private float pitch;
        private boolean active = true;

        public Builder() {
        }

        public Builder name(String name) {
            this.name = name;
            return this;
        }

        public Builder uuid(UUID uuid) {
            this.uuid = uuid;
            return this;
        }

        public Builder world(String worldName) {
            this.worldName = worldName;
            return this;
        }

        public Builder location(double x, double y, double z, float yaw, float pitch) {
            this.x = x;
            this.y = y;
            this.z = z;
            this.yaw = yaw;
            this.pitch = pitch;
            return this;
        }

        public Builder location(Location loc) {
            if (loc != null) {
                this.x = loc.getX();
                this.y = loc.getY();
                this.z = loc.getZ();
                this.yaw = loc.getYaw();
                this.pitch = loc.getPitch();
                if (loc.getWorld() != null) {
                    this.worldName = loc.getWorld().getName();
                }
            }
            return this;
        }

        public Builder active(boolean active) {
            this.active = active;
            return this;
        }

        public FakePlayerData build() {
            // Basic validation to prevent incomplete data
            if (name == null || name.trim().isEmpty()) {
                throw new IllegalArgumentException("Name cannot be null or empty");
            }
            if (uuid == null) {
                throw new IllegalArgumentException("UUID cannot be null");
            }
            if (worldName == null || worldName.trim().isEmpty()) {
                throw new IllegalArgumentException("World name cannot be null or empty");
            }
            return new FakePlayerData(this);
        }
    }
}