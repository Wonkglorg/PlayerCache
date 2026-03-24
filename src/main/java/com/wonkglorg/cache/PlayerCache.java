package com.wonkglorg.cache;

import com.wonkglorg.cache.command.PlayerCacheCommand;
import com.wonkglorg.cache.data.DataCache;
import com.wonkglorg.cache.event.PlayerJoinListener;
import io.papermc.paper.plugin.lifecycle.event.types.LifecycleEvents;
import org.bukkit.Bukkit;
import org.bukkit.plugin.java.JavaPlugin;

public final class PlayerCache extends JavaPlugin{
	public static boolean floodGateEnabled = false;
	private static PlayerCache instance;
	private DataCache dataCache;
	
	@Override
	public void onLoad() {
		instance = this;
		PluginLogger.init(this);
		RegionScheduler.createInstance(this);
	}
	
	@Override
	public void onEnable() {
		floodGateEnabled = Bukkit.getPluginManager().getPlugin("floodgate") != null;
		dataCache = new DataCache(this);
		getServer().getPluginManager().registerEvents(new PlayerJoinListener(), this);
		this.getLifecycleManager().registerEventHandler(LifecycleEvents.COMMANDS, registrar -> new PlayerCacheCommand().register(registrar));
	}
	
	@Override
	public void onDisable() {
	}
	
	public DataCache dataCache() {
		return dataCache;
	}
	
	public static PlayerCache getInstance() {
		return instance;
	}
}
