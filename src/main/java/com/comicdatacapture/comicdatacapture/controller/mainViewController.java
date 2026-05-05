package com.comicdatacapture.comicdatacapture.controller;

import com.comicdatacapture.comicdatacapture.app.AppState;
import com.comicdatacapture.comicdatacapture.app.viewManager;
import com.comicdatacapture.comicdatacapture.model.CameraConfig;
import com.comicdatacapture.comicdatacapture.model.Session;
import com.comicdatacapture.comicdatacapture.service.DatabaseService;
import com.comicdatacapture.comicdatacapture.service.cameraService;
import com.comicdatacapture.comicdatacapture.service.sessionService;
import javafx.fxml.FXML;
import javafx.scene.control.Alert;
import javafx.scene.control.Label;
import javafx.scene.image.ImageView;
import javafx.scene.layout.StackPane;
import javafx.stage.Stage;

import java.sql.SQLException;

public class mainViewController {

    @FXML private Label     cameraAStatusLabel;
    @FXML private Label     cameraBStatusLabel;
    @FXML private Label     outputFolderStatusLabel;
    @FXML private Label     profileStatusLabel;
    @FXML private Label     cameraADeviceLabel;
    @FXML private Label     cameraBDeviceLabel;
    @FXML private Label     cameraAOverlayLabel;
    @FXML private Label     cameraBOverlayLabel;
    @FXML private ImageView cameraAImageView;
    @FXML private ImageView cameraBImageView;
    @FXML private StackPane cameraAPreview;
    @FXML private StackPane cameraBPreview;

    private final cameraService camService = cameraService.getInstance();

    @FXML
    public void initialize() {
        AppState state = AppState.getInstance();
        CameraConfig camA = state.getCameraAConfig();
        CameraConfig camB = state.getCameraBConfig();

        setText(cameraAStatusLabel, camA != null && camA.getCameraName() != null
                ? "Camera A: " + camA.getCameraName() : "Camera A: Not Assigned");
        setText(cameraBStatusLabel, camB != null && camB.getCameraName() != null
                ? "Camera B: " + camB.getCameraName() : "Camera B: Not Assigned");

        String folder = state.getOutputFolder();
        setText(outputFolderStatusLabel, folder != null && !folder.isBlank()
                ? "Output: " + folder : "Output Folder: Not Set");

        String profile = state.getActiveProfile().getProfileName();
        setText(profileStatusLabel, profile != null && !profile.isBlank()
                ? "Profile: " + profile : "Profile: Default");

        setText(cameraADeviceLabel, camA != null && camA.getCameraName() != null
                ? "Device: " + camA.getCameraName() : "Device: —");
        setText(cameraBDeviceLabel, camB != null && camB.getCameraName() != null
                ? "Device: " + camB.getCameraName() : "Device: —");

        // No binding — fitWidth/fitHeight are set in FXML and never overridden.
        if (camA != null && camA.getCameraName() != null)
            camService.startPreview(camA.getCameraName(), cameraAImageView, cameraAOverlayLabel);
        if (camB != null && camB.getCameraName() != null)
            camService.startPreview(camB.getCameraName(), cameraBImageView, cameraBOverlayLabel);
    }

    @FXML
    private void handleStartSession() {
        AppState state = AppState.getInstance();
        if (!state.isConfigured()) {
            alert(Alert.AlertType.WARNING, "Not Ready",
                    "Please complete configuration before starting a session.");
            return;
        }
        if (state.getActiveProfile().getFields().isEmpty()) {
            alert(Alert.AlertType.WARNING, "No Fields Defined",
                    "Add at least one capture field in Configuration before starting a session.");
            return;
        }
        camService.stopAllAndWait();
        try {
            DatabaseService.getInstance().connect();
        } catch (SQLException e) {
            alert(Alert.AlertType.ERROR, "Database Connection Failed",
                    "Could not connect to PostgreSQL:\n" + e.getMessage());
            return;
        }
        Session session = sessionService.getInstance().startSession(
                "operator", state.getActiveProfile().getFields());
        state.setActiveSession(session);
        Stage stage = (Stage) cameraAStatusLabel.getScene().getWindow();
        viewManager.setScene(stage, "session-started-view.fxml", "Intake Session");
    }

    @FXML
    private void handleConfig() {
        camService.stopAllAndWait();
        Stage stage = (Stage) cameraAStatusLabel.getScene().getWindow();
        viewManager.setScene(stage, "config-view.fxml", "Configuration");
    }

    private void setText(Label label, String text) {
        if (label != null) label.setText(text);
    }

    private void alert(Alert.AlertType type, String header, String content) {
        Alert a = new Alert(type);
        a.setHeaderText(header);
        a.setContentText(content);
        a.showAndWait();
    }
}
