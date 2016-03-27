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
}
