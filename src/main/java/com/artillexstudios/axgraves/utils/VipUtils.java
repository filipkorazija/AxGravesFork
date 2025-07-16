package com.artillexstudios.axgraves.utils;

import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.bukkit.permissions.PermissionAttachmentInfo;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

import static com.artillexstudios.axgraves.AxGraves.CONFIG;

public class VipUtils {
    
    // Cache for VIP despawn times to reduce frequent permission checks
    private static final Map<UUID, Integer> despawnTimeCache = new HashMap<>();
    private static final Map<UUID, Long> lastCheckTime = new HashMap<>();
    private static final long CACHE_DURATION = 30000; // 30 seconds cache
    
    /**
     * Get the despawn time for a player based on their VIP permissions
     * @param offlinePlayer The player to check permissions for
     * @return Despawn time in seconds, or default if no VIP permission found
     */
    public static int getDespawnTime(OfflinePlayer offlinePlayer) {
        // If player is offline, use default despawn time
        Player player = offlinePlayer.getPlayer();
        if (player == null) {
            return CONFIG.getInt("despawn-time-seconds", 180);
        }

        UUID playerId = player.getUniqueId();
        long currentTime = System.currentTimeMillis();
        
        // Check cache first to reduce frequent permission checks
        if (despawnTimeCache.containsKey(playerId) && lastCheckTime.containsKey(playerId)) {
            long timeSinceLastCheck = currentTime - lastCheckTime.get(playerId);
            if (timeSinceLastCheck < CACHE_DURATION) {
                return despawnTimeCache.get(playerId);
            }
        }

        int maxDespawnTime = CONFIG.getInt("despawn-time-seconds", 180);
        boolean foundVipPermission = false;

        // Debug logging for permission checking (limit spam)
        boolean debug = CONFIG.getBoolean("graveyard.debug", false);
        boolean shouldLog = debug && (!lastCheckTime.containsKey(playerId) || 
                                     (currentTime - lastCheckTime.get(playerId)) > 10000); // Log max once per 10 seconds per player

        // Check for VIP permissions and get the highest despawn time
        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            // Only check permissions that are actually granted
            if (!pai.getValue()) continue;
            
            String permission = pai.getPermission();
            
            // Check if this is a VIP permission we have configured
            if (permission.startsWith("grave.vip")) {
                // Split permission like "grave.vip1" into "grave" and "vip1"
                String[] parts = permission.split("\\.", 2);
                if (parts.length == 2) {
                    String configPath = "vip-despawn-times." + parts[0] + "." + parts[1];
                    int vipDespawnTime = CONFIG.getInt(configPath, -1);
                    
                    if (shouldLog) {
                        Bukkit.getLogger().info(String.format("[AxGraves] Checking permission '%s' at config path '%s': %d seconds", permission, configPath, vipDespawnTime));
                        
                        // Debug: show what's actually in the config section (only once)
                        if (CONFIG.getSection("vip-despawn-times") != null) {
                            Bukkit.getLogger().info("[AxGraves] Available VIP despawn times: " + CONFIG.getSection("vip-despawn-times").getKeys());
                            if (CONFIG.getSection("vip-despawn-times.grave") != null) {
                                Bukkit.getLogger().info("[AxGraves] Available grave VIP levels: " + CONFIG.getSection("vip-despawn-times.grave").getKeys());
                            }
                        }
                    }
                    
                    if (vipDespawnTime > 0) {
                        maxDespawnTime = Math.max(maxDespawnTime, vipDespawnTime);
                        foundVipPermission = true;
                        
                        if (shouldLog) {
                            Bukkit.getLogger().info(String.format("[AxGraves] Updated max despawn time to %d seconds", maxDespawnTime));
                        }
                    }
                }
            }
        }

        if (shouldLog) {
            if (foundVipPermission) {
                Bukkit.getLogger().info(String.format("[AxGraves] Final despawn time for %s: %d seconds (VIP)", player.getName(), maxDespawnTime));
            } else {
                Bukkit.getLogger().info(String.format("[AxGraves] No VIP permissions found for %s, using default: %d seconds", player.getName(), maxDespawnTime));
            }
        }

        // Cache the result to reduce future permission checks
        despawnTimeCache.put(playerId, maxDespawnTime);
        lastCheckTime.put(playerId, currentTime);

        return maxDespawnTime;
    }

    /**
     * Check if a player has any VIP permission
     * @param offlinePlayer The player to check
     * @return true if player has any grave.vip permission
     */
    public static boolean hasVipPermission(OfflinePlayer offlinePlayer) {
        Player player = offlinePlayer.getPlayer();
        if (player == null) return false;

        for (PermissionAttachmentInfo pai : player.getEffectivePermissions()) {
            // Only check permissions that are actually granted
            if (!pai.getValue()) continue;
            
            String permission = pai.getPermission();
            if (permission.startsWith("grave.vip")) {
                // Split permission like "grave.vip1" into "grave" and "vip1"
                String[] parts = permission.split("\\.", 2);
                if (parts.length == 2) {
                    String configPath = "vip-despawn-times." + parts[0] + "." + parts[1];
                    int vipDespawnTime = CONFIG.getInt(configPath, -1);
                    if (vipDespawnTime > 0) {
                        return true;
                    }
                }
            }
        }
        return false;
    }
} 