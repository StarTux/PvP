package com.winthier.minigames.pvp;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import org.bukkit.Material;
import org.bukkit.Particle;
import org.bukkit.configuration.file.YamlConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.entity.ProjectileLaunchEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.scheduler.BukkitRunnable;

public class GMOneInTheQuiver extends GMAbstractGameMode {
    final PvP game;
    final List<ItemStack> starterKit = new ArrayList<>();

    @Override
    public String getName() { return "One in the Quiver"; }

    GMOneInTheQuiver(PvP game) {
        this.game = game;
    }

    @Override
    public void tickArena(long ticks) {
        if (ticks == 0) {
        }
    }

    @Override
    public void load() {
        YamlConfiguration config = YamlConfiguration.loadConfiguration(new File(game.getDataFolder(), "OneInTheQuiver.yml"));
        for (Object o: config.getList("start")) {
            if (o instanceof ItemStack) starterKit.add((ItemStack)o);
        }
    }

    ItemStack arrow(int amount) {
        return new ItemStack(Material.ARROW, amount);
    }

    @Override
    public void onPlayerDamagePlayer(ArenaPlayer ap, ArenaPlayer victim, EntityDamageByEntityEvent event) {
        if (ap == victim) return;
        if (event.getDamager().getType() != EntityType.ARROW) return;
        event.setDamage(100.0);
    }

    @Override
    public void onPlayerKillPlayer(ArenaPlayer ap, ArenaPlayer victim, PlayerDeathEvent event) {
        game.give(ap.getPlayer(), arrow(1));
    }

    @Override
    public void onPlayerSpawn(ArenaPlayer ap) {
        final Player player = ap.getPlayer();
        if (player == null) return;
        for (ItemStack item: starterKit) game.give(player, item);
    }

    @EventHandler
    public void onProjectileLaunch(ProjectileLaunchEvent event) {
        if (event.getEntity().getType() != EntityType.ARROW) return;
        final Entity arrow = event.getEntity();
        new BukkitRunnable() {
            @Override public void run() {
                if (!arrow.isValid()) {
                    cancel();
                    return;
                }
                arrow.getWorld().spawnParticle(Particle.CRIT_MAGIC, arrow.getLocation(), 8, 0.2f, 0.2f, 0.2f, 0.25f);
            }
        }.runTaskTimer(game, 5L, 5L);
    }
}
