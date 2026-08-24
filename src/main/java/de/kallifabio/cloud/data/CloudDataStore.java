package de.kallifabio.cloud.data;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;
import com.mongodb.client.MongoClient;
import com.mongodb.client.MongoClients;
import com.mongodb.client.MongoCollection;
import com.mongodb.client.MongoDatabase;
import com.mongodb.client.model.Filters;
import com.mongodb.client.model.ReplaceOptions;
import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import de.kallifabio.cloud.config.ConfigManager;
import de.kallifabio.cloud.libs.logging.CentralLogger;
import de.kallifabio.cloud.master.permissions.PermissionGroup;
import org.bson.Document;

import java.lang.reflect.Type;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class CloudDataStore {

    public enum BackendType { SQLITE, MYSQL, MONGO }

    private final Gson gson = new Gson();
    private HikariDataSource dataSource;
    private MongoClient mongoClient;
    private MongoDatabase mongoDatabase;
    private BackendType backendType = BackendType.SQLITE;
    private volatile long lastDbErrorLogAt = 0L;
    private volatile String lastDbErrorSignature = "";

    public synchronized void initialize(ConfigManager config) {
        String type = config.getDatabaseType();
        if ("mysql".equalsIgnoreCase(type)) {
            backendType = BackendType.MYSQL;
            initJdbc(config.getDatabaseUrl(), config.getDatabaseUsername(), config.getDatabasePassword());
        } else if ("mongo".equalsIgnoreCase(type)) {
            backendType = BackendType.MONGO;
            initMongo(config.getMongoUri(), config.getMongoDatabase());
        } else {
            backendType = BackendType.SQLITE;
            initJdbc(config.getSqliteJdbcUrl(), "", "");
        }

        createTablesIfNeeded();
        CentralLogger.info("DataStore", "Initialized backend " + backendType);
    }

    public synchronized void shutdown() {
        if (dataSource != null) {
            dataSource.close();
            dataSource = null;
        }
        if (mongoClient != null) {
            mongoClient.close();
            mongoClient = null;
        }
    }

    private void initJdbc(String jdbcUrl, String user, String pass) {
        String effectiveJdbcUrl = normalizeAndPrepareJdbcUrl(jdbcUrl);

        HikariConfig hc = new HikariConfig();
        hc.setJdbcUrl(effectiveJdbcUrl);
        if (user != null && !user.isBlank()) hc.setUsername(user);
        if (pass != null && !pass.isBlank()) hc.setPassword(pass);
        hc.setMaximumPoolSize(10);
        hc.setMinimumIdle(1);
        hc.setConnectionTimeout(5000);
        hc.setValidationTimeout(3000);
        hc.setInitializationFailTimeout(-1);
        hc.setPoolName("CloudDataPool");
        dataSource = new HikariDataSource(hc);
    }

    private String normalizeAndPrepareJdbcUrl(String jdbcUrl) {
        if (jdbcUrl == null) {
            return null;
        }
        String lower = jdbcUrl.toLowerCase();
        if (!lower.startsWith("jdbc:sqlite:")) {
            return jdbcUrl;
        }

        try {
            String filePart = jdbcUrl.substring("jdbc:sqlite:".length());
            int paramsIdx = filePart.indexOf('?');
            if (paramsIdx >= 0) {
                filePart = filePart.substring(0, paramsIdx);
            }
            filePart = filePart.trim();
            if (filePart.isEmpty() || ":memory:".equalsIgnoreCase(filePart)) {
                return jdbcUrl;
            }

            Path dbPath = Paths.get(filePart);
            if (!dbPath.isAbsolute()) {
                dbPath = Path.of(System.getProperty("user.dir")).resolve(dbPath).normalize();
            }
            Path parent = dbPath.getParent();
            if (parent != null) {
                Files.createDirectories(parent);
            }
            return "jdbc:sqlite:" + dbPath.toString().replace("\\", "/");
        } catch (Exception e) {
            CentralLogger.error("DataStore", "Failed to prepare SQLite path for JDBC URL: " + jdbcUrl, e);
            return jdbcUrl;
        }
    }

    private void initMongo(String uri, String db) {
        mongoClient = MongoClients.create(uri);
        mongoDatabase = mongoClient.getDatabase(db);
    }

    private void createTablesIfNeeded() {
        if (backendType == BackendType.MONGO) {
            return;
        }

        try (Connection c = dataSource.getConnection(); Statement s = c.createStatement()) {
            s.executeUpdate("CREATE TABLE IF NOT EXISTS player_data (" +
                    "player_uuid TEXT PRIMARY KEY," +
                    "coins INTEGER DEFAULT 0," +
                    "stats_json TEXT DEFAULT '{}'," +
                    "permissions_json TEXT DEFAULT '[]'," +
                    "last_server TEXT DEFAULT ''," +
                    "last_seen BIGINT DEFAULT 0)");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS queue_entries (" +
                    "group_name TEXT NOT NULL," +
                    "player_uuid TEXT NOT NULL," +
                    "player_name TEXT NOT NULL," +
                    "priority INTEGER NOT NULL," +
                    "queued_at BIGINT NOT NULL," +
                    "PRIMARY KEY (group_name, player_uuid))");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS friends (" +
                    "player_uuid TEXT NOT NULL," +
                    "friend_uuid TEXT NOT NULL," +
                    "PRIMARY KEY (player_uuid, friend_uuid))");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS party_members (" +
                    "party_id TEXT NOT NULL," +
                    "player_uuid TEXT NOT NULL," +
                    "leader INTEGER DEFAULT 0," +
                    "PRIMARY KEY (party_id, player_uuid))");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS permission_groups (" +
                    "group_name TEXT PRIMARY KEY," +
                    "parent_group TEXT DEFAULT ''," +
                    "weight INTEGER DEFAULT 0," +
                    "prefix TEXT DEFAULT ''," +
                    "suffix TEXT DEFAULT ''," +
                    "permissions_json TEXT DEFAULT '[]')");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS player_permission_groups (" +
                    "player_uuid TEXT NOT NULL," +
                    "group_name TEXT NOT NULL," +
                    "PRIMARY KEY (player_uuid, group_name))");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS temp_permissions (" +
                    "player_uuid TEXT NOT NULL," +
                    "permission TEXT NOT NULL," +
                    "expires_at BIGINT NOT NULL," +
                    "PRIMARY KEY (player_uuid, permission))");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS friend_requests (" +
                    "target_uuid TEXT NOT NULL," +
                    "requester_uuid TEXT NOT NULL," +
                    "expires_at BIGINT NOT NULL," +
                    "created_at BIGINT NOT NULL," +
                    "PRIMARY KEY (target_uuid))");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS party_invites (" +
                    "target_uuid TEXT NOT NULL," +
                    "party_id TEXT NOT NULL," +
                    "inviter_uuid TEXT NOT NULL," +
                    "expires_at BIGINT NOT NULL," +
                    "created_at BIGINT NOT NULL," +
                    "PRIMARY KEY (target_uuid))");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS punishments (" +
                    "id TEXT PRIMARY KEY," +
                    "target_uuid TEXT NOT NULL," +
                    "target_name TEXT DEFAULT ''," +
                    "actor_uuid TEXT DEFAULT ''," +
                    "actor_name TEXT DEFAULT ''," +
                    "type TEXT NOT NULL," +
                    "reason TEXT DEFAULT ''," +
                    "proof TEXT DEFAULT ''," +
                    "notes TEXT DEFAULT ''," +
                    "address TEXT DEFAULT ''," +
                    "created_at BIGINT NOT NULL," +
                    "expires_at BIGINT DEFAULT 0," +
                    "active INTEGER DEFAULT 1)");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS clans (" +
                    "name TEXT PRIMARY KEY," +
                    "tag TEXT DEFAULT ''," +
                    "owner_uuid TEXT NOT NULL," +
                    "home_server TEXT DEFAULT ''," +
                    "friendly_fire INTEGER DEFAULT 0," +
                    "created_at BIGINT NOT NULL," +
                    "wins INTEGER DEFAULT 0," +
                    "kills INTEGER DEFAULT 0," +
                    "points INTEGER DEFAULT 0," +
                    "admins_json TEXT DEFAULT '[]'," +
                    "moderators_json TEXT DEFAULT '[]'," +
                    "members_json TEXT DEFAULT '[]')");

            s.executeUpdate("CREATE TABLE IF NOT EXISTS clan_invites (" +
                    "clan_name TEXT NOT NULL," +
                    "target_uuid TEXT NOT NULL," +
                    "inviter_uuid TEXT NOT NULL," +
                    "expires_at BIGINT NOT NULL," +
                    "created_at BIGINT NOT NULL," +
                    "PRIMARY KEY (clan_name, target_uuid))");
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to create tables", e);
        }
    }

    public void savePlayerData(PlayerData data) {
        if (backendType == BackendType.MONGO) {
            MongoCollection<Document> col = mongoDatabase.getCollection("player_data");
            Document doc = new Document("player_uuid", data.playerUuid)
                    .append("coins", data.coins)
                    .append("stats_json", gson.toJson(data.stats))
                    .append("permissions_json", gson.toJson(data.permissions))
                    .append("last_server", data.lastServer)
                    .append("last_seen", data.lastSeen);
            col.replaceOne(Filters.eq("player_uuid", data.playerUuid), doc, new ReplaceOptions().upsert(true));
            return;
        }

        String sql;
        if (backendType == BackendType.MYSQL) {
            sql = "INSERT INTO player_data(player_uuid,coins,stats_json,permissions_json,last_server,last_seen) " +
                    "VALUES (?,?,?,?,?,?) ON DUPLICATE KEY UPDATE " +
                    "coins=VALUES(coins), stats_json=VALUES(stats_json), permissions_json=VALUES(permissions_json), " +
                    "last_server=VALUES(last_server), last_seen=VALUES(last_seen)";
        } else {
            sql = "INSERT INTO player_data(player_uuid,coins,stats_json,permissions_json,last_server,last_seen) " +
                    "VALUES (?,?,?,?,?,?) ON CONFLICT(player_uuid) DO UPDATE SET " +
                    "coins=excluded.coins, stats_json=excluded.stats_json, permissions_json=excluded.permissions_json, " +
                    "last_server=excluded.last_server, last_seen=excluded.last_seen";
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, data.playerUuid);
            ps.setInt(2, data.coins);
            ps.setString(3, gson.toJson(data.stats));
            ps.setString(4, gson.toJson(data.permissions));
            ps.setString(5, data.lastServer == null ? "" : data.lastServer);
            ps.setLong(6, data.lastSeen);
            ps.executeUpdate();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to save player data", e);
        }
    }

    public PlayerData getPlayerData(String uuid) {
        if (backendType == BackendType.MONGO) {
            Document doc = mongoDatabase.getCollection("player_data").find(Filters.eq("player_uuid", uuid)).first();
            if (doc == null) {
                return new PlayerData(uuid);
            }
            return fromDocument(doc);
        }

        String sql = "SELECT * FROM player_data WHERE player_uuid=?";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, uuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return new PlayerData(uuid);
                }
                return fromResultSet(rs);
            }
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to load player data", e);
            return new PlayerData(uuid);
        }
    }

    public void saveQueueEntry(QueueEntry entry) {
        if (backendType == BackendType.MONGO) {
            MongoCollection<Document> col = mongoDatabase.getCollection("queue_entries");
            Document doc = new Document("group_name", entry.groupName)
                    .append("player_uuid", entry.playerUuid)
                    .append("player_name", entry.playerName)
                    .append("priority", entry.priority)
                    .append("queued_at", entry.queuedAt);
            col.replaceOne(Filters.and(Filters.eq("group_name", entry.groupName), Filters.eq("player_uuid", entry.playerUuid)),
                    doc, new ReplaceOptions().upsert(true));
            return;
        }

        String sql;
        if (backendType == BackendType.MYSQL) {
            sql = "INSERT INTO queue_entries(group_name,player_uuid,player_name,priority,queued_at) VALUES (?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE player_name=VALUES(player_name), priority=VALUES(priority), queued_at=VALUES(queued_at)";
        } else {
            sql = "INSERT INTO queue_entries(group_name,player_uuid,player_name,priority,queued_at) VALUES (?,?,?,?,?) " +
                    "ON CONFLICT(group_name,player_uuid) DO UPDATE SET player_name=excluded.player_name, priority=excluded.priority, queued_at=excluded.queued_at";
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, entry.groupName);
            ps.setString(2, entry.playerUuid);
            ps.setString(3, entry.playerName);
            ps.setInt(4, entry.priority);
            ps.setLong(5, entry.queuedAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to save queue entry", e);
        }
    }

    public void removeQueueEntry(String groupName, String playerUuid) {
        if (backendType == BackendType.MONGO) {
            mongoDatabase.getCollection("queue_entries")
                    .deleteOne(Filters.and(Filters.eq("group_name", groupName), Filters.eq("player_uuid", playerUuid)));
            return;
        }

        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "DELETE FROM queue_entries WHERE group_name=? AND player_uuid=?")) {
            ps.setString(1, groupName);
            ps.setString(2, playerUuid);
            ps.executeUpdate();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to remove queue entry", e);
        }
    }

    public List<QueueEntry> loadQueueEntries(String groupName) {
        List<QueueEntry> entries = new ArrayList<>();
        if (backendType == BackendType.MONGO) {
            for (Document doc : mongoDatabase.getCollection("queue_entries").find(Filters.eq("group_name", groupName))) {
                QueueEntry entry = new QueueEntry(
                        doc.getString("player_uuid"),
                        doc.getString("player_name"),
                        doc.getString("group_name"),
                        doc.getInteger("priority", 0),
                        doc.getLong("queued_at")
                );
                entries.add(entry);
            }
            return entries;
        }

        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM queue_entries WHERE group_name=? ORDER BY priority DESC, queued_at ASC")) {
            ps.setString(1, groupName);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    entries.add(new QueueEntry(
                            rs.getString("player_uuid"),
                            rs.getString("player_name"),
                            rs.getString("group_name"),
                            rs.getInt("priority"),
                            rs.getLong("queued_at")
                    ));
                }
            }
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to load queue entries", e);
        }

        return entries;
    }

    public void setFriends(String playerUuid, List<String> friends) {
        if (backendType == BackendType.MONGO) {
            mongoDatabase.getCollection("player_social").replaceOne(
                    Filters.eq("player_uuid", playerUuid),
                    new Document("player_uuid", playerUuid).append("friends", friends),
                    new ReplaceOptions().upsert(true));
            return;
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement del = c.prepareStatement("DELETE FROM friends WHERE player_uuid=?")) {
                del.setString(1, playerUuid);
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement("INSERT INTO friends(player_uuid,friend_uuid) VALUES (?,?)")) {
                for (String friend : friends) {
                    ins.setString(1, playerUuid);
                    ins.setString(2, friend);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to save friends", e);
        }
    }

    public List<String> getFriends(String playerUuid) {
        List<String> result = new ArrayList<>();
        if (backendType == BackendType.MONGO) {
            Document doc = mongoDatabase.getCollection("player_social").find(Filters.eq("player_uuid", playerUuid)).first();
            if (doc == null || doc.get("friends") == null) {
                return result;
            }
            return (List<String>) doc.get("friends");
        }

        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT friend_uuid FROM friends WHERE player_uuid=?")) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("friend_uuid"));
                }
            }
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to load friends", e);
        }
        return result;
    }

    public void setPartyMembers(String partyId, String leaderUuid, List<String> members) {
        if (backendType == BackendType.MONGO) {
            mongoDatabase.getCollection("parties").replaceOne(
                    Filters.eq("party_id", partyId),
                    new Document("party_id", partyId)
                            .append("leader_uuid", leaderUuid)
                            .append("members", members),
                    new ReplaceOptions().upsert(true)
            );
            return;
        }

        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement del = c.prepareStatement("DELETE FROM party_members WHERE party_id=?")) {
                del.setString(1, partyId);
                del.executeUpdate();
            }
            try (PreparedStatement ins = c.prepareStatement("INSERT INTO party_members(party_id,player_uuid,leader) VALUES (?,?,?)")) {
                for (String member : members) {
                    ins.setString(1, partyId);
                    ins.setString(2, member);
                    ins.setInt(3, member.equals(leaderUuid) ? 1 : 0);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
            c.commit();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to save party", e);
        }
    }

    public List<String> getPartyMembers(String partyId) {
        List<String> members = new ArrayList<>();
        if (backendType == BackendType.MONGO) {
            Document doc = mongoDatabase.getCollection("parties").find(Filters.eq("party_id", partyId)).first();
            if (doc == null || doc.get("members") == null) {
                return members;
            }
            return (List<String>) doc.get("members");
        }

        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT player_uuid FROM party_members WHERE party_id=?")) {
            ps.setString(1, partyId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    members.add(rs.getString("player_uuid"));
                }
            }
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to load party members", e);
        }
        return members;
    }

    public String getPartyLeader(String partyId) {
        if (partyId == null || partyId.isBlank()) {
            return null;
        }

        if (backendType == BackendType.MONGO) {
            Document doc = mongoDatabase.getCollection("parties").find(Filters.eq("party_id", partyId)).first();
            if (doc == null) {
                return null;
            }
            return doc.getString("leader_uuid");
        }

        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT player_uuid FROM party_members WHERE party_id=? AND leader=1 LIMIT 1")) {
            ps.setString(1, partyId);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("player_uuid");
                }
            }
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to load party leader", e);
        }
        return null;
    }

    public String getPartyIdForPlayer(String playerUuid) {
        if (playerUuid == null || playerUuid.isBlank()) {
            return null;
        }

        if (backendType == BackendType.MONGO) {
            Document doc = mongoDatabase.getCollection("parties")
                    .find(Filters.in("members", playerUuid))
                    .first();
            return doc == null ? null : doc.getString("party_id");
        }

        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT party_id FROM party_members WHERE player_uuid=? LIMIT 1")) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                if (rs.next()) {
                    return rs.getString("party_id");
                }
            }
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to lookup player party", e);
        }
        return null;
    }

    public void upsertPermissionGroup(PermissionGroup group) {
        if (group == null || group.name == null || group.name.isBlank()) {
            return;
        }

        if (backendType == BackendType.MONGO) {
            mongoDatabase.getCollection("permission_groups").replaceOne(
                    Filters.eq("group_name", group.name),
                    new Document("group_name", group.name)
                            .append("parent_group", group.parent == null ? "" : group.parent)
                            .append("weight", group.weight)
                            .append("prefix", group.prefix == null ? "" : group.prefix)
                            .append("suffix", group.suffix == null ? "" : group.suffix)
                            .append("permissions_json", gson.toJson(group.permissions)),
                    new ReplaceOptions().upsert(true)
            );
            return;
        }

        String sql;
        if (backendType == BackendType.MYSQL) {
            sql = "INSERT INTO permission_groups(group_name,parent_group,weight,prefix,suffix,permissions_json) VALUES (?,?,?,?,?,?) " +
                    "ON DUPLICATE KEY UPDATE parent_group=VALUES(parent_group), weight=VALUES(weight), " +
                    "prefix=VALUES(prefix), suffix=VALUES(suffix), permissions_json=VALUES(permissions_json)";
        } else {
            sql = "INSERT INTO permission_groups(group_name,parent_group,weight,prefix,suffix,permissions_json) VALUES (?,?,?,?,?,?) " +
                    "ON CONFLICT(group_name) DO UPDATE SET parent_group=excluded.parent_group, weight=excluded.weight, " +
                    "prefix=excluded.prefix, suffix=excluded.suffix, permissions_json=excluded.permissions_json";
        }

        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, group.name);
            ps.setString(2, group.parent == null ? "" : group.parent);
            ps.setInt(3, group.weight);
            ps.setString(4, group.prefix == null ? "" : group.prefix);
            ps.setString(5, group.suffix == null ? "" : group.suffix);
            ps.setString(6, gson.toJson(group.permissions));
            ps.executeUpdate();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to upsert permission group", e);
        }
    }

    public PermissionGroup getPermissionGroup(String groupName) {
        if (groupName == null || groupName.isBlank()) {
            return null;
        }

        if (backendType == BackendType.MONGO) {
            Document doc = mongoDatabase.getCollection("permission_groups").find(Filters.eq("group_name", groupName)).first();
            return fromPermissionDoc(doc);
        }

        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT * FROM permission_groups WHERE group_name=?")) {
            ps.setString(1, groupName);
            try (ResultSet rs = ps.executeQuery()) {
                if (!rs.next()) {
                    return null;
                }
                return fromPermissionResultSet(rs);
            }
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to load permission group", e);
            return null;
        }
    }

    public void assignPermissionGroup(String playerUuid, String groupName) {
        if (backendType == BackendType.MONGO) {
            mongoDatabase.getCollection("player_permission_groups").replaceOne(
                    Filters.and(Filters.eq("player_uuid", playerUuid), Filters.eq("group_name", groupName)),
                    new Document("player_uuid", playerUuid).append("group_name", groupName),
                    new ReplaceOptions().upsert(true)
            );
            return;
        }

        String sql = backendType == BackendType.MYSQL
                ? "INSERT INTO player_permission_groups(player_uuid,group_name) VALUES (?,?) ON DUPLICATE KEY UPDATE group_name=VALUES(group_name)"
                : "INSERT INTO player_permission_groups(player_uuid,group_name) VALUES (?,?) ON CONFLICT(player_uuid,group_name) DO NOTHING";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setString(2, groupName);
            ps.executeUpdate();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to assign permission group", e);
        }
    }

    public List<PermissionGroup> getPermissionGroupsForPlayer(String playerUuid) {
        List<PermissionGroup> result = new ArrayList<>();
        if (backendType == BackendType.MONGO) {
            for (Document doc : mongoDatabase.getCollection("player_permission_groups").find(Filters.eq("player_uuid", playerUuid))) {
                PermissionGroup g = getPermissionGroup(doc.getString("group_name"));
                if (g != null) {
                    result.add(g);
                }
            }
            return result;
        }

        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT group_name FROM player_permission_groups WHERE player_uuid=?")) {
            ps.setString(1, playerUuid);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PermissionGroup g = getPermissionGroup(rs.getString("group_name"));
                    if (g != null) {
                        result.add(g);
                    }
                }
            }
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to load player permission groups", e);
        }
        return result;
    }

    public void setTempPermission(String playerUuid, String permission, long expiresAt) {
        if (backendType == BackendType.MONGO) {
            mongoDatabase.getCollection("temp_permissions").replaceOne(
                    Filters.and(Filters.eq("player_uuid", playerUuid), Filters.eq("permission", permission)),
                    new Document("player_uuid", playerUuid).append("permission", permission).append("expires_at", expiresAt),
                    new ReplaceOptions().upsert(true)
            );
            return;
        }

        String sql = backendType == BackendType.MYSQL
                ? "INSERT INTO temp_permissions(player_uuid,permission,expires_at) VALUES (?,?,?) ON DUPLICATE KEY UPDATE expires_at=VALUES(expires_at)"
                : "INSERT INTO temp_permissions(player_uuid,permission,expires_at) VALUES (?,?,?) ON CONFLICT(player_uuid,permission) DO UPDATE SET expires_at=excluded.expires_at";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, playerUuid);
            ps.setString(2, permission);
            ps.setLong(3, expiresAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to set temp permission", e);
        }
    }

    public List<String> getActiveTempPermissions(String playerUuid) {
        long now = System.currentTimeMillis();
        List<String> result = new ArrayList<>();
        if (backendType == BackendType.MONGO) {
            for (Document doc : mongoDatabase.getCollection("temp_permissions").find(
                    Filters.and(Filters.eq("player_uuid", playerUuid), Filters.gt("expires_at", now)))) {
                result.add(doc.getString("permission"));
            }
            return result;
        }

        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(
                "SELECT permission FROM temp_permissions WHERE player_uuid=? AND expires_at>?")) {
            ps.setString(1, playerUuid);
            ps.setLong(2, now);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    result.add(rs.getString("permission"));
                }
            }
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to load temp permissions", e);
        }
        return result;
    }

    public void createFriendRequest(String requesterUuid, String targetUuid, long expiresAt) {
        long createdAt = System.currentTimeMillis();
        if (backendType == BackendType.MONGO) {
            mongoDatabase.getCollection("friend_requests").replaceOne(
                    Filters.eq("target_uuid", targetUuid),
                    new Document("target_uuid", targetUuid)
                            .append("requester_uuid", requesterUuid)
                            .append("expires_at", expiresAt)
                            .append("created_at", createdAt),
                    new ReplaceOptions().upsert(true)
            );
            return;
        }

        String sql = backendType == BackendType.MYSQL
                ? "INSERT INTO friend_requests(target_uuid,requester_uuid,expires_at,created_at) VALUES (?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE requester_uuid=VALUES(requester_uuid),expires_at=VALUES(expires_at),created_at=VALUES(created_at)"
                : "INSERT INTO friend_requests(target_uuid,requester_uuid,expires_at,created_at) VALUES (?,?,?,?) " +
                "ON CONFLICT(target_uuid) DO UPDATE SET requester_uuid=excluded.requester_uuid,expires_at=excluded.expires_at,created_at=excluded.created_at";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, targetUuid);
            ps.setString(2, requesterUuid);
            ps.setLong(3, expiresAt);
            ps.setLong(4, createdAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to create friend request", e);
        }
    }

    public String consumeFriendRequest(String targetUuid) {
        long now = System.currentTimeMillis();
        if (backendType == BackendType.MONGO) {
            Document doc = mongoDatabase.getCollection("friend_requests").find(Filters.eq("target_uuid", targetUuid)).first();
            if (doc == null) {
                return null;
            }
            Long expires = doc.getLong("expires_at");
            mongoDatabase.getCollection("friend_requests").deleteOne(Filters.eq("target_uuid", targetUuid));
            if (expires == null || expires < now) {
                return null;
            }
            return doc.getString("requester_uuid");
        }

        String requester = null;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement select = c.prepareStatement("SELECT requester_uuid, expires_at FROM friend_requests WHERE target_uuid=?")) {
                select.setString(1, targetUuid);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        long expiresAt = rs.getLong("expires_at");
                        if (expiresAt >= now) {
                            requester = rs.getString("requester_uuid");
                        }
                    }
                }
            }
            try (PreparedStatement delete = c.prepareStatement("DELETE FROM friend_requests WHERE target_uuid=?")) {
                delete.setString(1, targetUuid);
                delete.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to consume friend request", e);
        }
        return requester;
    }

    public void createPartyInvite(String partyId, String inviterUuid, String targetUuid, long expiresAt) {
        long createdAt = System.currentTimeMillis();
        if (backendType == BackendType.MONGO) {
            mongoDatabase.getCollection("party_invites").replaceOne(
                    Filters.eq("target_uuid", targetUuid),
                    new Document("target_uuid", targetUuid)
                            .append("party_id", partyId)
                            .append("inviter_uuid", inviterUuid)
                            .append("expires_at", expiresAt)
                            .append("created_at", createdAt),
                    new ReplaceOptions().upsert(true)
            );
            return;
        }

        String sql = backendType == BackendType.MYSQL
                ? "INSERT INTO party_invites(target_uuid,party_id,inviter_uuid,expires_at,created_at) VALUES (?,?,?,?,?) " +
                "ON DUPLICATE KEY UPDATE party_id=VALUES(party_id),inviter_uuid=VALUES(inviter_uuid),expires_at=VALUES(expires_at),created_at=VALUES(created_at)"
                : "INSERT INTO party_invites(target_uuid,party_id,inviter_uuid,expires_at,created_at) VALUES (?,?,?,?,?) " +
                "ON CONFLICT(target_uuid) DO UPDATE SET party_id=excluded.party_id,inviter_uuid=excluded.inviter_uuid,expires_at=excluded.expires_at,created_at=excluded.created_at";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, targetUuid);
            ps.setString(2, partyId);
            ps.setString(3, inviterUuid);
            ps.setLong(4, expiresAt);
            ps.setLong(5, createdAt);
            ps.executeUpdate();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to create party invite", e);
        }
    }

    public String consumePartyInvite(String targetUuid) {
        long now = System.currentTimeMillis();
        if (backendType == BackendType.MONGO) {
            Document doc = mongoDatabase.getCollection("party_invites").find(Filters.eq("target_uuid", targetUuid)).first();
            if (doc == null) {
                return null;
            }
            Long expires = doc.getLong("expires_at");
            mongoDatabase.getCollection("party_invites").deleteOne(Filters.eq("target_uuid", targetUuid));
            if (expires == null || expires < now) {
                return null;
            }
            return doc.getString("party_id");
        }

        String partyId = null;
        try (Connection c = dataSource.getConnection()) {
            c.setAutoCommit(false);
            try (PreparedStatement select = c.prepareStatement("SELECT party_id, expires_at FROM party_invites WHERE target_uuid=?")) {
                select.setString(1, targetUuid);
                try (ResultSet rs = select.executeQuery()) {
                    if (rs.next()) {
                        long expiresAt = rs.getLong("expires_at");
                        if (expiresAt >= now) {
                            partyId = rs.getString("party_id");
                        }
                    }
                }
            }
            try (PreparedStatement delete = c.prepareStatement("DELETE FROM party_invites WHERE target_uuid=?")) {
                delete.setString(1, targetUuid);
                delete.executeUpdate();
            }
            c.commit();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to consume party invite", e);
        }
        return partyId;
    }

    public int cleanupExpiredSocialPending(long now) {
        int removed = 0;
        if (backendType == BackendType.MONGO) {
            long a = mongoDatabase.getCollection("friend_requests").deleteMany(Filters.lt("expires_at", now)).getDeletedCount();
            long b = mongoDatabase.getCollection("party_invites").deleteMany(Filters.lt("expires_at", now)).getDeletedCount();
            return (int) (a + b);
        }
        if (dataSource == null) {
            logDbErrorThrottled("cleanup:no-datasource", null,
                    "Skipped cleanup social pending records: datasource not initialized");
            return 0;
        }

        try (Connection c = dataSource.getConnection()) {
            try (PreparedStatement a = c.prepareStatement("DELETE FROM friend_requests WHERE expires_at < ?")) {
                a.setLong(1, now);
                removed += a.executeUpdate();
            }
            try (PreparedStatement b = c.prepareStatement("DELETE FROM party_invites WHERE expires_at < ?")) {
                b.setLong(1, now);
                removed += b.executeUpdate();
            }
        } catch (SQLException e) {
            logDbErrorThrottled("cleanup:sql", e, "Failed to cleanup social pending records");
        }
        return removed;
    }

    public void upsertPunishment(PunishmentData punishment) {
        if (punishment == null || punishment.id == null || punishment.id.isBlank()) {
            return;
        }
        if (backendType == BackendType.MONGO) {
            Document doc = new Document("id", punishment.id)
                    .append("target_uuid", punishment.targetUuid)
                    .append("target_name", punishment.targetName)
                    .append("actor_uuid", punishment.actorUuid)
                    .append("actor_name", punishment.actorName)
                    .append("type", punishment.type)
                    .append("reason", punishment.reason)
                    .append("proof", punishment.proof)
                    .append("notes", punishment.notes)
                    .append("address", punishment.address)
                    .append("created_at", punishment.createdAt)
                    .append("expires_at", punishment.expiresAt)
                    .append("active", punishment.active);
            mongoDatabase.getCollection("punishments").replaceOne(Filters.eq("id", punishment.id), doc, new ReplaceOptions().upsert(true));
            return;
        }
        String sql = backendType == BackendType.MYSQL
                ? "INSERT INTO punishments(id,target_uuid,target_name,actor_uuid,actor_name,type,reason,proof,notes,address,created_at,expires_at,active) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE target_uuid=VALUES(target_uuid),target_name=VALUES(target_name),actor_uuid=VALUES(actor_uuid),actor_name=VALUES(actor_name),type=VALUES(type),reason=VALUES(reason),proof=VALUES(proof),notes=VALUES(notes),address=VALUES(address),created_at=VALUES(created_at),expires_at=VALUES(expires_at),active=VALUES(active)"
                : "INSERT INTO punishments(id,target_uuid,target_name,actor_uuid,actor_name,type,reason,proof,notes,address,created_at,expires_at,active) VALUES (?,?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(id) DO UPDATE SET target_uuid=excluded.target_uuid,target_name=excluded.target_name,actor_uuid=excluded.actor_uuid,actor_name=excluded.actor_name,type=excluded.type,reason=excluded.reason,proof=excluded.proof,notes=excluded.notes,address=excluded.address,created_at=excluded.created_at,expires_at=excluded.expires_at,active=excluded.active";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bindPunishment(ps, punishment);
            ps.executeUpdate();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to save punishment", e);
        }
    }

    public List<PunishmentData> getPunishments(String targetUuid, boolean activeOnly) {
        List<PunishmentData> result = new ArrayList<>();
        long now = System.currentTimeMillis();
        if (backendType == BackendType.MONGO) {
            Object filter = targetUuid == null || targetUuid.isBlank() ? new Document() : Filters.eq("target_uuid", targetUuid);
            for (Document doc : mongoDatabase.getCollection("punishments").find((org.bson.conversions.Bson) filter)) {
                PunishmentData data = punishmentFromDocument(doc);
                if (activeOnly && (!data.active || data.isExpired(now))) {
                    continue;
                }
                result.add(data);
            }
            return result;
        }
        StringBuilder sql = new StringBuilder("SELECT * FROM punishments");
        if (targetUuid != null && !targetUuid.isBlank()) {
            sql.append(" WHERE target_uuid=?");
        }
        sql.append(" ORDER BY created_at DESC");
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql.toString())) {
            if (targetUuid != null && !targetUuid.isBlank()) {
                ps.setString(1, targetUuid);
            }
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    PunishmentData data = punishmentFromResultSet(rs);
                    if (activeOnly && (!data.active || data.isExpired(now))) {
                        continue;
                    }
                    result.add(data);
                }
            }
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to load punishments", e);
        }
        return result;
    }

    public boolean deactivatePunishment(String id) {
        if (id == null || id.isBlank()) {
            return false;
        }
        if (backendType == BackendType.MONGO) {
            return mongoDatabase.getCollection("punishments")
                    .updateOne(Filters.eq("id", id), new Document("$set", new Document("active", false)))
                    .getModifiedCount() > 0;
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("UPDATE punishments SET active=0 WHERE id=?")) {
            ps.setString(1, id);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to deactivate punishment", e);
            return false;
        }
    }

    public int deactivatePunishmentsForTarget(String targetUuid, String typeLike) {
        if (targetUuid == null || targetUuid.isBlank()) {
            return 0;
        }
        if (backendType == BackendType.MONGO) {
            Document filter = new Document("target_uuid", targetUuid).append("active", true);
            return (int) mongoDatabase.getCollection("punishments")
                    .updateMany(filter, new Document("$set", new Document("active", false)))
                    .getModifiedCount();
        }
        String sql = "UPDATE punishments SET active=0 WHERE target_uuid=?" +
                (typeLike == null || typeLike.isBlank() ? "" : " AND type LIKE ?");
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            ps.setString(1, targetUuid);
            if (typeLike != null && !typeLike.isBlank()) {
                ps.setString(2, typeLike);
            }
            return ps.executeUpdate();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to deactivate target punishments", e);
            return 0;
        }
    }

    public void upsertClan(ClanData clan) {
        if (clan == null || clan.name == null || clan.name.isBlank()) {
            return;
        }
        if (backendType == BackendType.MONGO) {
            Document doc = new Document("name", clan.name)
                    .append("tag", clan.tag)
                    .append("owner_uuid", clan.ownerUuid)
                    .append("home_server", clan.homeServer)
                    .append("friendly_fire", clan.friendlyFire)
                    .append("created_at", clan.createdAt)
                    .append("wins", clan.wins)
                    .append("kills", clan.kills)
                    .append("points", clan.points)
                    .append("admins", clan.admins)
                    .append("moderators", clan.moderators)
                    .append("members", clan.members);
            mongoDatabase.getCollection("clans").replaceOne(Filters.eq("name", clan.name), doc, new ReplaceOptions().upsert(true));
            return;
        }
        String sql = backendType == BackendType.MYSQL
                ? "INSERT INTO clans(name,tag,owner_uuid,home_server,friendly_fire,created_at,wins,kills,points,admins_json,moderators_json,members_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON DUPLICATE KEY UPDATE tag=VALUES(tag),owner_uuid=VALUES(owner_uuid),home_server=VALUES(home_server),friendly_fire=VALUES(friendly_fire),created_at=VALUES(created_at),wins=VALUES(wins),kills=VALUES(kills),points=VALUES(points),admins_json=VALUES(admins_json),moderators_json=VALUES(moderators_json),members_json=VALUES(members_json)"
                : "INSERT INTO clans(name,tag,owner_uuid,home_server,friendly_fire,created_at,wins,kills,points,admins_json,moderators_json,members_json) VALUES (?,?,?,?,?,?,?,?,?,?,?,?) ON CONFLICT(name) DO UPDATE SET tag=excluded.tag,owner_uuid=excluded.owner_uuid,home_server=excluded.home_server,friendly_fire=excluded.friendly_fire,created_at=excluded.created_at,wins=excluded.wins,kills=excluded.kills,points=excluded.points,admins_json=excluded.admins_json,moderators_json=excluded.moderators_json,members_json=excluded.members_json";
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement(sql)) {
            bindClan(ps, clan);
            ps.executeUpdate();
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to save clan", e);
        }
    }

    public ClanData getClan(String name) {
        if (name == null || name.isBlank()) {
            return null;
        }
        if (backendType == BackendType.MONGO) {
            return clanFromDocument(mongoDatabase.getCollection("clans").find(Filters.eq("name", name)).first());
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("SELECT * FROM clans WHERE name=?")) {
            ps.setString(1, name);
            try (ResultSet rs = ps.executeQuery()) {
                return rs.next() ? clanFromResultSet(rs) : null;
            }
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to load clan", e);
            return null;
        }
    }

    public List<ClanData> getClans() {
        List<ClanData> result = new ArrayList<>();
        if (backendType == BackendType.MONGO) {
            for (Document doc : mongoDatabase.getCollection("clans").find()) {
                result.add(clanFromDocument(doc));
            }
            return result;
        }
        try (Connection c = dataSource.getConnection();
             PreparedStatement ps = c.prepareStatement("SELECT * FROM clans ORDER BY points DESC, name ASC");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                result.add(clanFromResultSet(rs));
            }
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to load clans", e);
        }
        return result;
    }

    public boolean deleteClan(String name) {
        if (name == null || name.isBlank()) {
            return false;
        }
        if (backendType == BackendType.MONGO) {
            return mongoDatabase.getCollection("clans").deleteOne(Filters.eq("name", name)).getDeletedCount() > 0;
        }
        try (Connection c = dataSource.getConnection(); PreparedStatement ps = c.prepareStatement("DELETE FROM clans WHERE name=?")) {
            ps.setString(1, name);
            return ps.executeUpdate() > 0;
        } catch (SQLException e) {
            CentralLogger.error("DataStore", "Failed to delete clan", e);
            return false;
        }
    }

    private void logDbErrorThrottled(String action, Exception e, String message) {
        String signature = action + "|" + (e == null ? "-" : String.valueOf(e.getMessage()));
        long now = System.currentTimeMillis();
        if (!signature.equals(lastDbErrorSignature) || (now - lastDbErrorLogAt) > 120_000L) {
            if (e == null) {
                CentralLogger.warn("DataStore", message);
            } else {
                CentralLogger.error("DataStore", message, e);
            }
            lastDbErrorSignature = signature;
            lastDbErrorLogAt = now;
        }
    }

    private void bindPunishment(PreparedStatement ps, PunishmentData p) throws SQLException {
        ps.setString(1, p.id);
        ps.setString(2, p.targetUuid);
        ps.setString(3, p.targetName);
        ps.setString(4, p.actorUuid);
        ps.setString(5, p.actorName);
        ps.setString(6, p.type);
        ps.setString(7, p.reason);
        ps.setString(8, p.proof);
        ps.setString(9, p.notes);
        ps.setString(10, p.address);
        ps.setLong(11, p.createdAt);
        ps.setLong(12, p.expiresAt);
        ps.setInt(13, p.active ? 1 : 0);
    }

    private PunishmentData punishmentFromResultSet(ResultSet rs) throws SQLException {
        PunishmentData p = new PunishmentData();
        p.id = rs.getString("id");
        p.targetUuid = rs.getString("target_uuid");
        p.targetName = rs.getString("target_name");
        p.actorUuid = rs.getString("actor_uuid");
        p.actorName = rs.getString("actor_name");
        p.type = rs.getString("type");
        p.reason = rs.getString("reason");
        p.proof = rs.getString("proof");
        p.notes = rs.getString("notes");
        p.address = rs.getString("address");
        p.createdAt = rs.getLong("created_at");
        p.expiresAt = rs.getLong("expires_at");
        p.active = rs.getInt("active") == 1;
        return p;
    }

    private PunishmentData punishmentFromDocument(Document doc) {
        PunishmentData p = new PunishmentData();
        if (doc == null) {
            return p;
        }
        p.id = doc.getString("id");
        p.targetUuid = doc.getString("target_uuid");
        p.targetName = doc.getString("target_name");
        p.actorUuid = doc.getString("actor_uuid");
        p.actorName = doc.getString("actor_name");
        p.type = doc.getString("type");
        p.reason = doc.getString("reason");
        p.proof = doc.getString("proof");
        p.notes = doc.getString("notes");
        p.address = doc.getString("address");
        p.createdAt = doc.getLong("created_at") == null ? 0L : doc.getLong("created_at");
        p.expiresAt = doc.getLong("expires_at") == null ? 0L : doc.getLong("expires_at");
        p.active = doc.getBoolean("active", true);
        return p;
    }

    private void bindClan(PreparedStatement ps, ClanData c) throws SQLException {
        ps.setString(1, c.name);
        ps.setString(2, c.tag);
        ps.setString(3, c.ownerUuid);
        ps.setString(4, c.homeServer);
        ps.setInt(5, c.friendlyFire ? 1 : 0);
        ps.setLong(6, c.createdAt);
        ps.setInt(7, c.wins);
        ps.setInt(8, c.kills);
        ps.setInt(9, c.points);
        ps.setString(10, gson.toJson(c.admins));
        ps.setString(11, gson.toJson(c.moderators));
        ps.setString(12, gson.toJson(c.members));
    }

    private ClanData clanFromResultSet(ResultSet rs) throws SQLException {
        ClanData c = new ClanData();
        c.name = rs.getString("name");
        c.tag = rs.getString("tag");
        c.ownerUuid = rs.getString("owner_uuid");
        c.homeServer = rs.getString("home_server");
        c.friendlyFire = rs.getInt("friendly_fire") == 1;
        c.createdAt = rs.getLong("created_at");
        c.wins = rs.getInt("wins");
        c.kills = rs.getInt("kills");
        c.points = rs.getInt("points");
        Type listType = new TypeToken<List<String>>() {}.getType();
        c.admins = gson.fromJson(rs.getString("admins_json"), listType);
        c.moderators = gson.fromJson(rs.getString("moderators_json"), listType);
        c.members = gson.fromJson(rs.getString("members_json"), listType);
        if (c.admins == null) c.admins = new ArrayList<>();
        if (c.moderators == null) c.moderators = new ArrayList<>();
        if (c.members == null) c.members = new ArrayList<>();
        return c;
    }

    @SuppressWarnings("unchecked")
    private ClanData clanFromDocument(Document doc) {
        if (doc == null) {
            return null;
        }
        ClanData c = new ClanData();
        c.name = doc.getString("name");
        c.tag = doc.getString("tag");
        c.ownerUuid = doc.getString("owner_uuid");
        c.homeServer = doc.getString("home_server");
        c.friendlyFire = doc.getBoolean("friendly_fire", false);
        c.createdAt = doc.getLong("created_at") == null ? 0L : doc.getLong("created_at");
        c.wins = doc.getInteger("wins", 0);
        c.kills = doc.getInteger("kills", 0);
        c.points = doc.getInteger("points", 0);
        c.admins = doc.get("admins", List.class);
        c.moderators = doc.get("moderators", List.class);
        c.members = doc.get("members", List.class);
        if (c.admins == null) c.admins = new ArrayList<>();
        if (c.moderators == null) c.moderators = new ArrayList<>();
        if (c.members == null) c.members = new ArrayList<>();
        return c;
    }

    private PlayerData fromResultSet(ResultSet rs) throws SQLException {
        PlayerData data = new PlayerData(rs.getString("player_uuid"));
        data.coins = rs.getInt("coins");
        Type mapType = new TypeToken<Map<String, Integer>>(){}.getType();
        Type listType = new TypeToken<List<String>>(){}.getType();
        data.stats = gson.fromJson(rs.getString("stats_json"), mapType);
        data.permissions = gson.fromJson(rs.getString("permissions_json"), listType);
        if (data.stats == null) data.stats = new HashMap<>();
        if (data.permissions == null) data.permissions = new ArrayList<>();
        data.lastServer = rs.getString("last_server");
        data.lastSeen = rs.getLong("last_seen");
        return data;
    }

    private PlayerData fromDocument(Document doc) {
        PlayerData data = new PlayerData(doc.getString("player_uuid"));
        data.coins = doc.getInteger("coins", 0);
        Type mapType = new TypeToken<Map<String, Integer>>(){}.getType();
        Type listType = new TypeToken<List<String>>(){}.getType();
        data.stats = gson.fromJson(doc.getString("stats_json"), mapType);
        data.permissions = gson.fromJson(doc.getString("permissions_json"), listType);
        if (data.stats == null) data.stats = new HashMap<>();
        if (data.permissions == null) data.permissions = new ArrayList<>();
        data.lastServer = doc.getString("last_server");
        data.lastSeen = doc.getLong("last_seen") == null ? 0L : doc.getLong("last_seen");
        return data;
    }

    private PermissionGroup fromPermissionResultSet(ResultSet rs) throws SQLException {
        PermissionGroup group = new PermissionGroup(rs.getString("group_name"));
        group.parent = rs.getString("parent_group");
        group.weight = rs.getInt("weight");
        group.prefix = rs.getString("prefix");
        group.suffix = rs.getString("suffix");
        Type listType = new TypeToken<List<String>>() {}.getType();
        group.permissions = gson.fromJson(rs.getString("permissions_json"), listType);
        if (group.permissions == null) {
            group.permissions = new ArrayList<>();
        }
        return group;
    }

    private PermissionGroup fromPermissionDoc(Document doc) {
        if (doc == null) {
            return null;
        }
        PermissionGroup group = new PermissionGroup(doc.getString("group_name"));
        group.parent = doc.getString("parent_group");
        group.weight = doc.getInteger("weight", 0);
        group.prefix = doc.getString("prefix");
        group.suffix = doc.getString("suffix");
        Type listType = new TypeToken<List<String>>() {}.getType();
        group.permissions = gson.fromJson(doc.getString("permissions_json"), listType);
        if (group.permissions == null) {
            group.permissions = new ArrayList<>();
        }
        return group;
    }
}
