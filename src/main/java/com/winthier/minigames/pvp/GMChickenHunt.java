package com.winthier.minigames.pvp;

import com.winthier.minigames.util.Title;
import org.bukkit.FireworkEffect;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.entity.Chicken;
import org.bukkit.entity.Entity;
import org.bukkit.entity.EntityType;
import org.bukkit.entity.Firework;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.EntityDeathEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.meta.FireworkMeta;
import org.bukkit.potion.PotionEffect;
import org.bukkit.potion.PotionEffectType;

public class GMChickenHunt extends GMAbstractGameMode {
    final PvP pvp;
    final int CHICKEN_TICKS = 20*10;
    int chickenTicks = 20*2;
    Chicken currentChicken = null;
    int pointsPerChicken = 10;

    @Override
    public String getName() { return "Chicken Hunt"; }

    @Override
    public int winAdvantage() { return 100; }
    
    GMChickenHunt(PvP pvp) {
        this.pvp = pvp;
    }
    
    @Override
    public void tickArena(long ticks) {
        int chickenTicks = this.chickenTicks--;
        if (chickenTicks == 0) {
            this.chickenTicks = CHICKEN_TICKS;
            spawnAChicken();
        }
        if ((ticks % 20) == 0) {
            if (currentChicken == null) {
                for (Entity e: pvp.arena.world.getEntities()) {
                    if (e.getType() == EntityType.CHICKEN) {
                        currentChicken = (Chicken)e;
                        break;
                    }
                }
            }
            if (currentChicken != null) {
                for (Player player: pvp.getOnlinePlayers()) {
                    player.setCompassTarget(currentChicken.getLocation());
                }
            }
        }
    }

    @Override
    public void load() {}

    // @Override
    // public void onPlayerDamagePlayer(ArenaPlayer ap, ArenaPlayer victim, EntityDamageByEntityEvent event) {
    //     event.setCancelled(true);
    //     event.setDamage(0.0);
    // }

    @Override
    public void onPlayerSpawn(ArenaPlayer ap) {
        ItemStack sword = new ItemStack(Material.IRON_SWORD);
        ItemStack bow = new ItemStack(Material.BOW);
        ItemStack arrows = new ItemStack(Material.ARROW);
        ItemStack compass = new ItemStack(Material.COMPASS);
        arrows.setAmount(9);
        ap.getPlayer().getInventory().addItem(sword);
        ap.getPlayer().getInventory().addItem(bow);
        ap.getPlayer().getInventory().addItem(arrows);
        ap.getPlayer().getInventory().addItem(compass);
        if (ap.getScore() > 0 && pvp.getCurrentWinner().equals(ap)) {
            ap.getPlayer().addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 9999, 0));
        }
    }

    @EventHandler
    public void onEntityDeath(EntityDeathEvent event) {
        event.getDrops().clear();
        if (event.getEntity().getType() != EntityType.CHICKEN) return;
        Chicken chicken = (Chicken)event.getEntity();
        Player killer = chicken.getKiller();
        if (killer == null) return;
        ArenaPlayer ap = pvp.getArenaPlayer(killer);
        ap.addScore(pointsPerChicken);
        this.chickenTicks = 20*2;
        pvp.announceTitle("", "&4"+killer.getName()+" killed the chicken!");
        pvp.announce("&3&lChickenHunt&r %s killed the chicken!", killer.getName());
        Location loc = chicken.getLocation();
        loc.getWorld().strikeLightningEffect(loc);
        ItemStack arrow = new ItemStack(Material.ARROW);
        arrow.setAmount(3);
        killer.getInventory().addItem(arrow);
        if (pvp.getCurrentWinner().getUuid().equals(killer.getUniqueId())) {
            killer.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 9999, 0));
        }
    }

    void spawnAChicken() {
        Location loc = pvp.arena.randomSpawnLocation();
        Chicken chicken = loc.getWorld().spawn(loc, Chicken.class);
        if (chicken == null) return;
        chicken.addPotionEffect(new PotionEffect(PotionEffectType.SPEED, 9999, 3));
        final double HEALTH = 20.0;
        chicken.setMaxHealth(HEALTH);
        chicken.setHealth(HEALTH);
        chicken.addPotionEffect(new PotionEffect(PotionEffectType.GLOWING, 99999, 0));
        currentChicken = chicken;
        // pvp.announceTitle("", "&4A chicken just spawned!");
        pvp.announce("&3&lChickenHunt&r A chicken just spawned! Kill it to win "+pointsPerChicken+" points.");
        // spawn firework
        Location fwLoc = loc.getWorld().getHighestBlockAt(loc.getBlockX(), loc.getBlockZ()).getLocation().add(0.5, 4.0, 0.5);
        Firework firework = (Firework)fwLoc.getWorld().spawnEntity(fwLoc, EntityType.FIREWORK);
        FireworkMeta meta = (FireworkMeta)pvp.getConfigFile("ChickenHunt").getItemStack("SpecialFireworkEffect").getItemMeta();
        firework.setFireworkMeta(meta);
    }
}
