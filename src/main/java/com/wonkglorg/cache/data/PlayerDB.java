package com.wonkglorg.cache.data;

import com.wonkglorg.database.DatabaseType;
import com.wonkglorg.database.databases.SqliteDatabase;
import com.wonkglorg.database.datasources.FileDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

public class PlayerDB extends SqliteDatabase<FileDataSource>{
	
	public PlayerDB(JavaPlugin plugin) {
		super(new FileDataSource(DatabaseType.SQLITE, plugin.getDataPath().resolve("PlayerData.db")));
	}
}
