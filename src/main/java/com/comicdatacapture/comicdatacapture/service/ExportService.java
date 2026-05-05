package com.comicdatacapture.comicdatacapture.service;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.io.*;
import java.lang.reflect.Type;
import java.nio.file.*;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * Exports captured session data from the local database to external formats.
 *
 * Supported targets
 * ══════════════════
 * 1. CSV        — one row per entry; field columns derived from field_definitions.
 *                 Images appended as comma-separated paths in a final column.
 *                 Zero extra dependencies.
 *
 * 2. Excel      — same structure as CSV exported as .xlsx via Apache POI.
 *                 Requires poi-ooxml in pom.xml (already listed in updated pom).
 *
 * 3. PostgreSQL — replays all session data into a remote Postgres database using
 *                 a caller-supplied JDBC URL.  Useful when the operator collected
 *                 data locally with H2 and needs to push it to a shared server.
 *
 * All export methods accept an optional sessionId filter.
 * Passing null exports ALL sessions.
 */
public class ExportService {

    private static final ExportService INSTANCE = new ExportService();
    public static ExportService getInstance() { return INSTANCE; }

    private final Gson gson = new Gson();
    private final Type mapType = new TypeToken<Map<String, Object>>() {}.getType();

    private ExportService() {}

    // ── CSV export ────────────────────────────────────────────────────────────

    /**
     * Exports entries to a TSV (tab-separated) file matching the required format:
     *
     *   Custom field 1 \t Custom field 2 \t ... \t Image capture Path A \t Image capture Path B
     *
     * One row per entry. No runtime/system columns (no session id, batch id,
     * timestamps, etc). Custom field columns appear in field_order sequence,
     * followed by the two image path columns as separate columns.
     *
     * @param outputPath destination file path
     * @param sessionId  filter to a single session, or null for all sessions
     */
    public void exportCsv(Path outputPath, Integer sessionId) throws SQLException, IOException {
        Connection conn = DatabaseService.getInstance().getConnection();

        List<String[]> fieldDefs = getFieldDefs(conn, sessionId);
        List<String[]> rows      = buildRows(conn, sessionId, fieldDefs);

        try (BufferedWriter w = Files.newBufferedWriter(outputPath)) {
            w.write(buildCsvHeader(fieldDefs));
            w.newLine();
            for (String[] row : rows) {
                w.write(joinCsv(row));
                w.newLine();
            }
        }
        System.out.println("[ExportService] CSV written: " + outputPath);
    }

    // ── Excel export ──────────────────────────────────────────────────────────

    /**
     * Exports entries to an Excel .xlsx file using Apache POI.
     *
     * Same column structure as CSV.  Requires:
     *   org.apache.poi:poi-ooxml:5.2.5 in pom.xml (see updated pom).
     */
    public void exportExcel(Path outputPath, Integer sessionId) throws Exception {
        Connection conn = DatabaseService.getInstance().getConnection();
        List<String[]> fieldDefs = getFieldDefs(conn, sessionId);
        List<String[]> rows      = buildRows(conn, sessionId, fieldDefs);

        // POI classes loaded reflectively so the app compiles even without POI
        // on the classpath — ExportService degrades gracefully; the button is
        // just disabled in the UI if POI is absent.
        Class<?> wbClass  = Class.forName("org.apache.poi.xssf.usermodel.XSSFWorkbook");
        Object   workbook = wbClass.getDeclaredConstructor().newInstance();

        Object sheet = wbClass.getMethod("createSheet", String.class)
                .invoke(workbook, "Entries");

        Class<?> sheetClass = sheet.getClass().getSuperclass().getSuperclass();
        java.lang.reflect.Method createRow =
                sheetClass.getMethod("createRow", int.class);

        // Write header
        writeExcelRow(createRow.invoke(sheet, 0), buildHeaderArray(fieldDefs));

        // Write data rows
        for (int i = 0; i < rows.size(); i++) {
            writeExcelRow(createRow.invoke(sheet, i + 1), rows.get(i));
        }

        try (OutputStream os = Files.newOutputStream(outputPath)) {
            wbClass.getMethod("write", OutputStream.class).invoke(workbook, os);
            wbClass.getMethod("close").invoke(workbook);
        }
        System.out.println("[ExportService] Excel written: " + outputPath);
    }

    // ── PostgreSQL push ───────────────────────────────────────────────────────

    /**
     * Pushes all data from the local H2 database into a remote PostgreSQL database.
     *
     * The target database must already have the schema applied
     * (run V1__schema_postgres.sql on it first, or let Flyway do it).
     *
     * This is the primary "hand off to enterprise" export path:
     *   1. Operator gathers data locally all day with H2
     *   2. At end of day, they click "Push to Server" and provide the JDBC URL
     *   3. This method replays all inserts into the remote DB
     *   4. Image file paths are preserved — if images live on a shared drive
     *      the remote system can access them immediately
     *
     * @param targetUrl  jdbc:postgresql://host:5432/dbname
     * @param targetUser postgres username
     * @param targetPass postgres password
     * @param sessionId  filter to one session, or null for all
     */
    public void pushToPostgres(String targetUrl, String targetUser, String targetPass,
                               Integer sessionId) throws SQLException {
        Connection src = DatabaseService.getInstance().getConnection();

        try (Connection dst = DriverManager.getConnection(targetUrl, targetUser, targetPass)) {
            dst.setAutoCommit(false);
            try {
                pushSessions(src, dst, sessionId);
                pushFieldDefinitions(src, dst, sessionId);
                pushBatches(src, dst, sessionId);
                pushEntries(src, dst, sessionId);
                pushImages(src, dst, sessionId);
                dst.commit();
                System.out.println("[ExportService] Push to Postgres complete: " + targetUrl);
            } catch (SQLException e) {
                dst.rollback();
                throw e;
            }
        }
    }

    // ── Row building ──────────────────────────────────────────────────────────

    /**
     * Returns ordered field definitions for the given session(s).
     * Each entry is [db_key, label].
     * Selects field_order explicitly so H2 allows ORDER BY without DISTINCT conflict.
     */
    private List<String[]> getFieldDefs(Connection conn, Integer sessionId) throws SQLException {
        String sql = sessionId != null
                ? "SELECT db_key, label, field_order FROM field_definitions "
                + "WHERE session_id = ? ORDER BY field_order"
                : "SELECT db_key, label, field_order FROM field_definitions ORDER BY field_order";

        List<String[]> defs = new ArrayList<>();
        Set<String> seen = new LinkedHashSet<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (sessionId != null) ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String key = rs.getString("db_key");
                    if (seen.add(key)) {
                        defs.add(new String[]{key, rs.getString("label")});
                    }
                }
            }
        }
        return defs;
    }

    private List<String[]> buildRows(Connection conn, Integer sessionId,
                                     List<String[]> fieldDefs) throws SQLException {
        // Fetch entries with their image paths split by image_type.
        // We use two LEFT JOINs — one for camera_a, one for camera_b —
        // so each appears as its own column rather than being pipe-joined.
        String sql =
                "SELECT e.id entry_id, e.data, " +
                        "       ia.file_path path_a, " +
                        "       ib.file_path path_b " +
                        "FROM entry e " +
                        "JOIN batches b ON b.id = e.batch_id " +
                        "JOIN sessions s ON s.id = b.session_id " +
                        "LEFT JOIN images ia ON ia.entry_id = e.id AND ia.image_type = 'camera_a' " +
                        "LEFT JOIN images ib ON ib.entry_id = e.id AND ib.image_type = 'camera_b' " +
                        (sessionId != null ? "WHERE s.id = ? " : "") +
                        "ORDER BY s.id, b.id, e.id";

        // Row width: one column per custom field + 2 image path columns
        int rowWidth = fieldDefs.size() + 2;

        List<String[]> rows = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(sql)) {
            if (sessionId != null) ps.setInt(1, sessionId);
            try (ResultSet rs = ps.executeQuery()) {
                while (rs.next()) {
                    String[] row = new String[rowWidth];
                    int col = 0;

                    // Custom field values — in field_order sequence
                    String json = rs.getString("data");
                    Map<String, Object> data = json != null
                            ? gson.fromJson(json, mapType) : Collections.emptyMap();
                    for (String[] def : fieldDefs) {
                        Object val = data.get(def[0]);
                        row[col++] = val != null ? val.toString() : "";
                    }

                    // Image paths as separate columns
                    String pathA = rs.getString("path_a");
                    String pathB = rs.getString("path_b");
                    row[col++] = pathA != null ? pathA : "";
                    row[col]   = pathB != null ? pathB : "";

                    rows.add(row);
                }
            }
        }
        return rows;
    }

    // ── PostgreSQL push helpers ───────────────────────────────────────────────

    private void pushSessions(Connection src, Connection dst, Integer sessionId)
            throws SQLException {
        String sel = "SELECT id, started_at FROM sessions"
                + (sessionId != null ? " WHERE id = ?" : "");
        String ins = "INSERT INTO sessions (id, started_at) VALUES (?, ?) ON CONFLICT (id) DO NOTHING";
        copyRows(src, dst, sel, ins, sessionId, rs -> new Object[]{
                rs.getInt("id"), rs.getTimestamp("started_at")
        });
    }

    private void pushFieldDefinitions(Connection src, Connection dst, Integer sessionId)
            throws SQLException {
        String sel = "SELECT * FROM field_definitions"
                + (sessionId != null ? " WHERE session_id = ?" : "");
        String ins = "INSERT INTO field_definitions "
                + "(id, session_id, field_order, label, db_key, field_type, required, options) "
                + "VALUES (?, ?, ?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";
        copyRows(src, dst, sel, ins, sessionId, rs -> new Object[]{
                rs.getInt("id"), rs.getInt("session_id"), rs.getInt("field_order"),
                rs.getString("label"), rs.getString("db_key"), rs.getString("field_type"),
                rs.getBoolean("required"), rs.getString("options")
        });
    }

    private void pushBatches(Connection src, Connection dst, Integer sessionId)
            throws SQLException {
        String sel = "SELECT b.* FROM batches b"
                + (sessionId != null ? " WHERE b.session_id = ?" : "");
        String ins = "INSERT INTO batches (id, session_id, item_label, created_at) "
                + "VALUES (?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";
        copyRows(src, dst, sel, ins, sessionId, rs -> new Object[]{
                rs.getInt("id"), rs.getInt("session_id"),
                rs.getString("item_label"), rs.getTimestamp("created_at")
        });
    }

    private void pushEntries(Connection src, Connection dst, Integer sessionId)
            throws SQLException {
        String sel = "SELECT e.* FROM entry e JOIN batches b ON b.id = e.batch_id"
                + (sessionId != null ? " WHERE b.session_id = ?" : "");
        // Cast to jsonb on the PostgreSQL side
        String ins = "INSERT INTO entry (id, batch_id, captured_at, data) "
                + "VALUES (?, ?, ?, ?::jsonb) ON CONFLICT (id) DO NOTHING";
        copyRows(src, dst, sel, ins, sessionId, rs -> new Object[]{
                rs.getInt("id"), rs.getInt("batch_id"),
                rs.getTimestamp("captured_at"), rs.getString("data")
        });
    }

    private void pushImages(Connection src, Connection dst, Integer sessionId)
            throws SQLException {
        String sel = "SELECT i.* FROM images i "
                + "JOIN batches b ON b.id = i.batch_id"
                + (sessionId != null ? " WHERE b.session_id = ?" : "");
        String ins = "INSERT INTO images (id, batch_id, entry_id, image_type, file_path, created_at) "
                + "VALUES (?, ?, ?, ?, ?, ?) ON CONFLICT (id) DO NOTHING";
        copyRows(src, dst, sel, ins, sessionId, rs -> new Object[]{
                rs.getInt("id"), rs.getInt("batch_id"), rs.getObject("entry_id"),
                rs.getString("image_type"), rs.getString("file_path"),
                rs.getTimestamp("created_at")
        });
    }

    @FunctionalInterface
    private interface RowMapper { Object[] map(ResultSet rs) throws SQLException; }

    private void copyRows(Connection src, Connection dst,
                          String selectSql, String insertSql,
                          Integer sessionId, RowMapper mapper) throws SQLException {
        try (PreparedStatement sel = src.prepareStatement(selectSql)) {
            if (sessionId != null) sel.setInt(1, sessionId);
            try (ResultSet rs = sel.executeQuery();
                 PreparedStatement ins = dst.prepareStatement(insertSql)) {
                while (rs.next()) {
                    Object[] vals = mapper.map(rs);
                    for (int i = 0; i < vals.length; i++) ins.setObject(i + 1, vals[i]);
                    ins.addBatch();
                }
                ins.executeBatch();
            }
        }
    }

    // ── CSV / Excel helpers ───────────────────────────────────────────────────

    /**
     * Builds the header row: custom field labels in order, then the two fixed
     * image path column headers.  No system/runtime columns included.
     */
    private String buildCsvHeader(List<String[]> fieldDefs) {
        return joinCsv(buildHeaderArray(fieldDefs));
    }

    private String[] buildHeaderArray(List<String[]> fieldDefs) {
        // Custom field human labels + two fixed image path columns
        List<String> cols = new ArrayList<>();
        for (String[] def : fieldDefs) cols.add(def[1]);   // human label (not db_key)
        cols.add("Image capture Path A");
        cols.add("Image capture Path B");
        return cols.toArray(new String[0]);
    }

    /**
     * Joins values with a COMMA separator (standard CSV).
     * Values containing commas, quotes, or newlines are double-quote escaped.
     */
    private String joinCsv(String[] values) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < values.length; i++) {
            if (i > 0) sb.append(',');
            String v = values[i] == null ? "" : values[i];
            if (v.contains(",") || v.contains("\"") || v.contains("\n")) {
                sb.append('"').append(v.replace("\"", "\"\"")).append('"');
            } else {
                sb.append(v);
            }
        }
        return sb.toString();
    }

    private void writeExcelRow(Object row, String[] values) throws Exception {
        Class<?> rowClass = row.getClass().getSuperclass().getSuperclass();
        java.lang.reflect.Method createCell = rowClass.getMethod("createCell", int.class);
        for (int i = 0; i < values.length; i++) {
            Object cell = createCell.invoke(row, i);
            cell.getClass().getMethod("setCellValue", String.class)
                    .invoke(cell, values[i] != null ? values[i] : "");
        }
    }

    // ── Convenience: export filename ──────────────────────────────────────────

    /** Generates a timestamped default filename for exports. */
    public String defaultExportName(String extension) {
        return "comic_intake_export_"
                + LocalDateTime.now().format(DateTimeFormatter.ofPattern("yyyyMMdd_HHmmss"))
                + "." + extension;
    }
}