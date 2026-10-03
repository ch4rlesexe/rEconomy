package net.ch4rlesexe.economy.db;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import net.ch4rlesexe.economy.REconomyPlugin;

import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;

public final class Database {
    private final REconomyPlugin plugin;
    private HikariDataSource dataSource;

    public Database(REconomyPlugin plugin) {
        this.plugin = plugin;
    }

    public void init() {
        var cfg = plugin.getConfig();
        HikariConfig hikari = new HikariConfig();
        hikari.setJdbcUrl("jdbc:mysql://" + cfg.getString("mysql.host") + ":" + cfg.getInt("mysql.port")
                + "/" + cfg.getString("mysql.database")
                + "?useSSL=false&allowPublicKeyRetrieval=true&characterEncoding=utf8");
        hikari.setUsername(cfg.getString("mysql.username"));
        hikari.setPassword(cfg.getString("mysql.password"));
        hikari.setMaximumPoolSize(cfg.getInt("mysql.pool-size", 8));
        hikari.setPoolName("rEconomy");
        hikari.setDriverClassName("com.mysql.cj.jdbc.Driver");
        dataSource = new HikariDataSource(hikari);
        migrate();
        plugin.getLogger().info("MySQL ready (" + cfg.getString("mysql.database") + ").");
    }

    private void migrate() {
        try (Connection c = connection(); Statement s = c.createStatement()) {
            s.execute("""
                CREATE TABLE IF NOT EXISTS balances (
                  uuid CHAR(36) PRIMARY KEY,
                  name VARCHAR(32) NOT NULL,
                  balance DOUBLE NOT NULL DEFAULT 0,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS transactions (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  uuid CHAR(36) NOT NULL,
                  amount DOUBLE NOT NULL,
                  reason VARCHAR(64) NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  INDEX (uuid)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS homes (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  uuid CHAR(36) NOT NULL,
                  name VARCHAR(32) NOT NULL,
                  world VARCHAR(64) NOT NULL,
                  x DOUBLE NOT NULL,
                  y DOUBLE NOT NULL,
                  z DOUBLE NOT NULL,
                  yaw FLOAT NOT NULL,
                  pitch FLOAT NOT NULL,
                  UNIQUE KEY uniq_home (uuid, name)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS teams (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  name VARCHAR(24) NOT NULL UNIQUE,
                  tag VARCHAR(8) NOT NULL,
                  owner_uuid CHAR(36) NOT NULL,
                  open_join TINYINT(1) NOT NULL DEFAULT 1,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            try {
                s.execute("ALTER TABLE teams ADD COLUMN open_join TINYINT(1) NOT NULL DEFAULT 1");
            } catch (SQLException ignored) {

            }
            s.execute("""
                CREATE TABLE IF NOT EXISTS team_members (
                  team_id BIGINT NOT NULL,
                  uuid CHAR(36) NOT NULL,
                  role VARCHAR(16) NOT NULL,
                  PRIMARY KEY (uuid),
                  INDEX (team_id)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS team_invites (
                  team_id BIGINT NOT NULL,
                  uuid CHAR(36) NOT NULL,
                  PRIMARY KEY (team_id, uuid),
                  INDEX (uuid)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS team_relations (
                  team_a BIGINT NOT NULL,
                  team_b BIGINT NOT NULL,
                  relation VARCHAR(16) NOT NULL,
                  PRIMARY KEY (team_a, team_b)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS claims (
                  world VARCHAR(64) NOT NULL,
                  chunk_x INT NOT NULL,
                  chunk_z INT NOT NULL,
                  team_id BIGINT NOT NULL,
                  PRIMARY KEY (world, chunk_x, chunk_z),
                  INDEX (team_id)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS claim_trusts (
                  team_id BIGINT NOT NULL,
                  uuid CHAR(36) NOT NULL,
                  level VARCHAR(16) NOT NULL,
                  PRIMARY KEY (team_id, uuid),
                  INDEX (uuid)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS team_homes (
                  team_id BIGINT NOT NULL,
                  name VARCHAR(32) NOT NULL,
                  world VARCHAR(64) NOT NULL,
                  x DOUBLE NOT NULL,
                  y DOUBLE NOT NULL,
                  z DOUBLE NOT NULL,
                  yaw FLOAT NOT NULL,
                  pitch FLOAT NOT NULL,
                  access_mode VARCHAR(16) NOT NULL DEFAULT 'all',
                  PRIMARY KEY (team_id, name)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS team_home_perms (
                  team_id BIGINT NOT NULL,
                  home_name VARCHAR(32) NOT NULL,
                  uuid CHAR(36) NOT NULL,
                  PRIMARY KEY (team_id, home_name, uuid),
                  INDEX (uuid)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS meta (
                  k VARCHAR(64) PRIMARY KEY,
                  v VARCHAR(255) NOT NULL
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS shops (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  owner_uuid CHAR(36) NOT NULL,
                  world VARCHAR(64) NOT NULL,
                  x INT NOT NULL,
                  y INT NOT NULL,
                  z INT NOT NULL,
                  mode CHAR(1) NOT NULL,
                  price DOUBLE NOT NULL,
                  amount INT NOT NULL,
                  material VARCHAR(64) NOT NULL,
                  UNIQUE KEY uniq_shop (world, x, y, z)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS auctions (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  seller_uuid CHAR(36) NOT NULL,
                  item_blob LONGBLOB NOT NULL,
                  buyout DOUBLE NOT NULL,
                  highest_bid DOUBLE NOT NULL DEFAULT 0,
                  highest_bidder CHAR(36) NULL,
                  expires_at BIGINT NOT NULL,
                  collected TINYINT(1) NOT NULL DEFAULT 0
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS wagers (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  challenger CHAR(36) NOT NULL,
                  target CHAR(36) NOT NULL,
                  amount DOUBLE NOT NULL,
                  status VARCHAR(16) NOT NULL,
                  winner CHAR(36) NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS coinflips (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  creator CHAR(36) NOT NULL,
                  amount DOUBLE NOT NULL,
                  side VARCHAR(8) NOT NULL,
                  status VARCHAR(16) NOT NULL,
                  joiner CHAR(36) NULL,
                  winner CHAR(36) NULL,
                  result_side VARCHAR(8) NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  INDEX (status)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS government_ledger (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  type VARCHAR(32) NOT NULL,
                  amount DOUBLE NOT NULL,
                  actor_uuid CHAR(36) NULL,
                  actor_name VARCHAR(32) NULL,
                  target_uuid CHAR(36) NULL,
                  target_name VARCHAR(32) NULL,
                  reason VARCHAR(96) NOT NULL,
                  balance_after DOUBLE NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  INDEX (created_at),
                  INDEX (type)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS market_items (
                  material VARCHAR(64) PRIMARY KEY,
                  base_price DOUBLE NOT NULL,
                  current_price DOUBLE NOT NULL,
                  stock INT NOT NULL,
                  buy_volume INT NOT NULL DEFAULT 0,
                  sell_volume INT NOT NULL DEFAULT 0,
                  frozen TINYINT(1) NOT NULL DEFAULT 0
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS market_price_history (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  material VARCHAR(64) NOT NULL,
                  price DOUBLE NOT NULL,
                  stock INT NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  INDEX (material)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS team_upgrades (
                  team_id BIGINT PRIMARY KEY,
                  bonus_homes INT NOT NULL DEFAULT 0,
                  bonus_claims INT NOT NULL DEFAULT 0
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS player_stats (
                  uuid CHAR(36) PRIMARY KEY,
                  name VARCHAR(32) NOT NULL,
                  kills INT NOT NULL DEFAULT 0,
                  deaths INT NOT NULL DEFAULT 0,
                  combat_logs INT NOT NULL DEFAULT 0,
                  killstreak INT NOT NULL DEFAULT 0,
                  best_killstreak INT NOT NULL DEFAULT 0
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS player_activity (
                  uuid CHAR(36) PRIMARY KEY,
                  name VARCHAR(32) NOT NULL,
                  playtime_ticks BIGINT NOT NULL DEFAULT 0,
                  mob_kills INT NOT NULL DEFAULT 0,
                  crop_harvests INT NOT NULL DEFAULT 0,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS player_mob_kills (
                  uuid CHAR(36) NOT NULL,
                  entity_type VARCHAR(64) NOT NULL,
                  kills INT NOT NULL DEFAULT 0,
                  PRIMARY KEY (uuid, entity_type),
                  INDEX (entity_type, kills)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS player_crop_harvests (
                  uuid CHAR(36) NOT NULL,
                  crop VARCHAR(64) NOT NULL,
                  amount INT NOT NULL DEFAULT 0,
                  PRIMARY KEY (uuid, crop),
                  INDEX (crop, amount)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS name_colors_owned (
                  uuid CHAR(36) NOT NULL,
                  color_id VARCHAR(32) NOT NULL,
                  purchased_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  PRIMARY KEY (uuid, color_id)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS name_colors_active (
                  uuid CHAR(36) PRIMARY KEY,
                  color_id VARCHAR(32) NOT NULL
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS image_maps (
                  map_id INT PRIMARY KEY,
                  url TEXT NOT NULL,
                  tiles INT NOT NULL,
                  tile_x INT NOT NULL,
                  tile_y INT NOT NULL,
                  tile_png LONGBLOB NOT NULL,
                  updated_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS shop_economy_items (
                  shop VARCHAR(64) NOT NULL,
                  product VARCHAR(64) NOT NULL,
                  material VARCHAR(64) NOT NULL,
                  item_name VARCHAR(96) NOT NULL,
                  buy_price DOUBLE NOT NULL DEFAULT 0,
                  sell_price DOUBLE NOT NULL DEFAULT 0,
                  supply INT NOT NULL DEFAULT -1,
                  stock_remaining INT NOT NULL DEFAULT 0,
                  purchased INT NOT NULL DEFAULT 0,
                  sold INT NOT NULL DEFAULT 0,
                  base_price DOUBLE NOT NULL DEFAULT 0,
                  sd_rate DOUBLE NOT NULL DEFAULT 1,
                  demand DOUBLE NOT NULL DEFAULT 1,
                  bonus DOUBLE NOT NULL DEFAULT 1,
                  base_amount DOUBLE NOT NULL DEFAULT 0,
                  week INT NOT NULL DEFAULT 0,
                  PRIMARY KEY (shop, product),
                  INDEX (material)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS shop_economy_tx (
                  id BIGINT AUTO_INCREMENT PRIMARY KEY,
                  shop VARCHAR(64) NOT NULL,
                  product VARCHAR(64) NOT NULL,
                  buy_or_sell VARCHAR(8) NOT NULL,
                  amount INT NOT NULL,
                  player_uuid CHAR(36) NULL,
                  player_name VARCHAR(32) NULL,
                  stock_after INT NOT NULL,
                  purchased_after INT NOT NULL,
                  sold_after INT NOT NULL,
                  created_at TIMESTAMP DEFAULT CURRENT_TIMESTAMP,
                  INDEX (shop, product),
                  INDEX (created_at)
                )
                """);
            s.execute("""
                CREATE TABLE IF NOT EXISTS shop_economy_meta (
                  meta_key VARCHAR(64) PRIMARY KEY,
                  meta_value TEXT NOT NULL
                )
                """);
        } catch (SQLException e) {
            throw new IllegalStateException("Failed to migrate database", e);
        }
    }

    public Connection connection() throws SQLException {
        return dataSource.getConnection();
    }

    public void close() {
        if (dataSource != null) {
            dataSource.close();
        }
    }
}
