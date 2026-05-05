package com.comicdatacapture.comicdatacapture.service;

import com.comicdatacapture.comicdatacapture.model.CaptureProfile;
import com.google.gson.Gson;
import com.google.gson.GsonBuilder;

import java.io.IOException;
import java.io.Reader;
import java.io.Writer;
import java.nio.file.*;
import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Saves and loads CaptureProfile objects as JSON files in a local profiles directory.
 *
 * Storage: <user.home>/ComicDataCapture/profiles/<profileName>.json
 *
 * Deployments can ship with pre-built profiles in this folder (e.g. "Comic Intake.json").
 * Operators can save and reload profiles at runtime without reconfiguring each launch.
 */
public class ProfileService {

    private static final ProfileService INSTANCE = new ProfileService();
    public static ProfileService getInstance() { return INSTANCE; }

    private static final String APP_DIR      = "ComicDataCapture";
    private static final String PROFILES_DIR = "profiles";

    private final Gson gson = new GsonBuilder().setPrettyPrinting().create();
    private final Path profilesPath;

    private ProfileService() {
        profilesPath = Paths.get(System.getProperty("user.home"), APP_DIR, PROFILES_DIR);
        try {
            Files.createDirectories(profilesPath);
        } catch (IOException e) {
            System.err.println("[ProfileService] Could not create profiles directory: " + e.getMessage());
        }
    }

    // ── Save ──────────────────────────────────────────────────────────────────

    /**
     * Saves a profile to disk. Filename = profileName + ".json".
     * Creates or overwrites if a profile with the same name already exists.
     */
    public void save(CaptureProfile profile) throws IOException {
        String name = profile.getProfileName();
        if (name == null || name.isBlank()) {
            throw new IllegalArgumentException("Profile must have a name before saving.");
        }
        Path file = profilesPath.resolve(name + ".json");
        try (Writer w = Files.newBufferedWriter(file)) {
            gson.toJson(profile, w);
        }
        System.out.println("[ProfileService] Saved: " + file);
    }

    // ── Load ──────────────────────────────────────────────────────────────────

    /**
     * Loads a profile by name (without the .json extension).
     */
    public CaptureProfile load(String name) throws IOException {
        Path file = profilesPath.resolve(name + ".json");
        try (Reader r = Files.newBufferedReader(file)) {
            CaptureProfile profile = gson.fromJson(r, CaptureProfile.class);
            System.out.println("[ProfileService] Loaded: " + name);
            return profile;
        }
    }

    // ── List ──────────────────────────────────────────────────────────────────

    /**
     * Returns all saved profile names (without .json), sorted alphabetically.
     */
    public List<String> listProfileNames() {
        try (Stream<Path> files = Files.list(profilesPath)) {
            return files
                    .filter(p -> p.toString().endsWith(".json"))
                    .map(p -> p.getFileName().toString().replace(".json", ""))
                    .sorted()
                    .collect(Collectors.toList());
        } catch (IOException e) {
            System.err.println("[ProfileService] Could not list profiles: " + e.getMessage());
            return new ArrayList<>();
        }
    }

    // ── Delete ────────────────────────────────────────────────────────────────

    /** Deletes a saved profile. Returns true if deleted, false if not found. */
    public boolean delete(String name) {
        try {
            return Files.deleteIfExists(profilesPath.resolve(name + ".json"));
        } catch (IOException e) {
            System.err.println("[ProfileService] Could not delete '" + name + "': " + e.getMessage());
            return false;
        }
    }

    public Path getProfilesPath() { return profilesPath; }
}
