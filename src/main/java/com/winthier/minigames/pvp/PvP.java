package com.winthier.minigames.pvp;

import com.winthier.connect.Connect;
import com.winthier.connect.Message;
import com.winthier.connect.event.ConnectMessageEvent;
import java.io.FileReader;
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
import org.bukkit.WorldCreator;
import org.bukkit.WorldType;
import org.bukkit.command.Command;
import org.bukkit.command.CommandSender;
import org.bukkit.configuration.ConfigurationSection;
import org.bukkit.configuration.MemoryConfiguration;
import org.bukkit.configuration.file.YamlConfiguration;
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
import org.bukkit.plugin.java.JavaPlugin;
import org.bukkit.scheduler.BukkitRunnable;
import org.bukkit.scoreboard.DisplaySlot;
import org.bukkit.scoreboard.Objective;
import org.bukkit.scoreboard.Scoreboard;
import org.json.simple.JSONValue;
import org.spigotmc.event.player.PlayerSpawnLocationEvent;

public class PvP extends JavaPlugin implements Listener {
    static enum State {
        INIT(60), WAIT_FOR_PLAYERS(60), COUNTDOWN(8), ARENA(60*5), END(60);
        long seconds;
        State(long seconds) { this.seconds = seconds; }
    }
    BukkitRunnable task;
    int ticks;
    // Config
    String mapId;
    World world;
    ArenaWorld arena;
    UUID gameId;
    // State
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
    //
    final Map<UUID, ArenaPlayer> arenaPlayers = new HashMap<>();

    @Override @SuppressWarnings("unchecked")
    public void onEnable() {
        saveDefaultConfig();
        saveResource("Kits.yml", false);
        saveResource("OneInTheQuiver.yml", false);
        saveResource("ChickenHunt.yml", false);
        ConfigurationSection gameConfig;
        ConfigurationSection worldConfig;
        try {
            gameConfig = new YamlConfiguration().createSection("tmp", (Map<String, Object>)JSONValue.parse(new FileReader("game_config.json")));
            worldConfig = YamlConfiguration.loadConfiguration(new FileReader("GameWorld/config.yml"));
        } catch (Throwable t) {
            t.printStackTrace();
            getServer().shutdown();
            return;
        }
        mapId = gameConfig.getString("map_id", mapId);
        gameId = UUID.fromString(gameConfig.getString("unique_id"));
        debug = gameConfig.getBoolean("debug", debug);
        try {
            gameMode = GMGameMode.Type.valueOf(gameConfig.getString("play_mode", "").toUpperCase().replace(" ", "_")).create(this);
        } catch (Throwable t) {
            t.printStackTrace();
            gameMode = new GMOneInTheQuiver(this);
        }

        WorldCreator wc = WorldCreator.name("GameWorld");
        wc.generator("VoidGenerator");
        wc.type(WorldType.FLAT);
        try {
            wc.environment(World.Environment.valueOf(worldConfig.getString("world.Environment").toUpperCase()));
        } catch (Throwable t) {
            wc.environment(World.Environment.NORMAL);
        }
        world = wc.createWorld();

        arena = new ArenaWorld(this, world);
        arena.init();
        getServer().getPluginManager().registerEvents(this, this);
        task = new BukkitRunnable() {
            @Override public void run() {
                onTick();
            }
        };
        task.runTaskTimer(this, 1, 1);
    }

    ArenaPlayer getArenaPlayer(UUID uuid) {
        ArenaPlayer result = arenaPlayers.get(uuid);
        if (result == null) {
            result = new ArenaPlayer(this, uuid);
            arenaPlayers.put(uuid, result);
        }
        return result;
    }

    ArenaPlayer getArenaPlayer(Player player) {
        return getArenaPlayer(player.getUniqueId());
    }

    List<ArenaPlayer> getArenaPlayers() {
        return new ArrayList<>(arenaPlayers.values());
    }

    void onTick() {
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
            daemonGameConfig("players_may_join", false);
            Msg.announceTitle("&a"+gameMode.getName(), "");
            gameMode.load();
            getServer().getPluginManager().registerEvents(gameMode, this);
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
            daemonGameEnd();
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
            for (Player player : getServer().getOnlinePlayers()) {
                player.playSound(player.getEyeLocation(), Sound.ENTITY_ENDER_DRAGON_DEATH, 1f, 1f);
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
        if (getArenaPlayers().isEmpty()) {
            getServer().shutdown();
            return null;
        }
        if (ticks > state.seconds*20) {
            if (getServer().getOnlinePlayers().isEmpty()) {
                getServer().shutdown();
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
                getServer().shutdown();
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
                    list.add(button("&c[Quit]", "&cLeave this game", "/quit"));
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
                Msg.announceTitle("", "&a&oFight!");
                for (Player player: getServer().getOnlinePlayers()) {
                    player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_ROCKET_LARGE_BLAST, 1f, 1f);
                }
            } else if (secondsLeft <= 5) {
                Msg.announceTitle("&a&o" + secondsLeft, "&aGet ready!");
                for (Player player: getServer().getOnlinePlayers()) {
                    player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note((int)ticks/20));
                }
            }
        }
        return null;
    }

    State tickArena(long ticks)
    {
        if (getServer().getOnlinePlayers().isEmpty()) {
            getServer().shutdown();
            return null;
        }
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
        if (getServer().getOnlinePlayers().isEmpty()) {
            getServer().shutdown();
            return null;
        }
        if (ticks > state.seconds*20) {
            getServer().shutdown();
            return null;
        }
        if (ticks % 20 == 0) { setSidebarTitle("Game Over", ticks); }
        if (ticks % (20*5) == 0) {
            Msg.announceTitle("&a" + winnerName, "&awins the game!");
            for (Player player : getServer().getOnlinePlayers()) {
                if (winnerName != null) {
                    Msg.send(player, "%s wins the game!", winnerName);
                } else {
                    Msg.send(player, "Draw! Nobody wins.");
                }
                List<Object> list = new ArrayList<>();
                list.add("Click here to leave the game: ");
                list.add(button("&c[Quit]", "&cLeave this game", "/quit"));
                Msg.sendRaw(player, list);
            }
        }
        return null;
    }

    private void setupScoreboard() {
        scoreboard = getServer().getScoreboardManager().getNewScoreboard();
        sidebar = scoreboard.registerNewObjective("Sidebar", "dummy", "PvP");
        sidebar.setDisplaySlot(DisplaySlot.SIDEBAR);
        sidebar.setDisplayName(Msg.format("&aPvP"));
        for (Player player: getServer().getOnlinePlayers()) {
            if (getArenaPlayer(player).isPlayer()) {
                sidebar.getScore(player.getName()).setScore(0);
            }
        }
        for (Player player : getServer().getOnlinePlayers()) player.setScoreboard(scoreboard);
    }

    void setSidebarTitle(String title, long ticks) {
        ticks = state.seconds * 20 - ticks;
        long seconds = ticks / 20;
        long minutes = seconds / 60;
        sidebar.setDisplayName(Msg.format("&a%s &f%02d&a:&f%02d", title, minutes, seconds % 60));
    }

    @EventHandler
    public void onPlayerSpawnLocation(PlayerSpawnLocationEvent event) {
        Player player = event.getPlayer();
        if (getArenaPlayer(player).hasJoinedBefore) return;
        event.setSpawnLocation(getSpawnLocation(player));
    }

    public Location getSpawnLocation(Player player) {
        return arena.dealSpawnLocation();
    }

    public void onPlayerReady(Player player) {
        ArenaPlayer ap = getArenaPlayer(player);
        ap.setup(player);
        if (ap.isPlayer()) onePlayerDidJoin = true;
        if (arena.credits() != null) {
            Msg.sendTitle(player, "", "Made by &a" + arena.credits());
            player.sendMessage("");
            Msg.send(player, "&a%s&r made by &a%s", mapId, arena.credits());
            player.sendMessage("");
        }
    }

    @EventHandler(priority = EventPriority.MONITOR)
    public void onPlayerJoin(PlayerJoinEvent event)
    {
        final Player player = event.getPlayer();
        if (scoreboard != null) player.setScoreboard(scoreboard);
        if (!getArenaPlayer(player).hasJoinedBefore) {
            getArenaPlayer(player).hasJoinedBefore = true;
            onPlayerReady(player);
        }
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
        Msg.announce("&c" + event.getDeathMessage());
        Msg.announceTitle("", "&c" + event.getDeathMessage());
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
    public boolean onCommand(CommandSender sender, Command bcommand, String command, String[] args) {
        if (!(sender instanceof Player)) return true;
        Player player = (Player)sender;
        if ("test".equals(command)) {
        } else if (state == State.WAIT_FOR_PLAYERS && "ready".equals(command)) {
            getArenaPlayer(player).setReady(true);
            sidebar.getScore(player.getName()).setScore(1);
        } else if ("quit".equals(command)) {
            daemonRemovePlayer(player.getUniqueId());
        } else {
            return false;
        }
        return true;
    }

    Object button(String chat, String tooltip, String command) {
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
        case GOLDEN_HELMET:
            if (inv.getHelmet() == null || inv.getHelmet().getType() == Material.AIR) {
                inv.setHelmet(item);
                return;
            }
            break;
        case LEATHER_CHESTPLATE:
        case IRON_CHESTPLATE:
        case CHAINMAIL_CHESTPLATE:
        case DIAMOND_CHESTPLATE:
        case GOLDEN_CHESTPLATE:
            if (inv.getChestplate() == null || inv.getChestplate().getType() == Material.AIR) {
                inv.setChestplate(item);
                return;
            }
            break;
        case LEATHER_LEGGINGS:
        case IRON_LEGGINGS:
        case CHAINMAIL_LEGGINGS:
        case DIAMOND_LEGGINGS:
        case GOLDEN_LEGGINGS:
            if (inv.getLeggings() == null || inv.getLeggings().getType() == Material.AIR) {
                inv.setLeggings(item);
                return;
            }
            break;
        case LEATHER_BOOTS:
        case IRON_BOOTS:
        case CHAINMAIL_BOOTS:
        case DIAMOND_BOOTS:
        case GOLDEN_BOOTS:
            if (inv.getBoots() == null || inv.getBoots().getType() == Material.AIR) {
                inv.setBoots(item);
                return;
            }
            break;
        }
        player.getWorld().dropItem(player.getEyeLocation(), item).setPickupDelay(0);
    }

    // Daemon stuff

    // Request from a player to join this game.  It gets sent to us by
    // the daemon when the player enters the appropriate remote
    // command.  Tell the daemon that that the request has been
    // accepted, then wait for the daemon to send the player here.
    @EventHandler
    public void onConnectMessage(ConnectMessageEvent event) {
        final Message message = event.getMessage();
        if (message.getFrom().equals("daemon") && message.getChannel().equals("minigames")) {
            @SuppressWarnings("unchecked")
            Map<String, Object> payload = (Map<String, Object>)message.getPayload();
            if (payload == null) return;
            boolean join = false;
            boolean leave = false;
            boolean spectate = false;
            switch ((String)payload.get("action")) {
            case "player_join_game":
                join = true;
                spectate = false;
                break;
            case "player_spectate_game":
                join = true;
                spectate = true;
                break;
            case "player_leave_game":
                leave = true;
                break;
            default:
                return;
            }
            if (join) {
                final UUID gameId = UUID.fromString((String)payload.get("game"));
                if (!gameId.equals(gameId)) return;
                final UUID player = UUID.fromString((String)payload.get("player"));
                if (spectate) {
                    getArenaPlayer(player).setSpectator();
                    daemonAddSpectator(player);
                } else {
                    if (state != State.WAIT_FOR_PLAYERS) return;
                    if (arenaPlayers.containsKey(player)) return;
                    daemonAddPlayer(player);
                }
            } else if (leave) {
                final UUID playerId = UUID.fromString((String)payload.get("player"));
                Player player = getServer().getPlayer(playerId);
                if (player != null) player.kickPlayer("Leaving game");
            }
        }
    }

    void daemonRemovePlayer(UUID uuid) {
        Player player = getArenaPlayer(uuid).getPlayer();
        arenaPlayers.remove(uuid);
        Map<String, Object> map = new HashMap<>();
        map.put("action", "player_leave_game");
        map.put("player", uuid.toString());
        map.put("game", gameId.toString());
        Connect.getInstance().send("daemon", "minigames", map);
        if (player != null) player.kickPlayer("Leaving");
    }

    void daemonAddPlayer(UUID uuid) {
        Map<String, Object> map = new HashMap<>();
        map.put("action", "game_add_player");
        map.put("player", uuid.toString());
        map.put("game", gameId.toString());
        Connect.getInstance().send("daemon", "minigames", map);
    }

    void daemonAddSpectator(UUID uuid) {
        Map<String, Object> map = new HashMap<>();
        map.put("action", "game_add_spectator");
        map.put("player", uuid.toString());
        map.put("game", gameId.toString());
        Connect.getInstance().send("daemon", "minigames", map);
    }

    void daemonGameEnd() {
        Map<String, Object> map = new HashMap<>();
        map.put("action", "game_end");
        map.put("game", gameId.toString());
        Connect.getInstance().send("daemon", "minigames", map);
    }

    void daemonGameConfig(String key, Object value) {
        Map<String, Object> map = new HashMap<>();
        map.put("action", "game_config");
        map.put("game", gameId.toString());
        map.put("key", key);
        map.put("value", value);
        Connect.getInstance().send("daemon", "minigames", map);
    }

    // End of Daemon stuff
}
