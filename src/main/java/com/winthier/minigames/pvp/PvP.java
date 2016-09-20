package com.winthier.minigames.pvp;

import com.winthier.minigames.MinigamesPlugin;
import com.winthier.minigames.game.Game;
import com.winthier.minigames.player.PlayerInfo;
import com.winthier.minigames.util.BukkitFuture;
import com.winthier.minigames.util.Msg;
import com.winthier.minigames.util.Players;
import com.winthier.minigames.util.Title;
import com.winthier.minigames.util.WorldLoader;
import com.winthier.reward.RewardBuilder;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Random;
import java.util.UUID;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.Note;
import org.bukkit.Sound;
import org.bukkit.World;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.entity.Entity;
import org.bukkit.entity.Player;
import org.bukkit.entity.Projectile;
import org.bukkit.event.EventHandler;
import org.bukkit.event.EventPriority;
import org.bukkit.event.Listener;
import org.bukkit.event.block.BlockFadeEvent;
import org.bukkit.event.block.BlockFormEvent;
import org.bukkit.event.block.BlockGrowEvent;
import org.bukkit.event.block.BlockSpreadEvent;
import org.bukkit.event.entity.EntityDamageByEntityEvent;
import org.bukkit.event.entity.FoodLevelChangeEvent;
import org.bukkit.event.entity.PlayerDeathEvent;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerToggleFlightEvent;
import org.bukkit.inventory.ItemStack;
import org.bukkit.inventory.PlayerInventory;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;

public class PvP extends Game implements Listener {
    static enum State {
        INIT(60), WAIT_FOR_PLAYERS(60), COUNTDOWN(8), ARENA(60*5), END(60);
        long seconds;
        State(long seconds) { this.seconds = seconds; }
    }
    BukkitRunnable task;
    int ticks;
    // Config
    String mapId, mapPath;
    ArenaWorld arena;
    // State
    String gameModeName = "OneInTheQuiver";
    State state = State.INIT;
    long stateTicks = 0;
    boolean onePlayerDidJoin = false;
    boolean debug = false;
    final Random random = new Random(System.currentTimeMillis());
    UUID winnerUuid = null;
    String winnerName = "Nobody";
    // Scoreboard
    Scoreboard scoreboard;
    Objective sidebar;
    GMGameMode gameMode = null;
    // Time
    long startTime = 0, endTime = 0, totalTime = 0;
    int totalPlayers = 0;

    @Override
    public void onEnable()
    {
        mapId = getConfig().getString("MapID", "Default");
        mapPath = getConfig().getString("MapPath", "/home/creative/minecraft/worlds/PVP/");
        gameModeName = getConfig().getString("GameMode", gameModeName);
        gameModeName = getConfigFile("config").getString("GameMode", gameModeName);
        WorldLoader.loadWorlds(this,
                               new BukkitFuture<WorldLoader>() {
                                   @Override public void run() {
                                       onWorldsLoaded(get());
                                   }
                               },
                               mapPath);
    }

    void onWorldsLoaded(WorldLoader loader)
    {
        arena = new ArenaWorld(this, loader.getWorld(0));
        arena.init();
        mapId = getConfig().getString("MapID", mapId);
        mapPath = getConfig().getString("MapPath", mapPath);
        debug = getConfig().getBoolean("Debug", false);
        MinigamesPlugin.getEventManager().registerEvents(this, this);
        task = new BukkitRunnable() {
            @Override public void run() {
                onTick();
            }
        };
        task.runTaskTimer(MinigamesPlugin.getInstance(), 1, 1);
        ready();
    }

    @Override
    public void onDisable()
    {
        task.cancel();
    }

    ArenaPlayer getArenaPlayer(PlayerInfo info)
    {
        return info.<ArenaPlayer>getCustomData(ArenaPlayer.class);
    }

    ArenaPlayer getArenaPlayer(UUID uuid)
    {
        return getArenaPlayer(getPlayer(uuid));
    }

    ArenaPlayer getArenaPlayer(Player player)
    {
        return getArenaPlayer(player.getUniqueId());
    }

    List<ArenaPlayer> getArenaPlayers()
    {
        List<ArenaPlayer> result = new ArrayList<>();
        for (PlayerInfo info : getPlayers()) result.add(getArenaPlayer(info));
        return result;
    }

    void onTick()
    {
        long ticks = stateTicks++;
        State newState = tickState(ticks);
        if (newState != null && newState != state) setState(newState);
        for (ArenaPlayer ap : getArenaPlayers()) ap.onTick();
    }

    State tickState(long ticks)
    {
        switch (state) {
        case INIT: return tickInit(ticks);
        case WAIT_FOR_PLAYERS: return tickWaitForPlayers(ticks);
        case COUNTDOWN: return tickCountdown(ticks);
        case ARENA: return tickArena(ticks);
        case END: return tickEnd(ticks);
        default: System.err.println("PvP.tickState(): Unhandled state: " + state);
        }
        return null;
    }

    void setState(State newState)
    {
        this.state = newState;
        stateTicks = 0;
        switch (newState) {
        case WAIT_FOR_PLAYERS:
            arena.world.setPVP(false);
            setupScoreboard();
            break;
        case COUNTDOWN:
            getLogger().info("GameMode(1): " + gameModeName);
            if (false) {
            } else if ("OneInTheQuiver".equalsIgnoreCase(gameModeName)) {
                gameMode = new GMOneInTheQuiver(this);
            } else if ("Kits".equalsIgnoreCase(gameModeName)) {
                gameMode = new GMKits(this);
            } else if ("SnowballFight".equalsIgnoreCase(gameModeName)) {
                gameMode = new GMSnowballFight(this);
            } else if ("ChickenHunt".equalsIgnoreCase(gameModeName)) {
                gameMode = new GMChickenHunt(this);
            } else { // Default
                gameMode = new GMOneInTheQuiver(this);
            }
            getLogger().info("GameMode(2): " + gameMode.getName());
            announceTitle("&a"+gameMode.getName(), "");
            gameMode.load();
            MinigamesPlugin.getEventManager().registerEvents(gameMode, this);
            break;
        case ARENA:
            startTime = System.currentTimeMillis();
            for (ArenaPlayer ap: getArenaPlayers()) {
                ap.freeze(false);
                if (ap.isPlayer()) {
                    totalPlayers += 1;
                    gameMode.onPlayerSpawn(ap);
                }
            }
            arena.world.setPVP(true);
            setupScoreboard();
            break;
        case END:
            endTime = System.currentTimeMillis();
            totalTime = endTime - startTime;
            arena.world.setPVP(false);
            int max = 0;
            for (ArenaPlayer ap: getArenaPlayers()) {
                if (ap.isPlayer() && ap.getScore() > max) {
                    max = ap.getScore();
                    winnerName = ap.getName();
                    winnerUuid = ap.getUuid();
                }
            }
            for (Player player : getOnlinePlayers()) {
                player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDERDRAGON_DEATH, 1f, 1f);
            }
            if (!debug && totalPlayers >= 2 && winnerUuid != null && totalTime > 1000*60*3) {
                ConfigurationSection config = getConfigFile("rewards");
                RewardBuilder reward = RewardBuilder.create().uuid(winnerUuid).name(winnerName);
                reward.comment("Winning a game of PvP");
                reward.config(config.getConfigurationSection("win"));
                reward.store();
            }
            break;
        }
    }

    ArenaPlayer getCurrentWinner() {
        int max = 0;
        ArenaPlayer result = getArenaPlayers().get(0);
        for (ArenaPlayer ap: getArenaPlayers()) {
            if (ap.isPlayer() && ap.getScore() > max) {
                max = ap.getScore();
                result = ap;
            }
        }
        return result;
    }

    State tickInit(long ticks)
    {
        if (ticks > state.seconds*20) return State.WAIT_FOR_PLAYERS;
        if (onePlayerDidJoin) return State.WAIT_FOR_PLAYERS;
        return null;
    }

    State tickWaitForPlayers(long ticks)
    {
        if (onePlayerDidJoin && getOnlinePlayers().isEmpty()) {
            cancel();
            return null;
        }
        if (ticks > state.seconds*20) {
            if (getOnlinePlayers().isEmpty()) {
                cancel();
                return null;
            }
            int onlinePlayerCount = 0;
            for (ArenaPlayer ap: getArenaPlayers()) {
                if (!ap.isOnline()) { ap.leave(); }
                if (ap.isPlayer()) onlinePlayerCount += 1;
            }
            if ((debug && onlinePlayerCount >= 1) || onlinePlayerCount >= 2) {
                return State.COUNTDOWN;
            } else {
                cancel();
                return null;
            }
        }
        if (ticks % 20 == 0) { setSidebarTitle("Waiting", ticks); }
        int notReadyCount = 0;
        int playerCount = 0;
        for (ArenaPlayer ap : getArenaPlayers()) {
            if (ap.isPlayer()) playerCount += 1;
            if (ap.isPlayer() && !ap.isReady()) {
                notReadyCount += 1;
                if (ticks % (20*5) == 0 && ap.getPlayer() != null) {
                    ap.getPlayer().sendMessage("");
                    List<Object> list = new ArrayList<>();
                    list.add(Msg.format("&fClick here when ready: "));
                    list.add(button("&3[Ready]", "&3Mark yourself as ready", "/ready"));
                    list.add(Msg.format("&f or "));
                    list.add(button("&c[Leave]", "&cLeave this game", "/leave"));
                    Msg.sendRaw(ap.getPlayer(), list);
                    ap.getPlayer().sendMessage("");
                }
            }
        }
        if (((debug && playerCount >= 1) || playerCount >= 2) && notReadyCount == 0) return State.COUNTDOWN;
        return null;
    }

    State tickCountdown(long ticks) {
        if (ticks > state.seconds*20) return State.ARENA;
        if (ticks % 20 == 0) { setSidebarTitle("Get ready", ticks); }
        if (ticks % 20 == 0) {
            long secondsLeft = state.seconds - ticks / 20;
            if (secondsLeft == 0) {
                announceTitle("", "&a&oFight!");
                for (Player player: getOnlinePlayers()) {
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_LARGE_BLAST, 1f, 1f);
                }
            } else if (secondsLeft <= 5) {
                announceTitle("&a&o" + secondsLeft, "&aGet ready!");
                for (Player player: getOnlinePlayers()) {
                    player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int)ticks/20));
                }
            }
        }
        return null;
    }

    State tickArena(long ticks)
    {
        if (ticks > state.seconds*20) return State.END;
        if (ticks % 20 == 0) { setSidebarTitle("Fight", ticks); }
        int rank1 = 0, rank2 = 0;
        for (ArenaPlayer ap: getArenaPlayers()) {
            if (ap.getScore() > rank1) {
                rank2 = rank1;
                rank1 = ap.getScore();
            } else if (ap.getScore() > rank2) {
                rank2 = ap.getScore();
            }
        }
        if (rank1 >= rank2 + gameMode.winAdvantage()) return State.END;
        gameMode.tickArena(ticks);
        return null;
    }

    State tickEnd(long ticks)
    {
        if (getOnlinePlayers().isEmpty()) { cancel(); return null; }
        if (ticks > state.seconds*20) { cancel(); return null; }
        if (ticks % 20 == 0) { setSidebarTitle("Game Over", ticks); }
        if (ticks % (20*5) == 0) {
            announceTitle("&a" + winnerName, "&awins the game!");
            for (Player player : getOnlinePlayers()) {
                if (winnerName != null) {
                    Msg.send(player, "%s wins the game!", winnerName);
                } else {
                    Msg.send(player, "Draw! Nobody wins.");
                }
                List<Object> list = new ArrayList<>();
                list.add("Click here to leave the game: ");
                list.add(button("&c[Leave]", "&cLeave this game", "/leave"));
                Msg.sendRaw(player, list);
            }
        }
        return null;
    }

    private void setupScoreboard() {
        scoreboard = MinigamesPlugin.getInstance().getServer().getScoreboardManager().getNewScoreboard();
        sidebar = scoreboard.registerNewObjective("Sidebar", "dummy");
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        sidebar.setDisplayName(Msg.format("&aPvP"));
        for (Player player: getOnlinePlayers()) {
            if (getArenaPlayer(player).isPlayer()) {
                sidebar.getScore(player.getName()).setScore(0);
            }
        }
        for (Player player : getOnlinePlayers()) player.setScoreboard(scoreboard);
    }

    void setSidebarTitle(String title, long ticks)
    {
        ticks = state.seconds * 20 - ticks;
        long seconds = ticks / 20;
        long minutes = seconds / 60;
        sidebar.setDisplayName(Msg.format("&a%s &f%02d&a:&f%02d", title, minutes, seconds % 60));
    }
    
    @Override
    public Location getSpawnLocation(Player player)
    {
        return arena.dealSpawnLocation();
    }

    @Override
    public void onPlayerReady(Player player)
    {
        Players.reset(player);
        ArenaPlayer ap = getArenaPlayer(player);
        ap.setup(player);
        if (ap.isPlayer()) onePlayerDidJoin = true;
        if (arena.credits() != null) {
            Title.show(player, "", "Made by &a" + arena.credits());
            player.sendMessage("");
            Msg.send(player, "&a%s&r made by &a%s", mapId, arena.credits());
            player.sendMessage("");
        }
    }

    @Override
    public boolean joinPlayers(List<UUID> uuids)
    {
        switch (state) {
        case INIT:
        case WAIT_FOR_PLAYERS:
            return super.joinPlayers(uuids);
        default: return false;
        }
    }

    @Override
    public boolean joinSpectators(List<UUID> uuids)
    {
        switch (state) {
        case INIT:
        case WAIT_FOR_PLAYERS:
            return false;
        default:
            if (super.joinSpectators(uuids)) {
                for (UUID uuid : uuids) {
                    getArenaPlayer(uuid).setSpectator();
                }
                return true;
            }
        }
        return false;
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        final Player player = event.getPlayer();
        if (scoreboard != null) player.setScoreboard(scoreboard);
        switch (state) {
        case INIT: case WAIT_FOR_PLAYERS: case COUNTDOWN:
            getArenaPlayer(player).freeze(true);
            break;
        default:
            getArenaPlayer(player).freeze(false);
            break;
        }
        getArenaPlayer(player).onJoin(player);
        if (state == State.WAIT_FOR_PLAYERS) {
            final ArenaPlayer ap = getArenaPlayer(player);
            if (ap.isPlayer()) {
                sidebar.getScore(player.getName()).setScore(ap.isReady() ? 1 : 0);
            }
        }
    }

    @EventHandler(ignoreCancelled = true)
    public void onEntityDamageByEntity(EntityDamageByEntityEvent event)
    {
        if (!(event.getEntity() instanceof Player)) return;
        Player damagee = (Player)event.getEntity();
        Player damager = null;
        Entity entity = event.getDamager();
        if (entity instanceof Player) {
            damager = (Player)entity;
        } else if (entity instanceof Projectile &&
                   ((Projectile)entity).getShooter() instanceof Player) {
            damager = (Player)((Projectile)entity).getShooter();
        } else {
            return;
        }
        if (damager == null) return;
        if (damager.equals(damagee)) return;
        ArenaPlayer ap1 = getArenaPlayer(damagee);
        ArenaPlayer ap2 = getArenaPlayer(damager);
        if (ap1 == ap2) return;
        if (ap1 == null || ap2 == null) return;
        if (!ap1.isPlayer() || !ap2.isPlayer()) {
            event.setCancelled(true);
            return;
        }
        ap1.setLastDamager(ap2.getUuid());
        gameMode.onPlayerDamagePlayer(ap2, ap1, event);
    }

    @EventHandler
    public void onPlayerDeath(PlayerDeathEvent event) {
        event.getDrops().clear();
        final Player player = event.getEntity();
        final ArenaPlayer ap = getArenaPlayer(player);
        if (state != State.ARENA || !ap.isPlayer()) {
            ap.leave();
            return;
        }
        ap.onDeath(player);
        announce("&c" + event.getDeathMessage());
        announceTitle("", "&c" + event.getDeathMessage());
        event.setDeathMessage(null);
        // Killer
        ArenaPlayer killer = ap.getKiller();
        if (killer != null && ap != killer) {
            killer.onKill(ap);
            gameMode.onPlayerKillPlayer(killer, ap, event);
        }
    }

    @EventHandler
    public void onPlayerToggleFlight(PlayerToggleFlightEvent event) {
        event.setCancelled(true);
    }
    
    @EventHandler
    public void onFoodLevelChange(FoodLevelChangeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockFade(BlockFadeEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockGrow(BlockGrowEvent event) {
        event.setCancelled(true);
    }
    
    @EventHandler(ignoreCancelled = true)
    public void onBlockForm(BlockFormEvent event) {
        event.setCancelled(true);
    }

    @EventHandler(ignoreCancelled = true)
    public void onBlockSpread(BlockSpreadEvent event) {
        event.setCancelled(true);
    }

    @Override
    public boolean onCommand(Player player, String command, String[] args)
    {
        if ("test".equals(command)) {
        } else if (state == State.WAIT_FOR_PLAYERS && "ready".equals(command)) {
            getArenaPlayer(player).setReady(true);
            sidebar.getScore(player.getName()).setScore(1);
        } else {
            return false;
        }
        return true;
    }

    Object button(String chat, String tooltip, String command)
    {
        Map<String, Object> map = new HashMap<>();
        map.put("text", Msg.format(chat));
        Map<String, Object> map2 = new HashMap<>();
        map.put("clickEvent", map2);
        map2.put("action", "run_command");
        map2.put("value", command);
        map2 = new HashMap<>();
        map.put("hoverEvent", map2);
        map2.put("action", "show_text");
        map2.put("value", Msg.format(tooltip));
        return map;
    }

    void give(Player player, ItemStack item) {
        final PlayerInventory inv = player.getInventory();
        switch (item.getType()) {
        case LEATHER_HELMET:
        case IRON_HELMET:
        case CHAINMAIL_HELMET:
        case DIAMOND_HELMET:
        case GOLD_HELMET:
            if (inv.getHelmet() == null || inv.getHelmet().getType() == Material.AIR) {
                inv.setHelmet(item);
                return;
            }
            break;
        case LEATHER_CHESTPLATE:
        case IRON_CHESTPLATE:
        case CHAINMAIL_CHESTPLATE:
        case DIAMOND_CHESTPLATE:
        case GOLD_CHESTPLATE:
            if (inv.getChestplate() == null || inv.getChestplate().getType() == Material.AIR) {
                inv.setChestplate(item);
                return;
            }
            break;
        case LEATHER_LEGGINGS:
        case IRON_LEGGINGS:
        case CHAINMAIL_LEGGINGS:
        case DIAMOND_LEGGINGS:
        case GOLD_LEGGINGS:
            if (inv.getLeggings() == null || inv.getLeggings().getType() == Material.AIR) {
                inv.setLeggings(item);
                return;
            }
            break;
        case LEATHER_BOOTS:
        case IRON_BOOTS:
        case CHAINMAIL_BOOTS:
        case DIAMOND_BOOTS:
        case GOLD_BOOTS:
            if (inv.getBoots() == null || inv.getBoots().getType() == Material.AIR) {
                inv.setBoots(item);
                return;
            }
            break;
        }
        player.getWorld().dropItem(player.getEyeLocation(), item).setPickupDelay(0);
    }

}
