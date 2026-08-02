package org.phantam.fozminespoofcore.database.executors;

import org.phantam.fozminespoofapi.action.IBotAction;
import org.phantam.fozminespoofapi.database.IFakePlayerDatabase;

/**
 * Permanently removes a bot from both the world and the database.
 */
public class RemoveBotAction implements IBotAction<String, Boolean> {

    private final IFakePlayerDatabase database;
    private final DespawnBotAction despawnAction;

    public RemoveBotAction(IFakePlayerDatabase database, DespawnBotAction despawnAction) {
        this.database = database;
        this.despawnAction = despawnAction;
    }

    @Override
    public Boolean execute(String name) {
        despawnAction.execute(name);
        database.deleteFakePlayer(name);
        return true;
    }
}