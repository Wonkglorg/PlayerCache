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

public record PlayerProfile(@NotNull UUID uuid, String name, long firstJoined, long lastSeen, boolean isBedrock){
	public static final PlayerProfile EMPTY_PROFILE = new PlayerProfile(UUID.fromString("00000000-0000-0000-0000-000000000000"), null, 0, 0, false);
	
	public PlayerProfile(@NotNull UUID uuid, String name, long firstJoined, long lastSeen) {
		this(uuid, name, firstJoined, lastSeen, isBedrock(uuid));
	}
	
	public PlayerProfile setLastSeen(long lastSeen) {
		return new PlayerProfile(this.uuid, this.name, this.firstJoined, lastSeen, this.isBedrock);
	}
	
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
	 * @return the offline player associated with this profile
	 */
	public OfflinePlayer getOfflinePlayer() {
		return Bukkit.getOfflinePlayer(uuid);
	}
	/**
	 * @return the player associated with this profile or null if not online
	 */
	public @Nullable Player getPlayer(){
		return Bukkit.getPlayer(uuid);
	}
	
}
