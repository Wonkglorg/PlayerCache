package com.wonkglorg.cache;

import org.bukkit.Location;
import org.bukkit.entity.Entity;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.function.Supplier;

/**
 * Handles region execution, folia compatible
 */
@SuppressWarnings("unused")
public class RegionScheduler{//NOSONAR
	private static RegionScheduler instance;
	private static JavaPlugin plugin;
	
	private final ExecutorService asyncExecutor;
	
	private RegionScheduler(String threadName) {
		this.asyncExecutor = Executors.newFixedThreadPool(Math.max(2, Runtime.getRuntime().availableProcessors() / 2), r -> {
			Thread t = new Thread(r, threadName);
			t.setDaemon(true);
			return t;
		});
	}
	
	/**
	 * Creates a global Async manager instance
	 *
	 * @param plugin the owning plugin
	 * @return the created instance, the same instance can be retrieved using {@link RegionScheduler#getInstance()}
	 */
	public static RegionScheduler createInstance(JavaPlugin plugin, String threadName) {
		RegionScheduler.plugin = plugin;
		instance = new RegionScheduler(threadName);
		return instance;
	}
	
	/**
	 * Creates a global Async manager instance
	 *
	 * @param plugin the owning plugin
	 * @return the created instance, the same instance can be retrieved using {@link RegionScheduler#getInstance()}
	 */
	public static RegionScheduler createInstance(JavaPlugin plugin) {
		return createInstance(plugin, "async-thread");
	}
	
	/**
	 * @return the instance created using {@link #createInstance(JavaPlugin, String)}
	 * @throws IllegalStateException when the instance has not been properly initialised
	 */
	public static RegionScheduler getInstance() {
		if(instance == null){
			throw new NullPointerException("AsyncManager instance not initialized!");
		}
		return instance;
	}
	
	/**
	 * Runs a task on the next tick on the global region scheduler
	 *
	 * @param task the task to run
	 */
	public void runNextTick(Runnable task) {
		plugin.getServer().getGlobalRegionScheduler().run(plugin, s -> task.run());
	}
	
	/**
	 * Runs a task on the next tick on a local region scheduler
	 *
	 * @param location the location of the region scheduler
	 * @param task the task to run
	 */
	public void runNextTick(Location location, Runnable task) {
		plugin.getServer().getRegionScheduler().run(plugin, location, s -> task.run());
	}
	
	/**
	 * Runs a task on the next tick available on the global region scheduler
	 *
	 * @param task the task to run
	 */
	public void run(Runnable task) {
		plugin.getServer().getGlobalRegionScheduler().execute(plugin, task);
	}
	
	/**
	 * Runs a task on the next tick available on the local region scheduler
	 *
	 * @param location the location of the region scheduler
	 * @param task the task to run
	 */
	public void run(Location location, Runnable task) {
		plugin.getServer().getRegionScheduler().execute(plugin, location, task);
	}
	
	/**
	 * Run task as entity
	 */
	public void run(Entity entity, Runnable task) {
		entity.getScheduler().execute(plugin, task, null, 1);
	}
	
	/**
	 * Runs async task (this should not interact with any minecraft api!)
	 */
	public <T> CompletableFuture<T> runAsync(Supplier<T> task) {
		return CompletableFuture.supplyAsync(task, asyncExecutor);
	}
	
	/**
	 * Shutdown on plugin disable
	 */
	public void shutdown() {
		asyncExecutor.shutdownNow();
	}
	
}