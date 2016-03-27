package com.winthier.minigames.pvp;

import java.util.ArrayList;
import java.util.List;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.entity.Player;
import org.bukkit.event.EventHandler;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.inventory.ItemStack;

public class GMKits extends GMAbstractGameMode {
    final PvP game;
    // Kit
    final List<ItemStack> starterKit = new ArrayList<>();
    final List<List<ItemStack>> kits = new ArrayList<>();

    GMKits(PvP game) {
        this.game = game;
    }

    @Override
    public void load() {
        final ConfigurationSection config = game.getConfigFile("kits");
        for (Object o: config.getList("start")) {
            if (o instanceof ItemStack) starterKit.add((ItemStack)o);
        }
        for (Object o: config.getList("kits")) {
            List<ItemStack> kit = new ArrayList<>();
            if (o instanceof ItemStack) {
                kit.add((ItemStack)o);
            } else if (o instanceof List) {
                @SuppressWarnings("unchecked")
                    List<Object> list = (List<Object>)o;
                for (Object p: list) {
                    if (p instanceof ItemStack) kit.add((ItemStack)p);
                }
            }
            if (!kit.isEmpty()) kits.add(kit);
        }
    }        

    @Override
    public void tickArena(long ticks) {
        if (ticks == 0) {
            for (ArenaPlayer ap: game.getArenaPlayers()) {
                if (ap.isPlayer() && ap.isOnline()) {
                    giveStarterGear(ap.getPlayer());
                }
            }
        }
    }

    @Override
    public void onPlayerSpawn(ArenaPlayer ap) {
        Player player = ap.getPlayer();
        if (player == null) return;
        giveStarterGear(player);
    }

    @Override
    public void onPlayerKillPlayer(ArenaPlayer ap, ArenaPlayer victim, PlayerDeathEvent event) {
        Player player = ap.getPlayer();
        if (player == null) return;
        for (int i = 0; i < ap.getStreak(); ++i) giveRandomGear(player);
    }

    @Override
    public void onPlayerDamagePlayer(ArenaPlayer ap, ArenaPlayer victim, EntityDamageByEntityEvent event) {
    }

    void giveStarterGear(Player player) {
        for (ItemStack item: starterKit) game.give(player, item);
    }

    void giveRandomGear(Player player) {
        for (ItemStack item: randomGear()) game.give(player, item.clone());
    }

    List<ItemStack> randomGear() {
        List<ItemStack> result = new ArrayList<>();
        if (kits.isEmpty()) return result;
        for (ItemStack item: kits.get(game.random.nextInt(kits.size()))) {
            result.add(item.clone());
        }
        return result;
    }
}
