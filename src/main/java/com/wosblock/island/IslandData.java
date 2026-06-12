package com.wosblock.island;

import java.util.HashSet;
import java.util.HashMap;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.World;

public final class IslandData {
    private final UUID ownerId;
    private final Set<UUID> trustedMembers = new HashSet<>();
    private String worldName;
    private int parcelIndex = -1;
    private int centerX;
    private int centerY = 100;
    private int centerZ;
    private int radius = 75;
    private int expandNorth = 75;
    private int expandSouth = 75;
    private int expandEast = 75;
    private int expandWest = 75;
    private VisitMode visitMode = VisitMode.FRIENDLY;
    private final Map<Integer, int[]> waypoints = new HashMap<>();
    private int generatorLevel = 1;
    private long generatorXp;
    private int achievementLevel;

    public IslandData(UUID ownerId) {
        this.ownerId = ownerId;
    }

    public UUID ownerId() {
        return ownerId;
    }

    public Set<UUID> trustedMembers() {
        return trustedMembers;
    }

    public String worldName() {
        return worldName;
    }

    public void worldName(String worldName) {
        this.worldName = worldName;
    }

    public int parcelIndex() {
        return parcelIndex;
    }

    public void parcelIndex(int parcelIndex) {
        this.parcelIndex = parcelIndex;
    }

    public int centerX() {
        return centerX;
    }

    public void centerX(int centerX) {
        this.centerX = centerX;
    }

    public int centerY() {
        return centerY;
    }

    public void centerY(int centerY) {
        this.centerY = centerY;
    }

    public int centerZ() {
        return centerZ;
    }

    public void centerZ(int centerZ) {
        this.centerZ = centerZ;
    }

    public int radius() {
        return radius;
    }

    public void radius(int radius) {
        this.radius = Math.max(1, radius);
        expandNorth = Math.max(expandNorth, this.radius);
        expandSouth = Math.max(expandSouth, this.radius);
        expandEast = Math.max(expandEast, this.radius);
        expandWest = Math.max(expandWest, this.radius);
    }

    public int expandNorth() {
        return expandNorth;
    }

    public void expandNorth(int expandNorth) {
        this.expandNorth = Math.max(1, expandNorth);
    }

    public int expandSouth() {
        return expandSouth;
    }

    public void expandSouth(int expandSouth) {
        this.expandSouth = Math.max(1, expandSouth);
    }

    public int expandEast() {
        return expandEast;
    }

    public void expandEast(int expandEast) {
        this.expandEast = Math.max(1, expandEast);
    }

    public int expandWest() {
        return expandWest;
    }

    public void expandWest(int expandWest) {
        this.expandWest = Math.max(1, expandWest);
    }

    public boolean contains(Location location) {
        if (location == null || location.getWorld() == null || !location.getWorld().getName().equals(worldName)) {
            return false;
        }
        double x = location.getX();
        double z = location.getZ();
        return x >= centerX - expandWest
            && x <= centerX + expandEast
            && z >= centerZ - expandNorth
            && z <= centerZ + expandSouth;
    }

    public int width() {
        return expandWest + expandEast;
    }

    public int depth() {
        return expandNorth + expandSouth;
    }

    public VisitMode visitMode() {
        return visitMode;
    }

    public void visitMode(VisitMode visitMode) {
        this.visitMode = visitMode == null ? VisitMode.FRIENDLY : visitMode;
    }

    public Location spawnLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Island world is not loaded: " + worldName);
        }
        return new Location(world, centerX + 0.5, centerY + 2.0, centerZ + 0.5, 0.0f, 0.0f);
    }

    public Location centerLocation() {
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Island world is not loaded: " + worldName);
        }
        return new Location(world, centerX, centerY, centerZ);
    }

    public Map<Integer, int[]> waypoints() {
        return waypoints;
    }

    public void setWaypoint(int slot, Location location) {
        waypoints.put(slot, new int[] { location.getBlockX(), location.getBlockY(), location.getBlockZ() });
    }

    public Location waypointLocation(int slot) {
        int[] point = waypoints.get(slot);
        if (point == null) {
            return null;
        }
        World world = Bukkit.getWorld(worldName);
        if (world == null) {
            throw new IllegalStateException("Island world is not loaded: " + worldName);
        }
        return new Location(world, point[0] + 0.5, point[1], point[2] + 0.5);
    }

    public int generatorLevel() {
        return generatorLevel;
    }

    public void generatorLevel(int generatorLevel) {
        this.generatorLevel = Math.max(1, generatorLevel);
    }

    public long generatorXp() {
        return generatorXp;
    }

    public void addGeneratorXp(long amount) {
        generatorXp += Math.max(0, amount);
    }

    public int achievementLevel() {
        return achievementLevel;
    }

    public void achievementLevel(int achievementLevel) {
        this.achievementLevel = Math.max(0, achievementLevel);
    }
}
