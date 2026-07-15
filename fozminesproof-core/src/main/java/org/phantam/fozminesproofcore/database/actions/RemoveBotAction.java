package org.phantam.fozminesproofcore.database.actions;

import org.phantam.fozminesproofapi.database.IFakePlayerDatabase;

public class RemoveBotAction implements org.phantam.fozminesproofapi.action.IBotAction<String, Boolean> {
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
