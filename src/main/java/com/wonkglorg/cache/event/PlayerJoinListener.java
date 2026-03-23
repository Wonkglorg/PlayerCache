package com.wonkglorg.cache.event;

import com.wonkglorg.cache.PlayerCache;
import org.bukkit.event.EventHandler;
import org.bukkit.event.Listener;
import org.bukkit.event.player.PlayerJoinEvent;
import org.bukkit.event.player.PlayerQuitEvent;

public class PlayerJoinListener implements Listener{
	@EventHandler
	public void onJoin(PlayerJoinEvent event) {
		PlayerCache.getInstance().dataCache().cacheUser(event.getPlayer());
	}
	
	@EventHandler
	public void onLeave(PlayerQuitEvent event) {
		PlayerCache.getInstance().dataCache().updateUserLastSeen(event.getPlayer());
	}
}
