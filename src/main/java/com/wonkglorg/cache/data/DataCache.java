package com.wonkglorg.cache.data;

import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import com.wonkglorg.cache.PlayerCache;
import com.wonkglorg.cache.PluginLogger;
import com.wonkglorg.cache.RegionScheduler;
import org.bukkit.Bukkit;
import org.bukkit.OfflinePlayer;
import org.bukkit.configuration.file.FileConfiguration;
import org.bukkit.entity.Player;
import org.bukkit.plugin.java.JavaPlugin;
import org.geysermc.floodgate.api.FloodgateApi;
import org.jetbrains.annotations.NotNull;
import org.jspecify.annotations.Nullable;

import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.Reader;
import java.net.HttpURLConnection;
import java.net.URL;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class DataCache {
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
	 * Any recent invalid cache requests, so it doesn't needlessly lookup the same users multiple
	 * times
	 */
	private static final Set<String> INVALID_USER_REQUESTS_CACHE = new HashSet<>();
	/**
	 * ANy recent invalid requests, so it doesn't needlessly lookup the same users multiple times
	 */
	private static final Set<String> INVALID_USER_REQUESTS = new HashSet<>();
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

	/**
	 * Returns the PlayerProfile of the given player name if cached
	 *
	 * @param name the name of the player, case insensitive
	 * @return the uuid if found or empty
	 */
	public PlayerProfile getProfileIfCached(String name) {
		if (!isValidMinecraftName(name)) {
			return null;
		}
		name = name.toLowerCase();
		if (INVALID_USER_REQUESTS_CACHE.contains(name)) {
			return null;
		}
		PlayerProfile profile = NAME_CACHE.get(name);

		if (profile != null) {
			return profile;
		}

		PlayerProfile dbProfile = playerDB.getProfile(name).join();
		if (dbProfile != null) {
			cacheUser(dbProfile);
			return dbProfile;
		}

		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayerIfCached(name);
		if (offlinePlayer == null) {
			INVALID_USER_REQUESTS_CACHE.add(name);
			return null;
		}

		PlayerProfile newProfile = PlayerProfile.from(offlinePlayer);

		cacheUserToDb(newProfile);
		return newProfile;
	}

	/**
	 * Returns the PlayerProfile of the given player name
	 *
	 * @param name the name of the player, case insensitive
	 * @return the uuid if found or empty
	 */
	public CompletableFuture<PlayerProfile> getProfile(String name) {
		if (!isValidMinecraftName(name)) {
			return CompletableFuture.completedFuture(null);
		}
		final String lowerName = name.toLowerCase();
		PlayerProfile cached = getProfileIfCached(lowerName);
		if (cached != null) {
			return CompletableFuture.completedFuture(cached);
		}
		if (INVALID_USER_REQUESTS.contains(lowerName)) {
			return CompletableFuture.completedFuture(null);
		}
		return RegionScheduler.getInstance().runAsync(() -> {
			try {
				OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(lowerName);
				PlayerProfile profile = PlayerProfile.from(offlinePlayer);
				cacheUserToDb(profile);
				return profile;
			} catch (Exception e) {
				PluginLogger.error("No player with the specified name exists!");
				INVALID_USER_REQUESTS.add(lowerName);
				return null;
			}
		});
	}

	public void reload() {
		PlayerCache.getInstance().reloadConfig();
		UUID_CACHE.clear();
		NAME_CACHE.clear();
		INVALID_USER_REQUESTS.clear();
		INVALID_USER_REQUESTS_CACHE.clear();
		for (var player : Bukkit.getOnlinePlayers()) {
			PlayerProfile profile = PlayerProfile.from(player);
			UUID_CACHE.put(profile.uuid(), profile);
			NAME_CACHE.put(profile.name().toLowerCase(), profile);
		}
		if (config.getBoolean("index_offline_players", false)) {
			indexOfflinePlayers();
			config.set("index_offline_players", false);
			PlayerCache.getInstance().saveConfig();
		}
	}

	/**
	 * Returns the uuid of the given player name if cached, if a name is required (with lookups 
	 * online
	 * and database lookups, use {@link #getProfile(String)}
	 *
	 * @param uuid the uuid of the player
	 * @return the name if found or empty
	 */
	public @Nullable PlayerProfile getProfileIfCached(UUID uuid) {
		if (uuid == null) {
			return null;
		}
		PlayerProfile profile = UUID_CACHE.get(uuid);

		if (profile != null) {
			return profile;
		}

		PlayerProfile dbProfile = playerDB.getProfile(uuid).join();
		if (dbProfile != null) {
			cacheUser(dbProfile);
			return dbProfile;
		}

		OfflinePlayer offlinePlayer = Bukkit.getOfflinePlayer(uuid);

		String name = offlinePlayer.getName();
		if (name == null) {
			return null;
		}

		PlayerProfile newProfile = PlayerProfile.from(offlinePlayer);

		cacheUserToDb(newProfile);
		return newProfile;
	}

	/**
	 * Returns the Profile given player uuid, this may be slow when looking up profile data via web
	 * request use {@link #getProfileIfCached(UUID)} instead to not send web requests
	 *
	 * @param uuid the uuid of the player
	 * @return the name if found or empty
	 */
	public CompletableFuture<PlayerProfile> getProfile(UUID uuid) {
		var cachedProfile = getProfileIfCached(uuid);
		if (cachedProfile != null) {
			return CompletableFuture.completedFuture(cachedProfile);
		}
		CompletableFuture<PlayerProfile> future = new CompletableFuture<>();
		resolveName(uuid, 3).thenAccept(name -> {
			PlayerProfile profile = new PlayerProfile(uuid, name, 0, 0);
			cacheUserToDb(profile);
			future.complete(profile);
		});

		return future;
	}

	public void updateUserLastSeen(@NotNull Player player) {
		PlayerProfile profile = UUID_CACHE.get(player.getUniqueId());
		if (profile == null) {
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

	/**
	 * Resolves a player name from UUID using mcprofile.io
	 * Supports Java and Bedrock (Floodgate) players.
	 *
	 * @param retries number of times to retry on failure
	 */
	@SuppressWarnings("deprecation")
	private CompletableFuture<String> resolveName(UUID uuid, int retries) {
		return CompletableFuture.supplyAsync(() -> {
			int backoff = 1000;
			for (int attempt = 1; attempt <= retries; attempt++) {
				try {
					boolean isBedrock = false;
					if (PlayerCache.floodGateEnabled) {
						FloodgateApi api = FloodgateApi.getInstance();
						if (api != null && api.isFloodgateId(uuid)) {
							isBedrock = true;
						}
					}

					String urlStr = isBedrock ? config.getString("url_resolve_bedrock") + uuid
							: config.getString("url_resolve_java") + uuid;

					PluginLogger.info("Resolving name with url: " + urlStr);
					URL url = new URL(urlStr);
					HttpURLConnection con = (HttpURLConnection) url.openConnection();
					con.setRequestMethod("GET");
					con.setConnectTimeout(5000);
					con.setReadTimeout(15000);
					con.setRequestProperty("User-Agent", "Java/MCPlugin");

					int code = con.getResponseCode();
					if (code == 429) {
						PluginLogger.warn("Rate-limited by mcprofile.io. Retrying in " + backoff + "ms");
						Thread.sleep(backoff);
						backoff *= 2;
						continue;
					}

					if (code != 200) {
						PluginLogger.warn("Expected 200 response was:" + con.getResponseCode());
						continue;
					}

					try (InputStream in = con.getInputStream(); Reader r = new InputStreamReader(in)) {
						JsonObject obj = JsonParser.parseReader(r).getAsJsonObject();
						String name = isBedrock ? obj.has("gamertag") ? obj.get("gamertag").getAsString() :
																																															null
								: obj.has("username") ? obj.get("username").getAsString() : null;

						if (name != null) {
							return (isBedrock ? config.getString("bedrock_prefix") : "") + name;
						}
					}
				} catch (Exception e) {
					PluginLogger.debug(
							"Attempt %d to resolve UUID %s failed: %s".formatted(attempt, uuid, e.getMessage()));
				}

				try {
					Thread.sleep(1000L * attempt);
				} catch (InterruptedException ignored) {
				}
			}

			return null;
		}, NAME_RESOLVE_EXECUTOR);
	}

	private void indexOfflinePlayers() {
		for (var player : Bukkit.getOfflinePlayers()) {
			PluginLogger.info(
					"<green>Loaded Player: <gold>%s<gray>(%s) <blue>first_joined:%s <yellow>last_seen:%s".formatted(
							player.getName(), player.getUniqueId(), player.getFirstPlayed(),
							player.getLastSeen()));
			//save to db
		}
	}
}
