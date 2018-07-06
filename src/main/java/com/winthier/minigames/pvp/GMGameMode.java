package com.winthier.minigames.pvp;

import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

interface GMGameMode extends Listener {
    String getName();
    int winAdvantage();
    void tickArena(long ticks);
    void load();
    void onPlayerDamagePlayer(ArenaPlayer ap, ArenaPlayer victim, EntityDamageByEntityEvent event);
    void onPlayerKillPlayer(ArenaPlayer ap, ArenaPlayer victim, PlayerDeathEvent event);
    void onPlayerSpawn(ArenaPlayer ap);

    enum Type {
        CHICKEN_HUNT,
        ONE_IN_THE_QUIVER,
        SNOWBALL_FIGHT;

        GMGameMode create(PvP pvp) {
            switch (this) {
            case CHICKEN_HUNT: return new GMChickenHunt(pvp);
            case ONE_IN_THE_QUIVER: return new GMOneInTheQuiver(pvp);
            case SNOWBALL_FIGHT: return new GMSnowballFight(pvp);
            default: return new GMOneInTheQuiver(pvp);
            }
        }
    }
}
