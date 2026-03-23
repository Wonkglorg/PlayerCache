package com.wonkglorg.cache.data;

import com.wonkglorg.cache.PlayerCache;
import org.bukkit.OfflinePlayer;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;

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
		boolean isBedrock = isBedrock(player.getUniqueId());
		return new PlayerProfile(player.getUniqueId(), player.getName(), player.getFirstPlayed(), player.getLastSeen(), isBedrock);
	}
	
	public static boolean isBedrock(UUID uuid) {
		if(PlayerCache.floodGateEnabled){
			if(FloodgateApi.getInstance().isFloodgateId(uuid)){
				return true;
			}
		}
		return false;
	}
	
}
