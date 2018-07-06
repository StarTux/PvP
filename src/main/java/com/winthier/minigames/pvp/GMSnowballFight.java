package com.winthier.minigames.pvp;

import org.bukkit.Effect;
import org.bukkit.Material;
import org.bukkit.Sound;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDamageEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerDropItemEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class GMSnowballFight extends GMAbstractGameMode {
    final PvP game;
    final int SLOWNESS_DURATION = 20*10;
    final int VISION_DURATION = 20*8;

    GMSnowballFight(PvP game) {
        this.game = game;
    }

    @Override
    public String getName() { return "Snowball Fight"; }

    void giveSnowball(Player player) {
        ItemStack item = new ItemStack(Material.SNOW_BALL);
        player.getWorld().dropItem(player.getEyeLocation(), item).setPickupDelay(0);
    }

    void giveEnderPearl(Player player) {
        ItemStack item = new ItemStack(Material.ENDER_PEARL);
        player.getWorld().dropItem(player.getEyeLocation(), item).setPickupDelay(0);
    }

    void giveIceHat(Player player) {
        player.getInventory().setHelmet(new ItemStack(Material.ICE));
    }

    void removeIceHat(Player player) {
        player.getInventory().setHelmet(null);
    }

    @Override
    public void tickArena(long ticks) {
        if (ticks % 20 == 0) {
            for (ArenaPlayer ap: game.getArenaPlayers()) {
                if (ap.isPlayer() && ap.isOnline()) {
                    Player player = ap.getPlayer();
                    if (!player.getInventory().contains(Material.SNOW_BALL)) {
                        giveSnowball(player);
                    }
                    if (!player.hasPotionEffect(PotionEffectType.SLOW)) {
                        removeIceHat(player);
                    } else {
                        
                    }
                }
            }
        }
    }

    @Override
    public void load() {
    }

    @Override
    public void onPlayerDamagePlayer(ArenaPlayer ap, ArenaPlayer victim, EntityDamageByEntityEvent event) {
        if (event.getDamager().getType() != EntityType.SNOWBALL) {
            event.setCancelled(true);
            return;
        }
        if (victim.isOnline()) {
            if (victim.getPlayer().hasPotionEffect(PotionEffectType.SLOW)) {
                event.setCancelled(true);
                return;
            }
            victim.addDeaths(1);
            if (victim.getDeaths() > 0 && victim.getDeaths() % 3 == 0) {
                giveEnderPearl(victim.getPlayer());
            }
            victim.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.SLOW, SLOWNESS_DURATION, 2));
            victim.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.CONFUSION, VISION_DURATION, 0));
            victim.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.BLINDNESS, VISION_DURATION, 0));
            victim.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.NIGHT_VISION, SLOWNESS_DURATION, 0));
            victim.getPlayer().playSound(victim.getPlayer().getEyeLocation(), Sound.ENTITY_ENDERDRAGON_HURT, 1.0f, 1.0f);
            Msg.send(victim.getPlayer(), "&3&lSnowball Fight&c&o You've been hit by %s!", ap.getName());
            giveIceHat(victim.getPlayer());
            ap.addScore(1);
            victim.addScore(-1);
            if (ap.isOnline()) {
                Player player = ap.getPlayer();
                player.playSound(player.getEyeLocation(), Sound.ENTITY_ARROW_HIT_PLAYER, 1.0f, 1.0f);
                Msg.send(player, "&3&lSnowball Fight&r&o You hit &a&o%s&r&o!", victim.getName());
            }
            victim.getPlayer().getWorld().spigot().playEffect(victim.getPlayer().getEyeLocation(), Effect.SNOWBALL_BREAK, 0, 0, 0.5f, 0.5f, 0.5f, 0.25f, 250, 64);
        }
    }

    @Override
    public void onPlayerKillPlayer(ArenaPlayer ap, ArenaPlayer victim, PlayerDeathEvent event) {
    }

    @Override
    public void onPlayerSpawn(ArenaPlayer ap) {
    }

    @EventHandler(priority = EventPriority.HIGHEST)
    public void onEntityDamage(EntityDamageEvent event) {
        event.setCancelled(true);
    }

    @EventHandler
    public void onPlayerDropItem(PlayerDropItemEvent event) {
        event.setCancelled(true);
    }
}
