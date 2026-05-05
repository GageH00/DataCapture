package com.comicdatacapture.comicdatacapture.controller;

import com.comicdatacapture.comicdatacapture.app.AppState;
import com.comicdatacapture.comicdatacapture.app.viewManager;
import com.comicdatacapture.comicdatacapture.service.DatabaseService;
import com.comicdatacapture.comicdatacapture.service.ExportService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.stage.FileChooser;
import javafx.stage.Stage;

import java.io.File;
import java.sql.*;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;

/**
 * Controller for session-end-view.fxml.
 *
 * Populates the summary grid by querying the local H2 database for the
 * completed session, then provides CSV export and PostgreSQL push.
 */
public class sessionEndController {

    @FXML private Label summarySessionId;
    @FXML private Label summaryStarted;
    @FXML private Label summaryEnded;
    @FXML private Label summaryBatches;
    @FXML private Label summaryEntries;
    @FXML private Label summaryImages;
    @FXML private Label summaryOutputFolder;
    @FXML private Label exportStatusLabel;

    private int sessionId = -1;

    private static final DateTimeFormatter DISPLAY_FMT =
            DateTimeFormatter.ofPattern("MMM d, yyyy  h:mm a");

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        AppState state = AppState.getInstance();
        String sid = state.getCompletedSessionId();
        if (sid == null) return;

        try {
            sessionId = Integer.parseInt(sid);
        } catch (NumberFormatException e) {
            showExportStatus("Could not parse session id: " + sid, true);
            return;
        }

        populateSummary(state.getOutputFolder());
    }

    private void populateSummary(String outputFolder) {
        try {
            Connection conn = DatabaseService.getInstance().getConnection();

            // Session started_at — stored as local time (no timezone).
            // rs.getTimestamp() interprets the value as local time, which is correct.
            String startedAt = "—";
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT started_at FROM sessions WHERE id = ?")) {
                ps.setInt(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) {
                        Timestamp ts = rs.getTimestamp("started_at");
                        if (ts != null)
                            startedAt = ts.toLocalDateTime().format(DISPLAY_FMT);
                    }
                }
            }

            // Batch count
            int batches = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM batches WHERE session_id = ?")) {
                ps.setInt(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) batches = rs.getInt(1);
                }
            }

            // Entry count
            int entries = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM entry e "
                  + "JOIN batches b ON b.id = e.batch_id "
                  + "WHERE b.session_id = ?")) {
                ps.setInt(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) entries = rs.getInt(1);
                }
            }

            // Image count
            int images = 0;
            try (PreparedStatement ps = conn.prepareStatement(
                    "SELECT COUNT(*) FROM images i "
                  + "JOIN batches b ON b.id = i.batch_id "
                  + "WHERE b.session_id = ?")) {
                ps.setInt(1, sessionId);
                try (ResultSet rs = ps.executeQuery()) {
                    if (rs.next()) images = rs.getInt(1);
                }
            }

            // "Ended" — use the time recorded by sessionController at the moment
            // the operator clicked End Session, not LocalDateTime.now() at init time.
            String endedAt = "—";
            String rawEndTime = AppState.getInstance().getCompletedSessionEndTime();
            if (rawEndTime != null) {
                try {
                    endedAt = LocalDateTime.parse(rawEndTime).format(DISPLAY_FMT);
                } catch (Exception e) {
                    endedAt = rawEndTime;  // show raw if parse fails
                }
            }

            summarySessionId.setText(String.valueOf(sessionId));
            summaryStarted.setText(startedAt);
            summaryEnded.setText(endedAt);
            summaryBatches.setText(String.valueOf(batches));
            summaryEntries.setText(String.valueOf(entries));
            summaryImages.setText(String.valueOf(images));
            summaryOutputFolder.setText(outputFolder != null ? outputFolder : "—");

        } catch (SQLException e) {
            showExportStatus("Could not load summary: " + e.getMessage(), true);
        }
    }

    // ── Export ────────────────────────────────────────────────────────────────

    @FXML
    private void onExportCsv() {
        FileChooser chooser = new FileChooser();
        chooser.setTitle("Export Session to CSV");
        chooser.setInitialFileName(
                ExportService.getInstance().defaultExportName("csv"));
        chooser.getExtensionFilters().add(
                new FileChooser.ExtensionFilter("CSV Files", "*.csv"));

        Stage stage = (Stage) summarySessionId.getScene().getWindow();
        File file = chooser.showSaveDialog(stage);
        if (file == null) return;

        try {
            ExportService.getInstance().exportCsv(
                    file.toPath(),
                    sessionId >= 0 ? sessionId : null);
            showExportStatus("✓ Exported to " + file.getName(), false);
        } catch (Exception e) {
            showExportStatus("Export failed: " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    @FXML
    private void onExportToPostgres() {
        Dialog<String[]> dialog = new Dialog<>();
        dialog.setTitle("Push to PostgreSQL");
        dialog.setHeaderText("Enter remote PostgreSQL connection details");

        ButtonType pushBtn = new ButtonType("Push", ButtonBar.ButtonData.OK_DONE);
        dialog.getDialogPane().getButtonTypes().addAll(pushBtn, ButtonType.CANCEL);

        javafx.scene.layout.GridPane grid = new javafx.scene.layout.GridPane();
        grid.setHgap(10);
        grid.setVgap(10);
        grid.setPadding(new Insets(16));

        TextField    urlField  = new TextField("jdbc:postgresql://localhost:5432/comic_intake");
        TextField    userField = new TextField("postgres");
        PasswordField passField = new PasswordField();

        grid.add(new Label("JDBC URL:"),  0, 0); grid.add(urlField,  1, 0);
        grid.add(new Label("Username:"),  0, 1); grid.add(userField, 1, 1);
        grid.add(new Label("Password:"),  0, 2); grid.add(passField, 1, 2);
        dialog.getDialogPane().setContent(grid);

        dialog.setResultConverter(btn -> btn == pushBtn
                ? new String[]{urlField.getText(), userField.getText(), passField.getText()}
                : null);

        dialog.showAndWait().ifPresent(creds -> {
            try {
                ExportService.getInstance().pushToPostgres(
                        creds[0], creds[1], creds[2],
                        sessionId >= 0 ? sessionId : null);
                showExportStatus("✓ Session pushed to remote database.", false);
            } catch (Exception e) {
                showExportStatus("Push failed: " + e.getMessage(), true);
                e.printStackTrace();
            }
        });
    }

    // ── Navigation ────────────────────────────────────────────────────────────

    @FXML
    private void onNewSession() {
        AppState state = AppState.getInstance();
        state.setCompletedSessionId(null);
        state.setActiveSession(null);
        state.setCurrentBatchId(null);

        Stage stage = (Stage) summarySessionId.getScene().getWindow();
        viewManager.setScene(stage, "intakeLanding-view.fxml", "Comic Intake Design");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    private void showExportStatus(String msg, boolean error) {
        exportStatusLabel.setText(msg);
        exportStatusLabel.setStyle("-fx-text-fill: " + (error ? "#d32f2f" : "#2e7d32") + ";");
    }
}
