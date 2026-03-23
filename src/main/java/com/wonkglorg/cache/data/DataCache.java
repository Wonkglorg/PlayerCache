package com.wonkglorg.cache.data;

import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class DataCache{
	private static final Map<UUID, String> UUID_NAME_MAP = new HashMap<>();
	private static final Map<String, UUID> NAME_UUID_MAP = new HashMap<>();
	private final PlayerDB playerDB;
	
	public DataCache(PlayerDB playerDB) {
		this.playerDB = playerDB;
	}
	
	/**
	 * Returns the uuid of the given player name if cached, if a name is required (with lookups online and database lookups, use {@link #getUuid(String)}
	 *
	 * @param name the name of the player, case insensitive
	 * @return the uuid if found or empty
	 */
	public Optional<UUID> getUuidIfCached(String name) {
		if(name == null) return Optional.empty();
		return Optional.ofNullable(NAME_UUID_MAP.getOrDefault(name.toLowerCase(), null));
	}
	
	public CompletableFuture<Optional<UUID>> getUuid(String name, boolean clearCached) {
		if(name == null) return CompletableFuture.completedFuture(Optional.empty());
		
		return Optional.ofNullable(NAME_UUID_MAP.getOrDefault(name.toLowerCase(), null));
	}
	
	/**
	 * Returns the uuid of the given player name if cached, if a name is required (with lookups online and database lookups, use {@link #getName(UUID)} (String)}
	 *
	 * @param uuid the uuid of the player
	 * @return the name if found or empty
	 */
	public Optional<String> getNameIfCached(UUID uuid) {
		if(uuid == null) return Optional.empty();
		return Optional.ofNullable(UUID_NAME_MAP.getOrDefault(uuid, null));
	}
	
	/**
	 * Returns the uuid of the given player name, this may be slower if not cached if a name is required (with lookups online and database lookups, use {@link #getName(UUID)} (String)}
	 *
	 * @param uuid the uuid of the player
	 * @return the name if found or empty
	 */
	public CompletableFuture<Optional<String>> getName(UUID uuid) {
		if(uuid == null) return Optional.empty();
		return Optional.ofNullable(UUID_NAME_MAP.getOrDefault(uuid, null));
	}
	
}
