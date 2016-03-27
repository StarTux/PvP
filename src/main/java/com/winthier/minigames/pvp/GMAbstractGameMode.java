package com.winthier.minigames.pvp;

import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;

abstract class GMAbstractGameMode implements GMGameMode {
    @Override
    public String getName() {
        return getClass().getSimpleName();
    }
    
    @Override
    public int winAdvantage() {
        return 10;
    }
    
    @Override
    public void tickArena(long ticks) {}

    @Override
    public void load() {}

    @Override
    public void onPlayerDamagePlayer(ArenaPlayer ap, ArenaPlayer victim, EntityDamageByEntityEvent event) {}

    @Override
    public void onPlayerKillPlayer(ArenaPlayer ap, ArenaPlayer victim, PlayerDeathEvent event) {}

    @Override
    public void onPlayerSpawn(ArenaPlayer ap) {}
}
