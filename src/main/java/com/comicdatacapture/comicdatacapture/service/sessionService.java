package com.comicdatacapture.comicdatacapture.service;

import com.comicdatacapture.comicdatacapture.model.FieldDefinition;
import com.comicdatacapture.comicdatacapture.model.Session;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.List;

/**
 * Manages session and batch lifecycle — creation and DB persistence.
 * Delegates to DatabaseService (H2 or PostgreSQL depending on db.properties).
 */
public class sessionService {

    private static final sessionService INSTANCE = new sessionService();
    public static sessionService getInstance() { return INSTANCE; }

    private sessionService() {}

    /**
     * Inserts a session row and snapshots the field definitions for audit.
     *
     * @param operatorId  operator identifier (stored in-memory only for now)
     * @param fields      active FieldDefinition list from the CaptureProfile
     */
    public Session startSession(String operatorId, List<FieldDefinition> fields) {
        // Use LocalDateTime (local time, no timezone) so the timestamp stored in
        // the DB matches what the operator sees on their clock — not UTC.
        String startedAt = LocalDateTime.now().toString();
        try {
            DatabaseService db = DatabaseService.getInstance();
            int dbId = db.insertSession(startedAt);
            db.insertFieldDefinitions(dbId, fields);
            System.out.println("[sessionService] Session started: id=" + dbId
                    + " fields=" + fields.size());
            return new Session(String.valueOf(dbId),
                    operatorId != null ? operatorId : "unknown");
        } catch (SQLException e) {
            throw new RuntimeException(
                    "[sessionService] Failed to start session: " + e.getMessage(), e);
        }
    }

    /**
     * Inserts a batch row linked to the given session.
     *
     * @param sessionId  string form of sessions.id
     * @param itemLabel  operator-supplied label (e.g. "batch_1")
     * @return string form of the auto-generated batches.id
     */
    public String startBatch(String sessionId, String itemLabel) {
        try {
            int sessionPk = Integer.parseInt(sessionId);
            int batchId   = DatabaseService.getInstance().insertBatch(sessionPk, itemLabel);
            System.out.println("[sessionService] Batch started: id=" + batchId);
            return String.valueOf(batchId);
        } catch (SQLException e) {
            throw new RuntimeException(
                    "[sessionService] Failed to start batch: " + e.getMessage(), e);
        }
    }
}
