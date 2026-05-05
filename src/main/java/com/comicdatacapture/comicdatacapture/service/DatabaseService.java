package com.comicdatacapture.comicdatacapture.service;

import com.comicdatacapture.comicdatacapture.model.FieldDefinition;
import com.google.gson.Gson;

import java.io.*;
import java.nio.file.*;
import java.sql.*;
import java.util.List;
import java.util.Map;
import java.util.Properties;

/**
 * Singleton JDBC wrapper supporting H2 (embedded, default) and PostgreSQL.
 *
 * Schema syntax fix
 * ══════════════════
 * The db.properties file uses MODE=PostgreSQL for H2. In this mode H2 adopts
 * PostgreSQL's SQL dialect — which means AUTO_INCREMENT is not recognised.
 * AUTO_INCREMENT is MySQL/H2-native syntax only.
 *
 * In PostgreSQL mode (both real Postgres and H2 with MODE=PostgreSQL) the
 * correct auto-increment primary key syntax is SERIAL:
 *
 *   SERIAL PRIMARY KEY  →  equivalent to INT + sequence + DEFAULT nextval()
 *
 * SchemaInitializer now uses SERIAL for both the H2 and Postgres paths,
 * since H2 in PostgreSQL mode accepts SERIAL identically to real Postgres.
 *
 * The isPostgres flag is kept only to choose between VARCHAR (H2 json storage)
 * and JSONB (real Postgres native type), which H2 does NOT support even in
 * PostgreSQL mode.
 */
public class DatabaseService {

    private static final DatabaseService INSTANCE = new DatabaseService();
    public static DatabaseService getInstance() { return INSTANCE; }

    private static final Path PROPS_PATH = Paths.get(
            System.getProperty("user.home"), "ComicDataCapture", "db.properties");

    private Connection connection;
    private final Gson gson = new Gson();

    private DatabaseService() {}

    // ── Connection lifecycle ──────────────────────────────────────────────────

    public void connect() throws SQLException {
        if (connection != null && !connection.isClosed()) return;

        Properties props = loadOrCreateProps();
        String url  = props.getProperty("db.url");
        String user = props.getProperty("db.user",     "sa");
        String pass = props.getProperty("db.password", "");

        connection = DriverManager.getConnection(url, user, pass);
        connection.setAutoCommit(true);
        System.out.println("[DatabaseService] Connected: " + url);

        // H2 with MODE=PostgreSQL accepts SERIAL just like real Postgres.
        // The only difference we need to track is JSONB vs VARCHAR for the
        // entry.data column — H2 doesn't support JSONB even in Postgres mode.
        boolean isRealPostgres = url.startsWith("jdbc:postgresql");
        SchemaInitializer.run(connection, isRealPostgres);
    }

    public void disconnect() {
        if (connection != null) {
            try {
                connection.close();
                System.out.println("[DatabaseService] Disconnected.");
            } catch (SQLException e) {
                System.err.println("[DatabaseService] Close error: " + e.getMessage());
            } finally {
                connection = null;
            }
        }
    }

    public boolean isConnected() {
        try { return connection != null && !connection.isClosed(); }
        catch (SQLException e) { return false; }
    }

    public Connection getConnection() { return connection; }

    // ── Session ───────────────────────────────────────────────────────────────

    public int insertSession(String startedAt) throws SQLException {
        // startedAt arrives as an ISO-8601 string from Instant.now().toString() (UTC).
        // Strip the trailing 'Z' and the 'T' separator so H2 / Postgres accepts it
        // as a plain TIMESTAMP literal.  Both databases store it as-is without
        // timezone conversion, so we pass local time instead of UTC to avoid the
        // display offset seen in the session summary ("Started" showing a future date).
        // sessionService now passes LocalDateTime.now().toString() rather than
        // Instant.now().toString() to ensure the value is already in local time.
        String sql = "INSERT INTO sessions (started_at) VALUES (CAST(? AS TIMESTAMP))";
        try (PreparedStatement ps = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            // Accept both ISO-8601 with T/Z (legacy) and plain local datetime strings
            String normalized = startedAt
                    .replace("T", " ")
                    .replaceAll("Z$", "")           // remove trailing Z (UTC marker)
                    .replaceAll("\\.\\d+$", "");     // strip sub-second precision H2 may reject
            ps.setString(1, normalized);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
                throw new SQLException("insertSession: no generated key.");
            }
        }
    }

    // ── Field definitions ─────────────────────────────────────────────────────

    public void insertFieldDefinitions(int sessionId, List<FieldDefinition> fields)
            throws SQLException {
        String sql = "INSERT INTO field_definitions "
                + "(session_id, field_order, label, db_key, field_type, required, options) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(sql)) {
            for (int i = 0; i < fields.size(); i++) {
                FieldDefinition f = fields.get(i);
                ps.setInt(1, sessionId);
                ps.setInt(2, i);
                ps.setString(3, f.getLabel());
                ps.setString(4, f.getDbKey());
                ps.setString(5, f.getType().name());
                ps.setBoolean(6, f.isRequired());
                if (f.getType() == FieldDefinition.FieldType.DROPDOWN
                        && f.getDropdownOptions() != null) {
                    ps.setString(7, String.join(",", f.getDropdownOptions()));
                } else {
                    ps.setNull(7, Types.VARCHAR);
                }
                ps.addBatch();
            }
            ps.executeBatch();
        }
    }

    // ── Batch ─────────────────────────────────────────────────────────────────

    public int insertBatch(int sessionId, String itemLabel) throws SQLException {
        String sql = "INSERT INTO batches (session_id, item_label) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, sessionId);
            ps.setString(2, itemLabel);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
                throw new SQLException("insertBatch: no generated key.");
            }
        }
    }

    // ── Entry ─────────────────────────────────────────────────────────────────

    public int insertEntry(int batchId, Map<String, Object> fields) throws SQLException {
        String json = gson.toJson(fields);
        String sql  = "INSERT INTO entry (batch_id, data) VALUES (?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, batchId);
            ps.setString(2, json);
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) {
                    int id = rs.getInt(1);
                    System.out.println("[DatabaseService] Entry " + id + ": " + json);
                    return id;
                }
                throw new SQLException("insertEntry: no generated key.");
            }
        }
    }

    // ── Image ─────────────────────────────────────────────────────────────────

    public int insertImage(int batchId, Integer entryId, String imageType, String filePath)
            throws SQLException {
        String sql = "INSERT INTO images (batch_id, entry_id, image_type, file_path) "
                + "VALUES (?, ?, ?, ?)";
        try (PreparedStatement ps = connection.prepareStatement(
                sql, Statement.RETURN_GENERATED_KEYS)) {
            ps.setInt(1, batchId);
            if (entryId != null) ps.setInt(2, entryId);
            else ps.setNull(2, Types.INTEGER);
            ps.setString(3, imageType);
            ps.setString(4, filePath != null ? filePath : "");
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                if (rs.next()) return rs.getInt(1);
                throw new SQLException("insertImage: no generated key.");
            }
        }
    }

    // ── Schema initializer ────────────────────────────────────────────────────

    /**
     * Creates all tables if they don't already exist.
     *
     * SERIAL works identically in both:
     *   - Real PostgreSQL
     *   - H2 with MODE=PostgreSQL  ← this is what we use for embedded
     *
     * The only difference is the data column:
     *   - Real PostgreSQL: JSONB (native binary JSON with GIN indexing)
     *   - H2 PostgreSQL mode: VARCHAR(32000) stored as text
     *     (H2 does not support JSONB even in Postgres mode)
     */
    private static class SchemaInitializer {

        static void run(Connection conn, boolean isRealPostgres) throws SQLException {
            // SERIAL is valid for both real Postgres and H2 in PostgreSQL mode.
            // Do NOT use AUTO_INCREMENT here — that is H2-native syntax and is
            // rejected when MODE=PostgreSQL is set.
            String dataCol = isRealPostgres
                    ? "JSONB NOT NULL DEFAULT '{}'"
                    : "VARCHAR(32000) NOT NULL DEFAULT '{}'";

            String[] ddl = {
                    "CREATE TABLE IF NOT EXISTS sessions ("
                            + "id         SERIAL PRIMARY KEY,"
                            + "started_at TIMESTAMP DEFAULT NOW()"
                            + ")",

                    "CREATE TABLE IF NOT EXISTS batches ("
                            + "id         SERIAL PRIMARY KEY,"
                            + "session_id INT REFERENCES sessions(id),"
                            + "item_label VARCHAR(255),"
                            + "created_at TIMESTAMP DEFAULT NOW()"
                            + ")",

                    "CREATE TABLE IF NOT EXISTS field_definitions ("
                            + "id          SERIAL PRIMARY KEY,"
                            + "session_id  INT REFERENCES sessions(id),"
                            + "field_order INT NOT NULL,"
                            + "label       VARCHAR(255) NOT NULL,"
                            + "db_key      VARCHAR(255) NOT NULL,"
                            + "field_type  VARCHAR(50)  NOT NULL,"
                            + "required    BOOLEAN DEFAULT FALSE,"
                            + "options     VARCHAR(2000)"
                            + ")",

                    "CREATE TABLE IF NOT EXISTS entry ("
                            + "id          SERIAL PRIMARY KEY,"
                            + "batch_id    INT REFERENCES batches(id),"
                            + "captured_at TIMESTAMP DEFAULT NOW(),"
                            + "data        " + dataCol
                            + ")",

                    "CREATE TABLE IF NOT EXISTS images ("
                            + "id         SERIAL PRIMARY KEY,"
                            + "batch_id   INT REFERENCES batches(id),"
                            + "entry_id   INT REFERENCES entry(id),"
                            + "image_type VARCHAR(50),"
                            + "file_path  VARCHAR(1000) NOT NULL,"
                            + "created_at TIMESTAMP DEFAULT NOW()"
                            + ")"
            };

            try (Statement stmt = conn.createStatement()) {
                for (String sql : ddl) {
                    stmt.execute(sql);
                }
                // GIN index only applies to real Postgres JSONB columns
                if (isRealPostgres) {
                    stmt.execute(
                            "CREATE INDEX IF NOT EXISTS idx_entry_data ON entry USING gin(data)");
                }
            }
            System.out.println("[DatabaseService] Schema verified.");
        }
    }

    // ── db.properties ─────────────────────────────────────────────────────────

    private Properties loadOrCreateProps() {
        Properties props = new Properties();

        if (Files.exists(PROPS_PATH)) {
            try (Reader r = Files.newBufferedReader(PROPS_PATH)) {
                props.load(r);
                return props;
            } catch (IOException e) {
                System.err.println("[DatabaseService] Could not read db.properties: "
                        + e.getMessage());
            }
        }

        // First launch — write H2 embedded defaults
        Path dataDir = Paths.get(System.getProperty("user.home"),
                "ComicDataCapture", "data", "comic_intake");

        props.setProperty("db.url",
                "jdbc:h2:" + dataDir + ";MODE=PostgreSQL");
        props.setProperty("db.user",     "sa");
        props.setProperty("db.password", "");

        try {
            Files.createDirectories(PROPS_PATH.getParent());
            try (Writer w = Files.newBufferedWriter(PROPS_PATH)) {
                props.store(w,
                        "Comic Data Capture - database configuration\n"
                                + "Default: embedded H2 (zero install).\n"
                                + "To use PostgreSQL: change db.url to jdbc:postgresql://host/dbname\n"
                                + "NOTE: do not add AUTO_SERVER=TRUE or change MODE=PostgreSQL");
            }
            System.out.println("[DatabaseService] Created db.properties: " + PROPS_PATH);
        } catch (IOException e) {
            System.err.println("[DatabaseService] Could not write db.properties: "
                    + e.getMessage());
        }
        return props;
    }
}