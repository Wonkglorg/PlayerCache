package com.wonkglorg.minecraft.cache.data;

import com.wonkglorg.minecraft.cache.PlayerCache;
import static com.wonkglorg.minecraft.cache.data.DataCache.normalizeName;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.Objects;
import java.util.UUID;

/**
 * Represents a players profile
 * @param uuid the uuid of the player
 * @param name the last known cached name of the player
 * @param firstJoined when they first joined the server or 0 if never
 * @param lastSeen when the most recent time when they last logged in the server or 0 if the person has never been on the server or joined for the first time and has not left since
 * @param isBedrock if the account is a geyser bedrock account (only works if floodgate is present and active on the server)
 */
public record PlayerProfile(@NotNull UUID uuid, String name, long firstJoined, long lastSeen, boolean isBedrock){
	public static final PlayerProfile EMPTY_PROFILE = new PlayerProfile(UUID.fromString("00000000-0000-0000-0000-000000000000"), null, 0, 0, false);
	
	public PlayerProfile(@NotNull UUID uuid, String name, long firstJoined, long lastSeen) {
		this(uuid, name, firstJoined, lastSeen, isBedrock(uuid));
	}
	
	public PlayerProfile setLastSeen(long lastSeen) {
		return new PlayerProfile(this.uuid, this.name, this.firstJoined, lastSeen, this.isBedrock);
	}
	
	/**
	 * @return true if the player has played on this server before
	 */
	public boolean hasPlayedBefore() {
		return firstJoined > 0;
	}
	
	public static PlayerProfile from(OfflinePlayer player) {
		Objects.requireNonNull(player.getName(), "Player Name should not be null");
		return new PlayerProfile(player.getUniqueId(), normalizeName(player.getName()), player.getFirstPlayed(), player.getLastSeen());
	}
	
	public static boolean isBedrock(UUID uuid) {
		if(PlayerCache.floodGateEnabled){
			return FloodgateApi.getInstance().isFloodgateId(uuid);
		}
		return false;
	}
	
	/**
	 * @return true if the player is currently online
	 */
	public boolean isOnline() {
		return Bukkit.getPlayer(uuid) != null;
	}
	
	/**
	 * @return the offline player associated with this profile
	 */
	public OfflinePlayer getOfflinePlayer() {
		return Bukkit.getOfflinePlayer(uuid);
	}
	
	/**
	 * @return the player associated with this profile or null if not online
	 */
	public @Nullable Player getPlayer() {
		return Bukkit.getPlayer(uuid);
	}
	
}
