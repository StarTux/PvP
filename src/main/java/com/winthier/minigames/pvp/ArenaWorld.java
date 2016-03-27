package com.winthier.minigames.pvp;

import java.util.ArrayList;
import java.util.List;
import lombok.Getter;
import lombok.NonNull;
import lombok.RequiredArgsConstructor;
import org.bukkit.Chunk;
import org.bukkit.Difficulty;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.BlockState;
import org.bukkit.block.Sign;
import org.bukkit.util.Vector;

@RequiredArgsConstructor
@Getter
class ArenaWorld
{
    @NonNull final PvP game;
    @NonNull final World world;
    List<Location> spawnLocations = new ArrayList<>();
    List<String> credits = new ArrayList<>();
    int spawnLocationIter = 0;

    void init()
    {
        world.setTime(12000);
        world.setGameRuleValue("doDaylightCycle", "false");
        world.setGameRuleValue("doFireTick", "false");
        world.setGameRuleValue("doMobLoot", "false");
        world.setGameRuleValue("doTileDrops", "false");
        world.setGameRuleValue("mobGriefing", "false");
        world.setPVP(true);
        world.setDifficulty(Difficulty.NORMAL);
        scan();
    }

    private void scan()
    {
        Chunk chunk = world.getSpawnLocation().getChunk();
        int RADIUS = 8;
        for (int x = chunk.getX() - RADIUS; x <= chunk.getX() + RADIUS; ++x) {
            for (int z = chunk.getZ() - RADIUS; z <= chunk.getZ() + RADIUS; ++z) {
                scanChunk(world.getChunkAt(x, z));
            }
        }
    }

    void scanChunk(Chunk chunk)
    {
        for (BlockState state : chunk.getTileEntities()) {
            if (state instanceof Sign) scanSign((Sign)state);
        }
    }

    void scanSign(Sign sign)
    {
        String firstLine = sign.getLine(0).toLowerCase();
        if ("[spawn]".equals(firstLine)) {
            spawnLocations.add(cookLocation(sign.getLocation()));
        } else if ("[credits]".equals(firstLine)) {
            for (int i = 1; i < 4; ++i) {
                String name = sign.getLine(i);
                if (name != null && !name.isEmpty()) {
                    credits.add(name);
                }
            }
        } else if ("[time]".equals(firstLine)) {
            long time = 0;
            String arg = sign.getLine(1).toLowerCase();
            if ("day".equals(arg)) {
                time = 1000;
            } else if ("night".equals(arg)) {
                time = 13000;
            } else if ("noon".equals(arg)) {
                time = 6000;
            } else if ("midnight".equals(arg)) {
                time = 18000;
            } else {
                try {
                    time = Long.parseLong(sign.getLine(1));
                } catch (NumberFormatException nfe) {}
            }
            world.setTime(time);
            if ("lock".equalsIgnoreCase(sign.getLine(2))) {
                world.setGameRuleValue("doDaylightCycle", "false");
            } else {
                world.setGameRuleValue("doDaylightCycle", "true");
            }
        } else if ("[weather]".equals(firstLine)) {
            int duration = 60;
            if (!sign.getLine(2).isEmpty()) {
                String arg = sign.getLine(2).toLowerCase();
                if ("lock".equals(arg)) {
                    duration = 99999;
                } else {
                    try {
                        duration = Integer.parseInt(sign.getLine(2));
                    } catch (NumberFormatException nfe) {}
                }
            }
            String weather = sign.getLine(1).toLowerCase();
            if ("clear".equals(weather)) {
                world.setStorm(false);
                world.setThundering(false);
            } else if ("rain".equals(weather)) {
                world.setStorm(true);
                world.setThundering(false);
            } else if ("thunder".equals(weather)) {
                world.setStorm(true);
                world.setThundering(true);
            }
            world.setWeatherDuration(duration * 20);
        } else {
            return;
        }
        sign.setType(Material.AIR);
        sign.update(true, false);
    }

    Location cookLocation(Location loc)
    {
        loc = loc.add(0.5, 0.5, 0.5);
        Vector vec = world.getSpawnLocation().toVector().subtract(loc.toVector());
        loc = loc.setDirection(vec);
        return loc;
    }

    Location dealSpawnLocation()
    {
        if (spawnLocations.isEmpty()) return world.getSpawnLocation();
        Location result = spawnLocations.get(0);
        double resultDist = 0.0;
        for (Location loc : spawnLocations) {
            double minDist = Double.MAX_VALUE;
            for (ArenaPlayer ap : game.getArenaPlayers()){
                if (ap.isPlayer() && ap.isOnline()) {
                    double dist = loc.distanceSquared(ap.getPlayer().getLocation());
                    if (dist < minDist) {
                        minDist = dist;
                    }
                }
            }
            if (minDist > resultDist) {
                result = loc;
                resultDist = minDist;
            }
        }
        return result;
    }

    Location randomSpawnLocation() {
        if (spawnLocations.isEmpty()) return world.getSpawnLocation();
        return spawnLocations.get(Math.abs(game.random.nextInt()) % spawnLocations.size());
    }

    String credits() {
        if (credits.isEmpty()) return null;
        StringBuilder sb = new StringBuilder(credits.get(0));
        for (int i = 1; i < credits.size(); ++i) {
            sb.append(" ").append(credits.get(i));
        }
        return sb.toString();
    }
}
