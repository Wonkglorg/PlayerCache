package com.wonkglorg.minecraft.cache.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wonkglorg.minecraft.cache.PlayerCache;
import com.wonkglorg.minecraft.cache.PluginLogger;
import com.wonkglorg.minecraft.cache.service.PlayerCacheService;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.stream.Stream;

public class DataCache implements PlayerCacheService{
	private static final ExecutorService NAME_RESOLVE_EXECUTOR = Executors.newFixedThreadPool(5);
	/**
	 * Quick access cache uuid to player profile
	 */
	private static final Map<UUID, PlayerProfile> UUID_CACHE = new ConcurrentHashMap<>();
	/**
	 * Quick access cache name lowercase to player profile
	 */
	private static final Map<String, PlayerProfile> NAME_CACHE = new ConcurrentHashMap<>();
	/**
	 * Any recent invalid requests, so it doesn't needlessly lookup the same users multiple times
	 */
	private static final Set<String> INVALID_USER_REQUESTS = ConcurrentHashMap.newKeySet();
	
	private final PlayerDB playerDB;
	private final FileConfiguration config;
	
	public DataCache(JavaPlugin plugin) {
		this.playerDB = new PlayerDB(plugin);
		plugin.saveDefaultConfig();
		config = plugin.getConfig();
		config.options().copyDefaults(true);
		plugin.saveConfig();
		reload();
	}
	
	private boolean isValidMinecraftName(String name) {
		return name != null && name.length() > 2;
	}
	
	public static String normalizeName(String name) {
		return name.trim().toLowerCase();
	}
	
	public PlayerProfile getProfileIfCached(String name) {
		if(!isValidMinecraftName(name)){
			return null;
		}
		name = normalizeName(name);
		PlayerProfile profile = NAME_CACHE.get(name);
		
		if(profile != null){
			return profile;
		}
		
		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached(name);
		if(offlinePlayer == null){
			return null;
		}
		
		PlayerProfile newProfile = PlayerProfile.from(offlinePlayer);
		
		cacheUserToDb(newProfile);
		return newProfile;
	}
	
	public @Nullable PlayerProfile getProfileIfCached(UUID uuid) {
		if(uuid == null){
			return null;
		}
		PlayerProfile profile = UUID_CACHE.get(uuid);
		
		if(profile != null){
			return profile;
		}
		
		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);
		
		String name = offlinePlayer.getName();
		if(name == null){
			return null;
		}
		
		PlayerProfile newProfile = PlayerProfile.from(offlinePlayer);
		
		cacheUserToDb(newProfile);
		return newProfile;
	}
	
	public CompletableFuture<PlayerProfile> getProfile(String name) {
		if(!isValidMinecraftName(name)){
			return null;
		}
		name = normalizeName(name);
		PlayerProfile cached = getProfileIfCached(name);
		if(cached != null){
			return CompletableFuture.completedFuture(cached);
		}
		if(INVALID_USER_REQUESTS.contains(name)){
			return CompletableFuture.completedFuture(null);
		}
		
		var future = playerDB.getProfile(name);
		
		future.thenAccept(p -> {
			if(p != null){
				cacheUser(p);
			}
		});
		
		return future;
	}
	
	public CompletableFuture<PlayerProfile> getProfile(UUID uuid) {
		if(uuid == null){
			return CompletableFuture.completedFuture(null);
		}
		
		var cachedProfile = getProfileIfCached(uuid);
		if(cachedProfile != null){
			return CompletableFuture.completedFuture(cachedProfile);
		}
		
		var future = playerDB.getProfile(uuid);
		
		future.thenAccept(p -> {
			if(p != null){
				cacheUser(p);
			}
		});
		
		return future;
	}
	
	/*
	@Override
	public CompletableFuture<PlayerProfile> getProfileOrRequest(String name) {
		if(!isValidMinecraftName(name)){
			return null;
		}
		final String normalizedName = normalizeName(name);
		CompletableFuture<PlayerProfile> future = new CompletableFuture<>();
		getProfile(normalizedName).thenAccept(p -> {
			if(p != null){
				future.complete(p);
			} else {
				resolveUuid(normalizedName, 3).thenAccept(uuid -> {
					PlayerProfile profile = new PlayerProfile(uuid, name, 0, 0);
					cacheUserToDb(profile);
					future.complete(profile);
				});
			}
		});
		return future;
	}
	
	@Override
	public CompletableFuture<PlayerProfile> getProfileOrRequest(UUID uuid) {
		CompletableFuture<PlayerProfile> future = new CompletableFuture<>();
		resolveUuid(uuid, 3).thenAccept(name -> {
			PlayerProfile profile = new PlayerProfile(uuid, name, 0, 0);
			cacheUserToDb(profile);
			future.complete(profile);
		});
	}
	
		/**
	 * Resolves a player uuid from their name using mcprofile.io
	 * Supports Java and Bedrock (Floodgate) players.
	 *
	 * @param retries number of times to retry on failure
	 */
	/*
	@SuppressWarnings("deprecation")
	private CompletableFuture<UUID> resolveUuid(String name, int retries) {
		return CompletableFuture.supplyAsync(() -> {
			int backoff = 1000;
			for(int attempt = 1; attempt <= retries; attempt++){
				try{
					PluginLogger.info("Resolving name with url: " + urlStr);
					URL url = new URL(urlStr);
					HttpURLConnection con = (HttpURLConnection) url.openConnection();
					con.setRequestMethod("GET");
					con.setConnectTimeout(5000);
					con.setReadTimeout(15000);
					con.setRequestProperty("User-Agent", "Java/MCPlugin");
					
					int code = con.getResponseCode();
					if(code == 429){
						PluginLogger.warn("Rate-limited by mcprofile.io. Retrying in " + backoff + "ms");
						Thread.sleep(backoff);
						backoff *= 2;
						continue;
					}
					
					if(code != 200){
						PluginLogger.warn("Expected 200 response was:" + con.getResponseCode());
						continue;
					}
					
					try(InputStream in = con.getInputStream(); Reader r = new InputStreamReader(in)){
						JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
						String name = isBedrock ? obj.has("gamertag") ? obj.get("gamertag").getAsString() : null : obj.has("username") ? obj.get(
								"username").getAsString() : null;
						
						if(name != null){
							return (isBedrock ? config.getString("bedrock_prefix") : "") + name;
						}
					}
				} catch(Exception e){
					PluginLogger.debug("Attempt %d to resolve UUID %s failed: %s".formatted(attempt, uuid, e.getMessage()));
				}
				
				try{
					Thread.sleep(1000L * attempt);
				} catch(InterruptedException ignored){
				}
			}
			
			return null;
		}, NAME_RESOLVE_EXECUTOR);
	}
	
	
	 */
	
	public void reload() {
		PlayerCache.getInstance().reloadConfig();
		UUID_CACHE.clear();
		NAME_CACHE.clear();
		INVALID_USER_REQUESTS.clear();
		for(var player : Bukkit.getOnlinePlayers()){
			PlayerProfile profile = PlayerProfile.from(player);
			UUID_CACHE.put(profile.uuid(), profile);
			NAME_CACHE.put(profile.name().toLowerCase(), profile);
		}
		if(config.getBoolean("index_offline_players", false)){
			indexOfflinePlayers();
			config.set("index_offline_players", false);
			PlayerCache.getInstance().saveConfig();
		}
	}
	
	public void updateUserLastSeen(@NotNull Player player) {
		PlayerProfile profile = UUID_CACHE.get(player.getUniqueId());
		if(profile == null){
			profile = PlayerProfile.from(player);
		}
		profile = profile.setLastSeen(System.currentTimeMillis());
		UUID_CACHE.put(player.getUniqueId(), profile);
		NAME_CACHE.put(player.getName().toLowerCase(), profile);
		playerDB.updateLastSeen(profile);
		
	}
	
	public void cacheUser(PlayerProfile profile) {
		UUID_CACHE.put(profile.uuid(), profile);
		NAME_CACHE.put(profile.name(), profile);
		INVALID_USER_REQUESTS.remove(profile.name());
	}
	
	public void cacheUserToDb(PlayerProfile profile) {
		UUID_CACHE.put(profile.uuid(), profile);
		NAME_CACHE.put(profile.name(), profile);
		INVALID_USER_REQUESTS.remove(profile.name());
		playerDB.addProfile(profile);
	}
	
	public void cacheUser(Player player) {
		cacheUserToDb(PlayerProfile.from(player));
	}
	
	private void indexOfflinePlayers() {
		List<OfflinePlayer> offlinePlayers = new ArrayList<>();
		try(Stream<Path> list = Files.list(Path.of("world", "playerdata"))){
			list.forEach(path -> {
				if(!path.toString().endsWith(".dat")){
					return;
				}
				try{
					UUID uuid = UUID.fromString(path.getFileName().toString().replace(".dat", ""));
					OfflinePlayer player = Bukkit.getOfflinePlayer(uuid);
					if(player.getName() == null){
						return;
					}
					offlinePlayers.add(player);
					if(offlinePlayers.size() > 50){
						PluginLogger.info("Batch inserting!");
						playerDB.addBatchProfile(offlinePlayers);
						offlinePlayers.clear();
					}
					
				} catch(Exception e){
					PluginLogger.error("Unable to get uuid from file name" + path);
				}
				
			});
			if(!offlinePlayers.isEmpty()){
				playerDB.addBatchProfile(offlinePlayers);
			}
		} catch(IOException e){
			PluginLogger.error("Unable to index offline players!");
		}
	}
	
	public PlayerDB getPlayerDB() {
		return playerDB;
	}
}
