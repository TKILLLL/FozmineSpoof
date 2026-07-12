package org.phantam.fozminesproofApi.database;

import java.util.Collection;
import java.util.Optional;

public interface IFakePlayerDatabase {
    void setup();
    void close();
    void saveFakePlayer(FakePlayerData data);
    Optional<FakePlayerData> loadFakePlayer(String name);
    Collection<FakePlayerData> loadAllPlayers();
    void deleteFakePlayer(String name);
}
