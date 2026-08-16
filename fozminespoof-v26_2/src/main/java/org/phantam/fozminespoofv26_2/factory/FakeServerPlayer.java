package org.phantam.fozminespoofv26_2.factory;

import com.mojang.authlib.GameProfile;
import net.minecraft.server.MinecraftServer;
import net.minecraft.server.level.ClientInformation;
import net.minecraft.server.level.ServerLevel;
import net.minecraft.server.level.ServerPlayer;

/**
 * Custom ServerPlayer implementation for simulated fake players in Minecraft 26.2.
 * Suppresses heavy tick logic to eliminate idle CPU consumption.
 */
public class FakeServerPlayer extends ServerPlayer {

    public FakeServerPlayer(MinecraftServer server, ServerLevel level, GameProfile profile, ClientInformation clientInfo) {
        super(server, level, profile, clientInfo);
        this.setInvulnerable(true);
    }

    @Override
    public void tick() {
        this.baseTick();
    }

    @Override
    public void doTick() {
        // No-op: suppress heavy entity ticking logic
    }

    @Override
    public boolean isSpectator() {
        return false;
    }

    @Override
    public boolean isCreative() {
        return true;
    }
}