package com.comicdatacapture.comicdatacapture.controller;

import com.comicdatacapture.comicdatacapture.app.AppState;
import com.comicdatacapture.comicdatacapture.app.viewManager;
import com.comicdatacapture.comicdatacapture.model.CameraConfig;
import com.comicdatacapture.comicdatacapture.model.CaptureProfile;
import com.comicdatacapture.comicdatacapture.model.FieldDefinition;
import com.comicdatacapture.comicdatacapture.service.DatabaseService;
import com.comicdatacapture.comicdatacapture.service.cameraService;
import com.comicdatacapture.comicdatacapture.service.sessionService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.geometry.Pos;
import javafx.scene.control.*;
import javafx.scene.image.Image;
import javafx.scene.image.ImageView;
import javafx.scene.layout.*;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.sql.SQLException;
import java.util.LinkedHashMap;
import java.util.Map;

public class sessionController {

    // ── FXML ─────────────────────────────────────────────────────────────────
    @FXML private Label     sessionIdLabel;
    @FXML private Label     batchCountLabel;
    @FXML private Label     statusLabel;
    @FXML private Label     cameraALabel;
    @FXML private Label     cameraBLabel;
    @FXML private Label     cameraAStateLabel;
    @FXML private Label     cameraBStateLabel;
    @FXML private Label     cameraAOverlay;
    @FXML private Label     cameraBOverlay;
    @FXML private ImageView cameraAImageView;
    @FXML private ImageView cameraBImageView;
    @FXML private Button    captureAButton;
    @FXML private Button    captureBButton;
    @FXML private Button    discardAButton;
    @FXML private Button    discardBButton;
    @FXML private VBox      dynamicFieldsContainer;
    @FXML private Button    saveEntryButton;

    private static final String BTN_RED =
            "-fx-background-color:#c62828;-fx-text-fill:white;"
          + "-fx-font-weight:bold;-fx-font-size:13px;-fx-background-radius:4;";
    private static final String BTN_GREEN =
            "-fx-background-color:#2e7d32;-fx-text-fill:white;"
          + "-fx-font-weight:bold;-fx-font-size:13px;-fx-background-radius:4;";
    private static final String BTN_DISCARD =
            "-fx-background-color:transparent;-fx-text-fill:#c62828;"
          + "-fx-font-size:11px;-fx-border-color:#e0e0e0;-fx-border-radius:4;"
          + "-fx-background-radius:4;-fx-cursor:hand;";

    private final cameraService camService = cameraService.getInstance();
    private final AppState      state      = AppState.getInstance();

    private final Map<String, Control> fieldControls = new LinkedHashMap<>();

    private int     batchCount      = 0;
    private int     currentBatchId  = -1;
    private String  pathCameraA     = null;
    private String  pathCameraB     = null;
    private boolean cameraACaptured = false;
    private boolean cameraBCaptured = false;

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        CaptureProfile profile = state.getActiveProfile();

        if (state.getActiveSession() != null)
            sessionIdLabel.setText("Session: " + state.getActiveSession().getSessionId());
        batchCountLabel.setText("Batches captured: 0");

        setCameraLabel(cameraALabel, profile.getCameraA(), "Camera A");
        setCameraLabel(cameraBLabel, profile.getCameraB(), "Camera B");

        captureAButton.setStyle(BTN_RED);
        captureBButton.setStyle(BTN_RED);
        discardAButton.setStyle(BTN_DISCARD);
        discardBButton.setStyle(BTN_DISCARD);
        discardAButton.setVisible(false);
        discardBButton.setVisible(false);

        CameraConfig camA = profile.getCameraA();
        CameraConfig camB = profile.getCameraB();
        if (camA != null && camA.getCameraName() != null)
            camService.startPreview(camA.getCameraName(), cameraAImageView, cameraAOverlay);
        if (camB != null && camB.getCameraName() != null)
            camService.startPreview(camB.getCameraName(), cameraBImageView, cameraBOverlay);

        buildDynamicFields(profile);
        openNewBatch();
    }

    // ── Field rendering ───────────────────────────────────────────────────────

    private void buildDynamicFields(CaptureProfile profile) {
        dynamicFieldsContainer.getChildren().clear();
        fieldControls.clear();

        HBox currentRow = null;
        int  rowSlot    = 0;

        for (FieldDefinition def : profile.getFields()) {
            String  labelText = resolveLabel(def);
            boolean wide      = isWideField(def);

            if (wide || currentRow == null || rowSlot >= 3) {
                if (currentRow != null)
                    dynamicFieldsContainer.getChildren().add(currentRow);
                currentRow = new HBox(16);
                currentRow.setAlignment(Pos.TOP_LEFT);
                rowSlot = 0;
            }

            VBox fieldBox = buildFieldBox(def, labelText, wide);
            if (wide) {
                HBox.setHgrow(fieldBox, Priority.ALWAYS);
                currentRow.getChildren().add(fieldBox);
                dynamicFieldsContainer.getChildren().add(currentRow);
                currentRow = null;
                rowSlot = 0;
            } else {
                HBox.setHgrow(fieldBox, Priority.SOMETIMES);
                currentRow.getChildren().add(fieldBox);
                rowSlot++;
            }
        }
        if (currentRow != null && !currentRow.getChildren().isEmpty())
            dynamicFieldsContainer.getChildren().add(currentRow);
    }

    private VBox buildFieldBox(FieldDefinition def, String labelText, boolean wide) {
        VBox box = new VBox(6);
        box.setPadding(new Insets(10, 0, 4, 0));
        if (wide) box.setMaxWidth(Double.MAX_VALUE);

        Separator sep = new Separator();
        sep.setOpacity(0.4);

        HBox labelRow = new HBox(4);
        labelRow.setAlignment(Pos.CENTER_LEFT);
        Label lbl = new Label(labelText);
        lbl.setStyle("-fx-font-weight:bold;-fx-font-size:13px;-fx-text-fill:#1a1a1a;");
        labelRow.getChildren().add(lbl);
        if (def.isRequired()) {
            Label req = new Label(" *");
            req.setStyle("-fx-text-fill:#c62828;-fx-font-size:13px;-fx-font-weight:bold;");
            labelRow.getChildren().add(req);
        }

        Control input = buildControl(def, labelText);
        String key = def.getDbKey() != null && !def.getDbKey().isBlank()
                ? def.getDbKey() : labelText;
        fieldControls.put(key, input);

        box.getChildren().addAll(sep, labelRow, input);
        return box;
    }

    private Control buildControl(FieldDefinition def, String labelText) {
        FieldDefinition.FieldType type =
                def.getType() != null ? def.getType() : FieldDefinition.FieldType.TEXT;
        return switch (type) {
            case NUMBER -> {
                TextField tf = new TextField();
                tf.setPromptText("Number…");
                tf.setPrefWidth(160);
                tf.textProperty().addListener((o, ov, nv) -> {
                    if (!nv.matches("-?\\d*\\.?\\d*")) tf.setText(ov);
                });
                yield tf;
            }
            case YEAR -> {
                TextField tf = new TextField();
                tf.setPromptText("YYYY");
                tf.setPrefWidth(90);
                tf.setMaxWidth(100);
                tf.textProperty().addListener((o, ov, nv) -> {
                    if (!nv.matches("\\d{0,4}")) tf.setText(ov);
                });
                yield tf;
            }
            case DATE -> {
                DatePicker dp = new DatePicker();
                dp.setPromptText("Select date");
                dp.setPrefWidth(180);
                yield dp;
            }
            case CHECKBOX -> {
                CheckBox cb = new CheckBox();
                cb.setStyle("-fx-font-size:18px;");
                cb.setPrefHeight(32);
                cb.setMinHeight(32);
                yield cb;
            }
            case DROPDOWN -> {
                ComboBox<String> cb = new ComboBox<>();
                if (def.getDropdownOptions() != null)
                    cb.getItems().addAll(def.getDropdownOptions());
                cb.setMaxWidth(Double.MAX_VALUE);
                yield cb;
            }
            default -> {
                TextField tf = new TextField();
                tf.setPromptText("Enter " + labelText.toLowerCase() + "…");
                tf.setMaxWidth(Double.MAX_VALUE);
                yield tf;
            }
        };
    }

    private boolean isWideField(FieldDefinition def) {
        if (def.getType() == null) return true;
        return def.getType() == FieldDefinition.FieldType.TEXT
            || def.getType() == FieldDefinition.FieldType.DROPDOWN;
    }

    // ── Batch management ──────────────────────────────────────────────────────

    private void openNewBatch() {
        String sessionId = state.getActiveSession() != null
                ? state.getActiveSession().getSessionId() : "0";
        try {
            String id = sessionService.getInstance()
                    .startBatch(sessionId, "batch_" + (batchCount + 1));
            currentBatchId = Integer.parseInt(id);
            state.setCurrentBatchId(id);
        } catch (Exception e) {
            showStatus("Could not open batch: " + e.getMessage(), true);
            currentBatchId = -1;
        }
        resetBatchState();
    }

    private void resetBatchState() {
        pathCameraA = pathCameraB = null;
        cameraACaptured = cameraBCaptured = false;
        setCameraStateBadge(cameraAStateLabel, false);
        setCameraStateBadge(cameraBStateLabel, false);
        setCaptureBtn(captureAButton, false, "Capture Camera A");
        setCaptureBtn(captureBButton, false, "Capture Camera B");
        discardAButton.setVisible(false);
        discardBButton.setVisible(false);
        saveEntryButton.setDisable(true);
    }

    // ── Camera A ──────────────────────────────────────────────────────────────

    @FXML
    private void onCaptureA() {
        if (pathCameraA != null) deleteFile(pathCameraA);

        CameraConfig camA = state.getActiveProfile().getCameraA();
        // Do NOT call startPreview() here — the existing worker is already running
        // and producing frames. Calling startPreview() stops that worker and starts
        // a new one, then grabStill() races against device open and times out.
        //
        // Additionally, grabStill() blocks the calling thread waiting for a frame.
        // If called on the JavaFX Application Thread it would deadlock because
        // Platform.runLater() posts to the FX queue which can't drain while blocked.
        // Run the entire capture on a background thread and update UI via runLater.

        captureAButton.setDisable(true);
        showStatus("Capturing Camera A…", false);

        Thread captureThread = new Thread(() -> {
            String path = doCapture(camA, "camera_a");
            javafx.application.Platform.runLater(() -> {
                captureAButton.setDisable(false);
                if (path != null) {
                    pathCameraA = path;
                    cameraACaptured = true;
                    camService.stopPreview(camA != null ? camA.getCameraName() : "");
                    freezePreview(cameraAImageView, path);
                    setCameraStateBadge(cameraAStateLabel, true);
                    setCaptureBtn(captureAButton, true, "Recapture Camera A");
                    discardAButton.setVisible(true);
                    checkBothCaptured();
                } else {
                    cameraACaptured = false;
                    setCameraStateBadge(cameraAStateLabel, false);
                }
            });
        }, "capture-a");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    @FXML
    private void onDiscardA() {
        discardCapture(pathCameraA, state.getActiveProfile().getCameraA(),
                cameraAImageView, cameraAOverlay,
                captureAButton, discardAButton, cameraAStateLabel,
                "Capture Camera A");
        pathCameraA = null;
        cameraACaptured = false;
        saveEntryButton.setDisable(true);
        showStatus("Camera A image discarded — live feed restored.", false);
    }

    // ── Camera B ──────────────────────────────────────────────────────────────

    @FXML
    private void onCaptureB() {
        if (pathCameraB != null) deleteFile(pathCameraB);

        CameraConfig camB = state.getActiveProfile().getCameraB();
        // Same fix as onCaptureA — no startPreview() before capture, run off FX thread.

        captureBButton.setDisable(true);
        showStatus("Capturing Camera B…", false);

        Thread captureThread = new Thread(() -> {
            String path = doCapture(camB, "camera_b");
            javafx.application.Platform.runLater(() -> {
                captureBButton.setDisable(false);
                if (path != null) {
                    pathCameraB = path;
                    cameraBCaptured = true;
                    camService.stopPreview(camB != null ? camB.getCameraName() : "");
                    freezePreview(cameraBImageView, path);
                    setCameraStateBadge(cameraBStateLabel, true);
                    setCaptureBtn(captureBButton, true, "Recapture Camera B");
                    discardBButton.setVisible(true);
                    checkBothCaptured();
                } else {
                    cameraBCaptured = false;
                    setCameraStateBadge(cameraBStateLabel, false);
                }
            });
        }, "capture-b");
        captureThread.setDaemon(true);
        captureThread.start();
    }

    @FXML
    private void onDiscardB() {
        discardCapture(pathCameraB, state.getActiveProfile().getCameraB(),
                cameraBImageView, cameraBOverlay,
                captureBButton, discardBButton, cameraBStateLabel,
                "Capture Camera B");
        pathCameraB = null;
        cameraBCaptured = false;
        saveEntryButton.setDisable(true);
        showStatus("Camera B image discarded — live feed restored.", false);
    }

    // ── Shared capture helpers ────────────────────────────────────────────────

    private String doCapture(CameraConfig config, String slotName) {
        if (config == null || config.getCameraName() == null) {
            showStatus("Camera not configured for: " + slotName, true);
            return null;
        }
        if (currentBatchId < 0) { showStatus("No active batch.", true); return null; }
        String sessionId = state.getActiveSession() != null
                ? state.getActiveSession().getSessionId() : "0";
        String path = camService.captureStill(
                config.getCameraName(), slotName,
                state.getActiveProfile().getOutputFolder(),
                sessionId, String.valueOf(currentBatchId));
        if (path == null) showStatus("Capture failed for " + slotName + ". Check camera.", true);
        return path;
    }

    private void discardCapture(String filePath, CameraConfig config,
                                 ImageView view, Label overlay,
                                 Button captureBtn, Button discardBtn,
                                 Label stateLabel, String captureBtnText) {
        deleteFile(filePath);
        view.setImage(null);
        if (config != null && config.getCameraName() != null) {
            // Use startPreviewAndWait so the camera is streaming before returning —
            // the operator can immediately click Capture without a grabStill timeout.
            Thread restoreThread = new Thread(() -> {
                camService.startPreviewAndWait(config.getCameraName(), view, overlay);
            }, "restore-preview");
            restoreThread.setDaemon(true);
            restoreThread.start();
        }
        setCameraStateBadge(stateLabel, false);
        setCaptureBtn(captureBtn, false, captureBtnText);
        discardBtn.setVisible(false);
    }

    private void deleteFile(String path) {
        if (path == null) return;
        try {
            if (Files.deleteIfExists(Paths.get(path)))
                System.out.println("[sessionController] Deleted: " + path);
        } catch (IOException e) {
            System.err.println("[sessionController] Delete failed: " + path + " — " + e.getMessage());
        }
    }

    private void freezePreview(ImageView view, String filePath) {
        try {
            File f = new File(filePath);
            if (f.exists()) view.setImage(new Image(f.toURI().toString()));
        } catch (Exception e) {
            System.err.println("[sessionController] freeze failed: " + e.getMessage());
        }
    }

    private void checkBothCaptured() {
        if (cameraACaptured && cameraBCaptured) {
            saveEntryButton.setDisable(false);
            showStatus("Both cameras captured — fill fields and save.", false);
        } else if (cameraACaptured) {
            showStatus("Camera A captured. Waiting for Camera B.", false);
        } else {
            showStatus("Camera B captured. Waiting for Camera A.", false);
        }
    }

    // ── Save entry ────────────────────────────────────────────────────────────

    @FXML
    private void onSaveEntry() {
        CaptureProfile profile = state.getActiveProfile();

        for (FieldDefinition def : profile.getFields()) {
            if (def.isRequired()) {
                String key = def.getDbKey() != null ? def.getDbKey() : def.getLabel();
                if (getControlValue(fieldControls.get(key), def).isEmpty()) {
                    showStatus("Required field missing: " + resolveLabel(def), true);
                    return;
                }
            }
        }

        Map<String, Object> entryData = new LinkedHashMap<>();
        for (FieldDefinition def : profile.getFields()) {
            String key = def.getDbKey() != null ? def.getDbKey() : def.getLabel();
            String val = getControlValue(fieldControls.get(key), def);
            if (!val.isEmpty()) entryData.put(key, val);
        }

        try {
            DatabaseService db = DatabaseService.getInstance();
            int entryId = db.insertEntry(currentBatchId, entryData);
            if (pathCameraA != null) db.insertImage(currentBatchId, entryId, "camera_a", pathCameraA);
            if (pathCameraB != null) db.insertImage(currentBatchId, entryId, "camera_b", pathCameraB);

            batchCount++;
            batchCountLabel.setText("Batches captured: " + batchCount);
            showStatus("Entry " + batchCount + " saved.", false);

            CameraConfig camA = profile.getCameraA();
            CameraConfig camB = profile.getCameraB();
            if (camA != null && camA.getCameraName() != null)
                camService.startPreview(camA.getCameraName(), cameraAImageView, cameraAOverlay);
            if (camB != null && camB.getCameraName() != null)
                camService.startPreview(camB.getCameraName(), cameraBImageView, cameraBOverlay);

            clearFields();
            openNewBatch();

        } catch (SQLException e) {
            showStatus("DB error: " + e.getMessage(), true);
            e.printStackTrace();
        }
    }

    // ── End session — navigate to summary view ────────────────────────────────

    @FXML
    private void onEndSession() {
        // Discard any unsaved captures
        deleteFile(pathCameraA);
        deleteFile(pathCameraB);

        camService.stopAll();

        // Store session id for the summary view to query
        if (state.getActiveSession() != null) {
            state.setCompletedSessionId(state.getActiveSession().getSessionId());
        }
        state.setActiveSession(null);
        state.setCurrentBatchId(null);

        // Navigate to end-of-session summary — keep DB connection open so
        // sessionEndController can query counts and ExportService can read data.
        Stage stage = (Stage) saveEntryButton.getScene().getWindow();
        viewManager.setScene(stage, "session-end-view.fxml", "Session Complete");
    }

    // ── UI helpers ────────────────────────────────────────────────────────────

    private void setCameraLabel(Label lbl, CameraConfig config, String fallback) {
        if (lbl == null) return;
        String s = config != null ? config.getSlotName() : null;
        lbl.setText(s != null && !s.isBlank() ? s : fallback);
    }

    private void setCameraStateBadge(Label label, boolean captured) {
        if (label == null) return;
        if (captured) {
            label.setText("Captured ✓");
            label.setStyle("-fx-font-size:11px;-fx-text-fill:#2e7d32;"
                    + "-fx-border-color:#a5d6a7;-fx-background-color:#e8f5e9;"
                    + "-fx-border-radius:10;-fx-background-radius:10;-fx-padding:2 10 2 10;");
        } else {
            label.setText("Not captured");
            label.setStyle("-fx-font-size:11px;-fx-text-fill:#9e9e9e;"
                    + "-fx-border-color:#e0e0e0;-fx-background-color:transparent;"
                    + "-fx-border-radius:10;-fx-background-radius:10;-fx-padding:2 10 2 10;");
        }
    }

    private void setCaptureBtn(Button btn, boolean captured, String text) {
        if (btn == null) return;
        btn.setText(text);
        btn.setStyle(captured ? BTN_GREEN : BTN_RED);
    }

    private void showStatus(String msg, boolean error) {
        if (statusLabel == null) return;
        statusLabel.setText((error ? "⚠ " : "✓ ") + msg);
        statusLabel.setStyle("-fx-text-fill:" + (error ? "#d32f2f" : "#2e7d32") + ";");
    }

    private void clearFields() {
        for (Control ctrl : fieldControls.values()) {
            if (ctrl instanceof TextField tf)        tf.clear();
            else if (ctrl instanceof DatePicker dp)  dp.setValue(null);
            else if (ctrl instanceof CheckBox cb)    cb.setSelected(false);
            else if (ctrl instanceof ComboBox<?> cb) cb.getSelectionModel().clearSelection();
        }
    }

    private String getControlValue(Control ctrl, FieldDefinition def) {
        if (ctrl == null)                   return "";
        if (ctrl instanceof TextField tf)   return tf.getText().trim();
        if (ctrl instanceof DatePicker dp)  return dp.getValue() != null ? dp.getValue().toString() : "";
        if (ctrl instanceof CheckBox cb)    return String.valueOf(cb.isSelected());
        if (ctrl instanceof ComboBox<?> cb) { Object v = cb.getValue(); return v != null ? v.toString() : ""; }
        return "";
    }

    private String resolveLabel(FieldDefinition def) {
        String l = def.getLabel();
        if (l != null && !l.isBlank()) return l;
        String k = def.getDbKey();
        return k != null && !k.isBlank() ? k : "field";
    }
}
