package com.artillexstudios.axgraves.utils;

import com.sk89q.worldedit.bukkit.BukkitAdapter;
import com.sk89q.worldguard.WorldGuard;
import com.sk89q.worldguard.protection.ApplicableRegionSet;
import com.sk89q.worldguard.protection.regions.ProtectedRegion;
import com.sk89q.worldguard.protection.regions.RegionContainer;
import com.sk89q.worldguard.protection.regions.RegionQuery;
import com.artillexstudios.axgraves.grave.Grave;
import com.artillexstudios.axgraves.grave.SpawnedGraves;
import org.bukkit.Bukkit;
import org.bukkit.Location;
import org.bukkit.Material;
import org.bukkit.World;
import org.bukkit.block.Block;

import java.util.ArrayList;
import java.util.List;
import java.util.Random;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;

public class GraveyardUtils {
    
    private static final Random random = new Random();
    private static boolean worldGuardAvailable = false;
    
    static {
        try {
            Class.forName("com.sk89q.worldguard.WorldGuard");
            worldGuardAvailable = true;
        } catch (ClassNotFoundException e) {
            worldGuardAvailable = false;
        }
    }
    
    /**
     * Check if the graveyard system is enabled and WorldGuard is available
     */
    public static boolean isGraveyardSystemEnabled() {
        return CONFIG.getBoolean("graveyard.enabled", false) && worldGuardAvailable;
    }
    
    /**
     * Find a suitable location for a grave in a graveyard
     * @param deathLocation The original death location
     * @return A suitable graveyard location, or null if none found
     */
    public static Location findGraveyardLocation(Location deathLocation) {
        if (!isGraveyardSystemEnabled()) {
            if (CONFIG.getBoolean("graveyard.debug", false)) {
                Bukkit.getLogger().info("[AxGraves] Graveyard system is disabled or WorldGuard not available");
            }
            return null;
        }
        
        String worldName = deathLocation.getWorld().getName();
        List<String> regionNames = CONFIG.getStringList("graveyard.regions." + worldName);
        
        if (regionNames == null || regionNames.isEmpty()) {
            if (CONFIG.getBoolean("graveyard.debug", false)) {
                Bukkit.getLogger().info(String.format("[AxGraves] No graveyard regions configured for world '%s'", worldName));
            }
            return null;
        }
        
        List<Material> allowedBlocks = getAllowedBlocks();
        if (allowedBlocks.isEmpty()) {
            Bukkit.getLogger().warning("[AxGraves] No valid allowed-blocks configured for graveyard system!");
            return null;
        }
        
        if (CONFIG.getBoolean("graveyard.debug", false)) {
            Bukkit.getLogger().info(String.format("[AxGraves] Attempting to find graveyard location in %d regions for world '%s'", regionNames.size(), worldName));
        }
        
        // Shuffle region names to randomize selection
        List<String> shuffledRegions = new ArrayList<>(regionNames);
        java.util.Collections.shuffle(shuffledRegions, random);
        
        for (String regionName : shuffledRegions) {
            Location graveyardLocation = findLocationInRegion(deathLocation.getWorld(), regionName, allowedBlocks);
            if (graveyardLocation != null) {
                return graveyardLocation;
            }
        }
        
        if (CONFIG.getBoolean("graveyard.debug", false)) {
            Bukkit.getLogger().warning(String.format("[AxGraves] Failed to find any suitable graveyard location in %d regions", shuffledRegions.size()));
        }
        
        return null;
    }
    
    /**
     * Get the list of allowed blocks from config
     */
    private static List<Material> getAllowedBlocks() {
        List<Material> allowedBlocks = new ArrayList<>();
        List<String> configBlocks = CONFIG.getStringList("graveyard.allowed-blocks");
        
        for (String blockName : configBlocks) {
            try {
                Material material = Material.valueOf(blockName.toUpperCase());
                allowedBlocks.add(material);
            } catch (IllegalArgumentException e) {
                Bukkit.getLogger().warning("Invalid block material in graveyard config: " + blockName);
            }
        }
        
        return allowedBlocks;
    }
    
    /**
     * Find a suitable location within a specific WorldGuard region
     */
    private static Location findLocationInRegion(World world, String regionName, List<Material> allowedBlocks) {
        if (!worldGuardAvailable) {
            return null;
        }
        
        try {
            RegionContainer container = WorldGuard.getInstance().getPlatform().getRegionContainer();
            com.sk89q.worldguard.protection.managers.RegionManager regions = container.get(BukkitAdapter.adapt(world));
            
            if (regions == null) {
                return null;
            }
            
            ProtectedRegion region = regions.getRegion(regionName);
            if (region == null) {
                return null;
            }
            
            // Get region bounds
            com.sk89q.worldedit.math.BlockVector3 min = region.getMinimumPoint();
            com.sk89q.worldedit.math.BlockVector3 max = region.getMaximumPoint();
            
            // Try to find a suitable location within the region
            int maxAttempts = 100; // Increased attempts for better success rate
            boolean foundValidGround = false;
            
            for (int attempt = 0; attempt < maxAttempts; attempt++) {
                int x = random.nextInt(max.getBlockX() - min.getBlockX() + 1) + min.getBlockX();
                int z = random.nextInt(max.getBlockZ() - min.getBlockZ() + 1) + min.getBlockZ();
                
                // Find the ground level at this x,z coordinate within the region bounds
                Location groundLocation = findGroundLevel(world, x, z, min.getBlockY(), max.getBlockY(), allowedBlocks);
                
                if (groundLocation != null) {
                    foundValidGround = true;
                    
                    // Check if there's an existing grave at this location
                    if (!isLocationOccupied(groundLocation)) {
                        // Found a suitable location!
                        // Match the original system's coordinate placement: ground block + 0.5 (center)
                        Location graveLocation = groundLocation.clone().add(0.5, 0.5, 0.5);
                        
                        // Debug logging for successful placement
                        if (CONFIG.getBoolean("graveyard.debug", false)) {
                            Bukkit.getLogger().info(String.format("[AxGraves] Found graveyard location in region '%s' at %.1f,%.1f,%.1f (attempt %d)", 
                                regionName, graveLocation.getX(), graveLocation.getY(), graveLocation.getZ(), attempt + 1));
                        }
                        
                        return graveLocation;
                    }
                }
            }
            
            // Debug logging for failed placement
            if (CONFIG.getBoolean("graveyard.debug", false)) {
                if (!foundValidGround) {
                    Bukkit.getLogger().warning(String.format("[AxGraves] No valid ground found in region '%s' after %d attempts. Check allowed-blocks config.", regionName, maxAttempts));
                } else {
                    Bukkit.getLogger().warning(String.format("[AxGraves] All valid locations in region '%s' are occupied after %d attempts.", regionName, maxAttempts));
                }
            }
            
        } catch (Exception e) {
            Bukkit.getLogger().warning("Error finding graveyard location: " + e.getMessage());
        }
        
        return null;
    }
    
    /**
     * Find the ground level at specific X,Z coordinates within Y bounds
     * @param world The world to search in
     * @param x The X coordinate
     * @param z The Z coordinate  
     * @param minY The minimum Y level to search
     * @param maxY The maximum Y level to search
     * @param allowedBlocks List of blocks that can serve as ground
     * @return The ground location, or null if no suitable ground found
     */
    private static Location findGroundLevel(World world, int x, int z, int minY, int maxY, List<Material> allowedBlocks) {
        // Start from the top and work downward to find the highest suitable block
        for (int y = maxY; y >= minY; y--) {
            Location loc = new Location(world, x, y, z);
            Block block = loc.getBlock();
            
            // Check if this is a suitable ground block (removed overly strict solid requirement)
            if (allowedBlocks.contains(block.getType())) {
                // Check if there's adequate space above for the grave
                Location above1 = loc.clone().add(0, 1, 0);
                
                // More lenient air space check - only need 1 block of air minimum
                if (isPassableBlock(above1.getBlock())) {
                    return loc;
                }
            }
        }
        
        return null;
    }
    
    /**
     * Check if a block is passable (air or non-solid blocks like grass, flowers, etc.)
     */
    private static boolean isPassableBlock(Block block) {
        Material type = block.getType();
        // Air is always passable
        if (type.isAir()) {
            return true;
        }
        // Common non-solid blocks that should be treated as passable
        return type == Material.TALL_GRASS || 
               type == Material.GRASS || 
               type == Material.FERN ||
               type == Material.LARGE_FERN ||
               type == Material.DEAD_BUSH ||
               type == Material.DANDELION ||
               type == Material.POPPY ||
               type == Material.BLUE_ORCHID ||
               type == Material.ALLIUM ||
               type == Material.AZURE_BLUET ||
               type == Material.RED_TULIP ||
               type == Material.ORANGE_TULIP ||
               type == Material.WHITE_TULIP ||
               type == Material.PINK_TULIP ||
               type == Material.OXEYE_DAISY ||
               type == Material.CORNFLOWER ||
               type == Material.LILY_OF_THE_VALLEY ||
               type == Material.SNOW ||
               !type.isSolid();
    }
    

    
    /**
     * Check if a location is already occupied by an existing grave
     */
    private static boolean isLocationOccupied(Location location) {
        for (Grave grave : SpawnedGraves.getGraves()) {
            Location graveLocation = grave.getLocation();
            
            if (graveLocation.getWorld().equals(location.getWorld()) && 
                graveLocation.getBlockX() == location.getBlockX() &&
                graveLocation.getBlockY() == location.getBlockY() &&
                graveLocation.getBlockZ() == location.getBlockZ()) {
                return true;
            }
        }
        
        return false;
    }
    
    /**
     * Check if WorldGuard is available
     */
    public static boolean isWorldGuardAvailable() {
        return worldGuardAvailable;
    }
} 