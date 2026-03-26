package com.wonkglorg.cache.data;

import com.wonkglorg.cache.PluginLogger;
import com.wonkglorg.cache.RegionScheduler;
import com.wonkglorg.database.DatabaseType;
import com.wonkglorg.database.databases.SqliteDatabase;
import com.wonkglorg.database.datasources.FileDataSource;
import org.bukkit.plugin.java.JavaPlugin;

import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;

public class PlayerDB extends SqliteDatabase<FileDataSource> {

	public PlayerDB(JavaPlugin plugin) {
		super(new FileDataSource(DatabaseType.SQLITE, plugin.getDataPath().resolve("PlayerData.db")));
		createTables();
	}

	private void createTables() {
		try (var statement = getConnection().prepareStatement("""
					CREATE TABLE IF NOT EXISTS "players" (
				 	"uuid"	TEXT NOT NULL,
				 	"name"	TEXT NOT NULL,
				 	"first_joined"	TEXT,
				 	"last_seen"	TEXT,
				 	"active"	integer NOT NULL,
				 	"is_bedrock"	INTEGER NOT NULL,
				 	PRIMARY KEY("uuid","name")
				 );
				""")) {
			statement.executeUpdate();
		} catch (SQLException e) {
			PluginLogger.error("Unable to create Player Table!", e);
		}
	}

	public CompletableFuture<PlayerProfile> getProfile(UUID uuid) {
		return RegionScheduler.getInstance().runAsync(() -> {
			try (var statement = getConnection().prepareStatement(
					"SELECT * FROM players WHERE uuid = ? AND active = 1")) {
				statement.setString(1, uuid.toString());
				ResultSet resultSet = statement.executeQuery();
				if (resultSet.next()) {
					String name = resultSet.getString("name");
					long firstJoin = resultSet.getLong("first_joined");
					long lastSeen = resultSet.getLong("last_seen");
					boolean isBedrock = resultSet.getBoolean("is_bedrock");
					return new PlayerProfile(uuid, name, firstJoin, lastSeen, isBedrock);
				}
				return null;
			} catch (SQLException e) {
				PluginLogger.error("Unable to retrieve Player Profile!");
				return null;
			}
		});
	}

	public CompletableFuture<PlayerProfile> getProfile(String name) {
		return RegionScheduler.getInstance().runAsync(() -> {
			try (var statement = getConnection().prepareStatement(
					"SELECT * FROM players WHERE name = ? AND active = 1")) {
				statement.setString(1, name);
				ResultSet resultSet = statement.executeQuery();
				if (resultSet.next()) {
					UUID uuid = UUID.fromString(resultSet.getString("uuid"));
					long firstJoin = resultSet.getLong("first_joined");
					long lastSeen = resultSet.getLong("last_seen");
					boolean isBedrock = resultSet.getBoolean("is_bedrock");
					return new PlayerProfile(uuid, name, firstJoin, lastSeen, isBedrock);
				}
				return null;
			} catch (SQLException e) {
				PluginLogger.error("Unable to retrieve Player Profile!");
				return null;
			}
		});
	}

	public void addProfile(PlayerProfile profile) {
		RegionScheduler.getInstance().runAsync(() -> {
			try (var connection = getConnection()) {
				connection.setAutoCommit(false);

				try (
						var deactivateStmt = connection.prepareStatement(
								"UPDATE players SET active = 0 WHERE uuid = ? AND name != ? AND active = 1"
						);
						var upsertStmt = connection.prepareStatement(
								"INSERT INTO players (uuid, name, first_joined, last_seen, is_bedrock, active) " +
								"VALUES (?, ?, ?, ?, ?, 1) " +
								"ON CONFLICT(uuid, name) DO UPDATE SET " +
								"first_joined = CASE WHEN players.first_joined = 0 THEN excluded.first_joined ELSE players.first_joined END, " +
								"last_seen = CASE WHEN players.last_seen = 0 THEN excluded.last_seen ELSE players.last_seen END, " +
								"is_bedrock = excluded.is_bedrock, " +
								"active = 1"
						)
				) {
					deactivateStmt.setString(1, profile.uuid().toString());
					deactivateStmt.setString(2, profile.name());
					deactivateStmt.executeUpdate();
					
					upsertStmt.setString(1, profile.uuid().toString());
					upsertStmt.setString(2, profile.name());
					upsertStmt.setLong(3, profile.firstJoined());
					upsertStmt.setLong(4, profile.lastSeen());
					upsertStmt.setBoolean(5, profile.isBedrock());
					upsertStmt.executeUpdate();

					connection.commit();
				} catch (SQLException e) {
					connection.rollback();
					throw e;
				} finally {
					connection.setAutoCommit(true);
				}

			} catch (SQLException e) {
				PluginLogger.error("Unable to add Player Profile!");
				e.printStackTrace();
			}
			return null;
		});
	}


	public void addBatchProfile(List<PlayerProfile> profiles) {
		RegionScheduler.getInstance().runAsync(() -> {
			if (profiles == null || profiles.isEmpty()) {
				return null;
			}

			try (var connection = getConnection()) {
				connection.setAutoCommit(false);

				try (var deactivateStmt = connection.prepareStatement(
						"UPDATE players SET active = 0 WHERE uuid = ? AND name != ? AND active = 1");
						var upsertStmt = connection.prepareStatement(
								"INSERT INTO players (uuid, name, first_joined, last_seen, is_bedrock, active) "
								+ "VALUES (?, ?, ?, ?, ?, 1) " + "ON CONFLICT(uuid, name) DO UPDATE SET "
								+ "first_joined = CASE WHEN players.first_joined = 0 THEN excluded.first_joined " 
								+ "ELSE players.first_joined END, "
								+ "last_seen = CASE WHEN players.last_seen = 0 THEN excluded.last_seen ELSE " 
								+ "players.last_seen END, "
								+ "is_bedrock = excluded.is_bedrock, " + "active = 1")) {
					for (PlayerProfile profile : profiles) {

						deactivateStmt.setString(1, profile.uuid().toString());
						deactivateStmt.setString(2, profile.name());
						deactivateStmt.addBatch();

						upsertStmt.setString(1, profile.uuid().toString());
						upsertStmt.setString(2, profile.name());
						upsertStmt.setLong(3, profile.firstJoined());
						upsertStmt.setLong(4, profile.lastSeen());
						upsertStmt.setBoolean(5, profile.isBedrock());
						upsertStmt.addBatch();
					}

					deactivateStmt.executeBatch();
					upsertStmt.executeBatch();

					connection.commit();
				} catch (SQLException e) {
					connection.rollback();
					throw e;
				} finally {
					connection.setAutoCommit(true);
				}

			} catch (SQLException e) {
				PluginLogger.error("Unable to batch upsert player profiles!");
				e.printStackTrace();
			}

			return null;
		});
	}


	public void updateLastSeen(PlayerProfile profile) {
		RegionScheduler.getInstance().runAsync(() -> {
			try {
				var connection = getConnection();
				try (var checkStmt = connection.prepareStatement(
						"SELECT 1 FROM players WHERE uuid = ? AND name = ? AND active = 1")) {

					checkStmt.setString(1, profile.uuid().toString());
					checkStmt.setString(2, profile.name());

					ResultSet rs = checkStmt.executeQuery();
					if (rs.next()) {
						try (var updateStmt = connection.prepareStatement(
								"UPDATE players SET last_seen = ? WHERE uuid = ? AND name = ? AND active = 1")) {

							updateStmt.setLong(1, profile.lastSeen());
							updateStmt.setString(2, profile.uuid().toString());
							updateStmt.setString(3, profile.name());
							updateStmt.executeUpdate();
						}
						return null;
					}
				}

				try (var deactivateStmt = connection.prepareStatement(
						"UPDATE players SET active = 0 WHERE uuid = ? AND name != ? AND active = 1")) {

					deactivateStmt.setString(1, profile.uuid().toString());
					deactivateStmt.setString(2, profile.name());
					deactivateStmt.executeUpdate();
				}

				try (var insertStmt = connection.prepareStatement(
						"INSERT INTO players (uuid, name, first_joined, last_seen, is_bedrock, active) "
						+ "VALUES (?, ?, ?, ?, ?, 1)")) {

					insertStmt.setString(1, profile.uuid().toString());
					insertStmt.setString(2, profile.name());
					insertStmt.setLong(3, profile.firstJoined());
					insertStmt.setLong(4, profile.lastSeen());
					insertStmt.setBoolean(5, profile.isBedrock());

					insertStmt.executeUpdate();
				}

			} catch (SQLException e) {
				PluginLogger.error("Unable to update Player Profile!");
				e.printStackTrace();
			}
			return null;
		});
	}
}
