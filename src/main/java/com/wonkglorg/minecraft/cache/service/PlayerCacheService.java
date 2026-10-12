package com.wonkglorg.minecraft.cache.service;

import com.wonkglorg.minecraft.cache.data.PlayerProfile;
import org.bukkit.OfflinePlayer;
import org.bukkit.entity.Player;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

public interface PlayerCacheService{
	/**
	 * Returns the PlayerProfile of the given player name if cached
	 *
	 * @param name the name of the player, case-insensitive
	 * @return the uuid if found or empty
	 */
	PlayerProfile getProfileIfCached(String name);
	
	/**
	 * Returns the uuid of the given player name if cached, if a name is required (with lookups
	 * online
	 * and database lookups, use {@link #getProfile(String)}
	 *
	 * @param uuid the uuid of the player
	 * @return the name if found or empty
	 */
	@Nullable PlayerProfile getProfileIfCached(UUID uuid);
	
	/**
	 * Returns the PlayerProfile of the given player name, this method only returns a valid PlayerProfile if the player has visited the server before
	 *
	 * @param name the name of the player, case-insensitive
	 * @return the uuid if found or empty
	 */
	CompletableFuture<PlayerProfile> getProfile(String name);
	
	/**
	 * Returns the Profile given player uuid, this method only returns a valid PlayerProfile if the player has visited the server before
	 *
	 * @param uuid the uuid of the player
	 * @return the name if found or empty
	 */
	CompletableFuture<PlayerProfile> getProfile(UUID uuid);
	
	/**
	 * Returns the PlayerProfile of the given player name, if the player has not joined the server before requests it from the configuration defined web endpoint
	 *
	 * @param name the name of the player, case-insensitive
	 * @return the uuid if found or empty
	 */
	CompletableFuture<PlayerProfile> getProfileOrRequest(String name);
	
	/**
	 * Returns the Profile given player uuid, if the player has not joined the server before requests it from the configuration defined web endpoint
	 *
	 * @param uuid the uuid of the player
	 * @return the name if found or empty
	 */
	CompletableFuture<PlayerProfile> getProfileOrRequest(UUID uuid);
}