package com.comicdatacapture.comicdatacapture.controller;

import com.comicdatacapture.comicdatacapture.app.AppState;
import com.comicdatacapture.comicdatacapture.app.viewManager;
import com.comicdatacapture.comicdatacapture.model.CameraConfig;
import com.comicdatacapture.comicdatacapture.model.CaptureProfile;
import com.comicdatacapture.comicdatacapture.model.FieldDefinition;
import com.comicdatacapture.comicdatacapture.service.ProfileService;
import com.comicdatacapture.comicdatacapture.service.cameraService;
import javafx.fxml.FXML;
import javafx.geometry.Insets;
import javafx.scene.control.*;
import javafx.scene.image.ImageView;
import javafx.scene.layout.HBox;
import javafx.scene.layout.Priority;
import javafx.scene.layout.VBox;
import javafx.stage.DirectoryChooser;
import javafx.stage.Stage;

import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * Controller for config-view.fxml.
 *
 * Added profile persistence:
 *   - Profile name field at the top of the form
 *   - "Save Profile" button persists to disk via ProfileService
 *   - "Load Profile" dropdown lists all saved profiles; selecting one
 *     populates the entire form including field definitions
 *   - "Delete" removes the selected saved profile
 *
 * FieldDefinition.dbKey is auto-generated from the field label on Add;
 * operators never see or touch it.
 */
public class cameraConfigController {

    // ── Status badges ─────────────────────────────────────────────────────────
    @FXML private Label cameraAStatusLabel;
    @FXML private Label cameraBStatusLabel;
    @FXML private Label outputFolderStatusLabel;

    // ── Profile management ────────────────────────────────────────────────────
    @FXML private TextField profileNameField;
    @FXML private ComboBox<String> savedProfilesComboBox;

    // ── Camera preview ────────────────────────────────────────────────────────
    @FXML private ImageView cameraAImageView;
    @FXML private ImageView cameraBImageView;
    @FXML private Label cameraAOverlayLabel;
    @FXML private Label cameraBOverlayLabel;

    // ── Camera / resolution selectors ─────────────────────────────────────────
    @FXML private ComboBox<String> cameraAComboBox;
    @FXML private ComboBox<String> cameraBComboBox;
    @FXML private ComboBox<String> resolutionComboBox;

    // ── Output folder ─────────────────────────────────────────────────────────
    @FXML private TextField outputFolderField;

    // ── Field builder UI ──────────────────────────────────────────────────────
    @FXML private TextField newFieldLabelInput;
    @FXML private ComboBox<String> newFieldTypeComboBox;
    @FXML private CheckBox newFieldRequiredCheckBox;
    @FXML private TextField newFieldDropdownOptionsInput;
    @FXML private VBox fieldListContainer;

    private final List<FieldDefinition> fieldDefs = new ArrayList<>();
    private final cameraService camService = cameraService.getInstance();
    private final ProfileService profileService = ProfileService.getInstance();

    // ── Init ──────────────────────────────────────────────────────────────────

    @FXML
    public void initialize() {
        cameraAComboBox.getItems().addAll("USB Camera 0", "USB Camera 1", "USB Camera 2");
        cameraBComboBox.getItems().addAll("USB Camera 0", "USB Camera 1", "USB Camera 2");
        resolutionComboBox.getItems().addAll("640x480", "1280x720", "1920x1080");
        resolutionComboBox.setValue("1280x720");

        newFieldTypeComboBox.getItems().addAll("Text", "Number", "Date", "Year", "Checkbox", "Dropdown");
        newFieldTypeComboBox.setValue("Text");

        newFieldTypeComboBox.valueProperty().addListener((obs, old, val) -> {
            boolean isDropdown = "Dropdown".equals(val);
            newFieldDropdownOptionsInput.setVisible(isDropdown);
            newFieldDropdownOptionsInput.setManaged(isDropdown);
        });
        newFieldDropdownOptionsInput.setVisible(false);
        newFieldDropdownOptionsInput.setManaged(false);

        // Populate saved profiles dropdown
        refreshSavedProfilesList();

        // Pre-populate from existing active profile if returning from session screen
        CaptureProfile profile = AppState.getInstance().getActiveProfile();
        populateFormFromProfile(profile);
    }

    // ── Profile management ────────────────────────────────────────────────────

    /** Refreshes the saved profiles combo box from disk. */
    private void refreshSavedProfilesList() {
        savedProfilesComboBox.getItems().setAll(profileService.listProfileNames());
    }

    /** Called when the operator selects a profile name from the saved list. */
    @FXML
    private void onLoadProfile() {
        String selected = savedProfilesComboBox.getValue();
        if (selected == null || selected.isBlank()) return;
        try {
            CaptureProfile profile = profileService.load(selected);
            populateFormFromProfile(profile);
            AppState.getInstance().setActiveProfile(profile);
            showAlert(Alert.AlertType.INFORMATION, "Profile '" + selected + "' loaded.");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Could not load profile: " + e.getMessage());
        }
    }

    /** Saves the current form state as a named profile to disk. */
    @FXML
    private void onSaveProfile() {
        String name = profileNameField.getText().trim();
        if (name.isBlank()) {
            showAlert(Alert.AlertType.WARNING, "Enter a profile name before saving.");
            return;
        }
        CaptureProfile profile = buildProfileFromForm();
        if (profile == null) return;
        profile.setProfileName(name);
        try {
            profileService.save(profile);
            refreshSavedProfilesList();
            savedProfilesComboBox.setValue(name);
            showAlert(Alert.AlertType.INFORMATION, "Profile '" + name + "' saved.");
        } catch (IOException e) {
            showAlert(Alert.AlertType.ERROR, "Could not save profile: " + e.getMessage());
        }
    }

    /** Deletes the currently selected saved profile from disk. */
    @FXML
    private void onDeleteProfile() {
        String selected = savedProfilesComboBox.getValue();
        if (selected == null || selected.isBlank()) return;
        boolean deleted = profileService.delete(selected);
        if (deleted) {
            refreshSavedProfilesList();
            savedProfilesComboBox.setValue(null);
            showAlert(Alert.AlertType.INFORMATION, "Profile '" + selected + "' deleted.");
        } else {
            showAlert(Alert.AlertType.ERROR, "Could not delete profile '" + selected + "'.");
        }
    }

    /** Fills the entire form from a CaptureProfile object. */
    private void populateFormFromProfile(CaptureProfile profile) {
        if (profile == null) return;
        if (profile.getProfileName() != null) profileNameField.setText(profile.getProfileName());
        if (profile.getOutputFolder() != null) outputFolderField.setText(profile.getOutputFolder());
        if (profile.getCameraA() != null) cameraAComboBox.setValue(profile.getCameraA().getCameraName());
        if (profile.getCameraB() != null) cameraBComboBox.setValue(profile.getCameraB().getCameraName());
        if (profile.getResolution() != null) resolutionComboBox.setValue(profile.getResolution());

        fieldDefs.clear();
        fieldListContainer.getChildren().clear();
        if (profile.getFields() != null) {
            fieldDefs.addAll(profile.getFields());
            fieldDefs.forEach(this::renderFieldRow);
        }
    }

    // ── Camera preview ────────────────────────────────────────────────────────

    @FXML
    private void onStartPreview() {
        String camA = cameraAComboBox.getValue();
        String camB = cameraBComboBox.getValue();
        if (camA != null) camService.startPreview(camA, cameraAImageView, cameraAOverlayLabel);
        if (camB != null) camService.startPreview(camB, cameraBImageView, cameraBOverlayLabel);
        updateStatusLabels();
    }

    @FXML
    private void onStopPreview() {
        camService.stopAll();
        if (cameraAOverlayLabel != null) cameraAOverlayLabel.setVisible(true);
        if (cameraBOverlayLabel != null) cameraBOverlayLabel.setVisible(true);
    }

    // ── Output folder ─────────────────────────────────────────────────────────

    @FXML
    private void onBrowseOutputFolder() {
        DirectoryChooser chooser = new DirectoryChooser();
        chooser.setTitle("Select Output Folder");
        File dir = chooser.showDialog(outputFolderField.getScene().getWindow());
        if (dir != null) {
            outputFolderField.setText(dir.getAbsolutePath());
            outputFolderStatusLabel.setText("Output: " + dir.getName());
        }
    }

    // ── Field builder ─────────────────────────────────────────────────────────

    @FXML
    private void onAddField() {
        String label   = newFieldLabelInput.getText().trim();
        String typeStr = newFieldTypeComboBox.getValue();
        boolean required = newFieldRequiredCheckBox.isSelected();

        if (label.isEmpty() || typeStr == null) {
            showAlert(Alert.AlertType.WARNING, "Field label is required.");
            return;
        }

        // Guard against duplicate dbKeys within the same profile
        String dbKey = FieldDefinition.toDbKey(label);
        boolean duplicate = fieldDefs.stream().anyMatch(f -> f.getDbKey().equals(dbKey));
        if (duplicate) {
            showAlert(Alert.AlertType.WARNING,
                    "A field with key '" + dbKey + "' already exists.\n"
                    + "Change the label slightly to create a distinct key.");
            return;
        }

        FieldDefinition.FieldType type = switch (typeStr) {
            case "Number"   -> FieldDefinition.FieldType.NUMBER;
            case "Date"     -> FieldDefinition.FieldType.DATE;
            case "Year"     -> FieldDefinition.FieldType.YEAR;
            case "Checkbox" -> FieldDefinition.FieldType.CHECKBOX;
            case "Dropdown" -> FieldDefinition.FieldType.DROPDOWN;
            default         -> FieldDefinition.FieldType.TEXT;
        };

        // Use new constructor — dbKey auto-generated from label
        FieldDefinition def = new FieldDefinition(label, type, required);

        if (type == FieldDefinition.FieldType.DROPDOWN) {
            String rawOptions = newFieldDropdownOptionsInput.getText().trim();
            if (rawOptions.isEmpty()) {
                showAlert(Alert.AlertType.WARNING, "Enter comma-separated options for a Dropdown field.");
                return;
            }
            def.setDropdownOptions(Arrays.asList(rawOptions.split("\\s*,\\s*")));
        }

        fieldDefs.add(def);
        renderFieldRow(def);

        newFieldLabelInput.clear();
        newFieldTypeComboBox.setValue("Text");
        newFieldRequiredCheckBox.setSelected(false);
        newFieldDropdownOptionsInput.clear();
    }

    private void renderFieldRow(FieldDefinition def) {
        HBox row = new HBox(10);
        row.setPadding(new Insets(6, 8, 6, 8));
        row.setStyle("-fx-border-color: #e0e0e0; -fx-border-radius: 4; -fx-background-radius: 4; -fx-background-color: #fafafa;");
        row.setAlignment(javafx.geometry.Pos.CENTER_LEFT);

        Label nameLabel = new Label(def.getLabel());
        nameLabel.setStyle("-fx-font-weight: bold;");

        Label keyLabel = new Label("[" + def.getDbKey() + "]");
        keyLabel.setStyle("-fx-text-fill: #1565c0; -fx-font-size: 11px;");

        Label typeLabel = new Label(def.getType().name().toLowerCase());
        typeLabel.setStyle("-fx-text-fill: #757575; -fx-font-size: 11px;");

        Label reqLabel = new Label(def.isRequired() ? "required" : "optional");
        reqLabel.setStyle("-fx-text-fill: " + (def.isRequired() ? "#d32f2f" : "#9e9e9e") + "; -fx-font-size: 11px;");

        javafx.scene.layout.Region spacer = new javafx.scene.layout.Region();
        HBox.setHgrow(spacer, Priority.ALWAYS);

        Button removeBtn = new Button("✕");
        removeBtn.setStyle("-fx-background-color: transparent; -fx-text-fill: #9e9e9e; -fx-cursor: hand;");
        removeBtn.setOnAction(e -> {
            fieldDefs.remove(def);
            fieldListContainer.getChildren().remove(row);
        });

        row.getChildren().addAll(nameLabel, keyLabel, typeLabel, reqLabel, spacer, removeBtn);
        fieldListContainer.getChildren().add(row);
    }

    // ── Save config & navigate ────────────────────────────────────────────────

    @FXML
    private void onSaveConfiguration() {
        CaptureProfile profile = buildProfileFromForm();
        if (profile == null) return;

        AppState.getInstance().setActiveProfile(profile);
        camService.stopAll();

        Stage stage = (Stage) outputFolderField.getScene().getWindow();
        viewManager.setScene(stage, "intakeLanding-view.fxml", "Comic Intake Design");
    }

    @FXML
    private void onCancel() {
        camService.stopAll();
        Stage stage = (Stage) outputFolderField.getScene().getWindow();
        viewManager.setScene(stage, "intakeLanding-view.fxml", "Comic Intake Design");
    }

    // ── Helpers ───────────────────────────────────────────────────────────────

    /**
     * Builds a CaptureProfile from the current form state.
     * Returns null and shows a warning if validation fails.
     */
    private CaptureProfile buildProfileFromForm() {
        String camAVal     = cameraAComboBox.getValue();
        String camBVal     = cameraBComboBox.getValue();
        String resolution  = resolutionComboBox.getValue();
        String folder      = outputFolderField.getText().trim();

        if (camAVal == null || camBVal == null) {
            showAlert(Alert.AlertType.WARNING, "Please select devices for both Camera A and Camera B.");
            return null;
        }
        if (folder.isEmpty()) {
            showAlert(Alert.AlertType.WARNING, "Please select an output folder.");
            return null;
        }

        CameraConfig camA = new CameraConfig();
        camA.setSlotName("Camera A");
        camA.setCameraName(camAVal);
        camA.applyResolution(resolution);
        camA.setEnabled(true);

        CameraConfig camB = new CameraConfig();
        camB.setSlotName("Camera B");
        camB.setCameraName(camBVal);
        camB.applyResolution(resolution);
        camB.setEnabled(true);

        CaptureProfile profile = AppState.getInstance().getActiveProfile();
        profile.setProfileName(profileNameField.getText().trim());
        profile.setCameraA(camA);
        profile.setCameraB(camB);
        profile.setResolution(resolution);
        profile.setOutputFolder(folder);
        profile.setFields(new ArrayList<>(fieldDefs));
        return profile;
    }

    private void updateStatusLabels() {
        if (cameraAComboBox.getValue() != null)
            cameraAStatusLabel.setText("Camera A: " + cameraAComboBox.getValue());
        if (cameraBComboBox.getValue() != null)
            cameraBStatusLabel.setText("Camera B: " + cameraBComboBox.getValue());
    }

    private void showAlert(Alert.AlertType type, String msg) {
        Alert alert = new Alert(type);
        alert.setHeaderText(null);
        alert.setContentText(msg);
        alert.showAndWait();
    }
}
