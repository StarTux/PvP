package com.winthier.minigames.pvp;

import com.winthier.minigames.MinigamesPlugin;
import com.winthier.minigames.player.PlayerInfo;
import com.winthier.minigames.util.Players;
import com.winthier.minigames.util.Title;
import java.util.UUID;
import lombok.Data;
import lombok.NonNull;
import org.bukkit.Bukkit;
import org.bukkit.GameMode;
import org.bukkit.Instrument;
import org.bukkit.Location;
import org.bukkit.Note;
import org.bukkit.Sound;
import org.bukkit.entity.Player;

@Data
public class ArenaPlayer
{
    static enum Type { PLAYER, SPECTATOR; }
    @NonNull final PvP game;
    @NonNull final UUID uuid;
    String name = "";
    Type playerType = Type.PLAYER;
    boolean ready;
    long offlineTicks = 0;
    long deathCooldown = 0;
    String selectedKit = "default";
    UUID lastDamager = null;
    boolean frozen = false;
    Location spawnLocation = null;
    int score = 0;
    int deaths = 0;
    int streak = 0;
    
    public ArenaPlayer(PvP game, UUID uuid)
    {
        this.game = game;
        this.uuid = uuid;
    }

    void setup(Player player)
    {
        this.name = player.getName();
        offlineTicks = 0;
    }

    Player getPlayer()
    {
        return Bukkit.getServer().getPlayer(uuid);
    }

    PlayerInfo getInfo()
    {
        return game.getPlayer(uuid);
    }

    boolean isOnline()
    {
        return getPlayer() != null;
    }

    boolean isPlayer()
    {
        return playerType == Type.PLAYER;
    }

    boolean isSpectator()
    {
        return playerType == Type.SPECTATOR;
    }

    void leave() {
        MinigamesPlugin.getInstance().leavePlayer(uuid);
    }

    boolean isDead() {
        return deathCooldown > 0;
    }

    void onTick()
    {
        Player player = getPlayer();
        if (player == null && offlineTicks++ > 20*60) {
            MinigamesPlugin.getInstance().leavePlayer(uuid);
            return;
        }
        if (frozen && player != null) {
            if (player.getLocation().distanceSquared(getSpawnLocation()) > 2) {
                player.teleport(getSpawnLocation());
            }
        }
        if (player != null && isPlayer() && isDead()) {
            final long left = --this.deathCooldown;
            if (left == 0) {
                player.teleport(game.arena.dealSpawnLocation());
                Players.reset(player);
                player.setGameMode(GameMode.ADVENTURE);
                player.setScoreboard(game.scoreboard);
                Title.show(player, "", "&aGo!");
                player.playSound(player.getEyeLocation(), Sound.ENTITY_FIREWORK_LARGE_BLAST, 1f, 1f);
                game.gameMode.onPlayerSpawn(this);
            } else if (left % 20 == 0 && left/20 <= 3) {
                Title.show(player, "&a&o" + left/20, "&aGet ready!");
                player.playNote(player.getEyeLocation(), Instrument.PIANO, new Note(5));
            }
        }
    }

    ArenaPlayer getKiller() {
        if (getPlayer() != null && getPlayer().getKiller() != null) {
            return game.getArenaPlayer(getPlayer().getKiller());
        } else if (lastDamager != null) {
            ArenaPlayer result = game.getArenaPlayer(lastDamager);
            lastDamager = null;
            return result;
        }
        return null;
    }
    
    void onKill() {
        score += 1;
        streak += 1;
        if (streak > 2) {
            game.announce("%s is on a kill streak of %d.", name, streak);
        }
        game.sidebar.getScore(name).setScore(score);
    }

    void setScore(int val) {
        score = Math.max(0, val);
        game.sidebar.getScore(name).setScore(score);
    }

    void addScore(int val) {
        setScore(score + val);
    }

    void setDeaths(int val) {
        deaths = val;
    }

    void addDeaths(int val) {
        setDeaths(deaths + 1);
    }

    void onDeath(Player player) {
        Players.reset(player);
        player.setScoreboard(game.scoreboard);
        deaths += 1;
        streak = 0;
        deathCooldown = 20*10;
        player.setGameMode(GameMode.SPECTATOR);
        player.getWorld().strikeLightningEffect(player.getLocation());
    }

    void onJoin(Player player) {
        if (isSpectator()) {
            player.setGameMode(GameMode.SPECTATOR);
        } else if (isPlayer()) {
            player.setGameMode(GameMode.ADVENTURE);
        }
        updateFrozenState(player);
    }

    void freeze(boolean val) {
        frozen = val;
        Player player = getPlayer();
        if (player == null) return;
        updateFrozenState(player);
    }

    void updateFrozenState(Player player) {
        if (isSpectator()) {
            player.setGameMode(GameMode.SPECTATOR);
            player.setAllowFlight(true);
            player.setFlying(true);
        } else if (frozen) {
            player.setAllowFlight(true);
            player.setFlying(true);
            player.setWalkSpeed(0);
            player.setFlySpeed(0);
        } else {
            player.setFlying(false);
            player.setAllowFlight(false);
            player.setWalkSpeed(.2f);
            player.setFlySpeed(.1f);
        }
    }

    Location getSpawnLocation() {
        if (spawnLocation == null) { spawnLocation = game.arena.dealSpawnLocation(); }
        return spawnLocation;
    }

    void setPlayer() { playerType = Type.PLAYER; }
    void setSpectator() { playerType = Type.SPECTATOR; }
}
