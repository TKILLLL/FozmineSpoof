package org.phantam.fozminespoofv1_21_11.factory;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Custom ServerPlayer implementation for fake (NPC) players.
 * Overrides critical methods to eliminate heavy tick logic, making bots
 * completely passive and CPU-efficient while remaining visible to clients.
 */
public class FakeServerPlayer extends ServerPlayer {

    public FakeServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation clientInfo) {
        super(server, level, profile, clientInfo);
        // Make the bot invulnerable to all damage sources.
        // This is the recommended way in 1.21.4+ since hurt() is final.
        this.setInvulnerable(true);
    }

    /**
     * Suppresses the full player tick loop to save CPU.
     * Only the minimal base tick (e.g., void fall protection) is retained.
     */
    @Override
    public void tick() {
        // Skip all heavy logic: hunger, potion effects, movement updates, network sync.
        // Only keep the bare minimum to prevent entity removal when falling into the void.
        this.baseTick();
    }

    /**
     * Completely blocks the doTick() method which handles per-tick player state.
     */
    @Override
    public void doTick() {
        // No-op: prevents any tick-dependent player logic.
    }

    /**
     * Forces the bot to be treated as a non-spectator.
     * Ensures the 3D model is always rendered for all clients.
     *
     * @return false (not a spectator)
     */
    @Override
    public boolean isSpectator() {
        return false;
    }

    /**
     * Simulates creative mode to prevent mobs from targeting the bot.
     * In survival, mobs would normally attack; creative mode avoids this.
     *
     * @return true (creative mode)
     */
    @Override
    public boolean isCreative() {
        return true;
    }
}