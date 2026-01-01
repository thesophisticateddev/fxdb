package org.fxsql.driverload;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;

import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.*;

@Singleton
public class DriverDownloader {

    private static final String DIRECTORY = "META-DATA";
    private static final String FILE_NAME = "driver_repository.json";

    private final ObjectMapper mapper;
    private List<DriverReference> references = new ArrayList<>();

    public DriverDownloader() {
        this(new ObjectMapper());
    }

    public DriverDownloader(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        loadReferences(); // eager-load; you can remove if you prefer lazy
    }

    /**
     * Simple DTO representing one driver entry in driver_repository.json
     *
     * JSON example:
     * [
     *   { "downloadLink": "https://...", "jarFileName": "sqlite-jdbc.jar" }
     * ]
     */
    public static class DriverReference {
        private String downloadLink;
        private String jarFileName;
        private String driverClass;

        public String getDriverClass() {
            return driverClass;
        }

        public void setDriverClass(String driverClass) {
            this.driverClass = driverClass;
        }

        public DriverReference() {
        }

        public DriverReference(String downloadLink, String jarFileName) {
            this.downloadLink = downloadLink;
            this.jarFileName = jarFileName;
        }

        public String getDownloadLink() {
            return downloadLink;
        }

        public void setDownloadLink(String downloadLink) {
            this.downloadLink = downloadLink;
        }

        public String getJarFileName() {
            return jarFileName;
        }

        public void setJarFileName(String jarFileName) {
            this.jarFileName = jarFileName;
        }

        @Override
        public String toString() {
            return "DriverReference{jarFileName='" + jarFileName + "', downloadLink='" + downloadLink + "'}";
        }
    }

    private File getFile() {
        return new File(DIRECTORY, FILE_NAME);
    }

    private void ensureDirectoryExists() {
        try {
            Files.createDirectories(Path.of(DIRECTORY));
        } catch (IOException e) {
            throw new RuntimeException("Failed to create directory: " + DIRECTORY, e);
        }
    }

    /**
     * Loads references from disk into memory.
     * If file does not exist, initializes with an empty list (no crash).
     */
    public final void loadReferences() {
        ensureDirectoryExists();

        File referenceFile = getFile();
        if (!referenceFile.exists()) {
            this.references = new ArrayList<>();
            return;
        }

        try {
            List<DriverReference> loaded = mapper.readValue(
                    referenceFile,
                    new TypeReference<List<DriverReference>>() {}
            );
            this.references = (loaded != null) ? new ArrayList<>(loaded) : new ArrayList<>();
        } catch (IOException e) {
            throw new RuntimeException("Failed to read driver repository file: " + referenceFile.getAbsolutePath(), e);
        }
    }

    /**
     * Persists current references back to disk.
     */
    public void saveReferences() {
        ensureDirectoryExists();
        File referenceFile = getFile();

        try {
            mapper.writerWithDefaultPrettyPrinter().writeValue(referenceFile, references);
        } catch (IOException e) {
            throw new RuntimeException("Failed to write driver repository file: " + referenceFile.getAbsolutePath(), e);
        }
    }

    public List<DriverReference> getReferences() {
        return Collections.unmodifiableList(references);
    }

    public Optional<DriverReference> findByJarFileName(String jarFileName) {
        if (jarFileName == null) return Optional.empty();
        return references.stream()
                .filter(r -> jarFileName.equalsIgnoreCase(r.getJarFileName()))
                .findFirst();
    }

    /**
     * Add or update a reference by jarFileName (upsert).
     */
    public void upsertReference(DriverReference ref) {
        Objects.requireNonNull(ref, "ref");
        if (ref.getJarFileName() == null || ref.getJarFileName().isBlank()) {
            throw new IllegalArgumentException("jarFileName must not be null/blank");
        }
        if (ref.getDownloadLink() == null || ref.getDownloadLink().isBlank()) {
            throw new IllegalArgumentException("downloadLink must not be null/blank");
        }

        // Replace existing if present
        for (int i = 0; i < references.size(); i++) {
            if (ref.getJarFileName().equalsIgnoreCase(references.get(i).getJarFileName())) {
                references.set(i, ref);
                return;
            }
        }

        // Otherwise add
        references.add(ref);
    }

    public boolean removeByJarFileName(String jarFileName) {
        if (jarFileName == null) return false;
        return references.removeIf(r -> jarFileName.equalsIgnoreCase(r.getJarFileName()));
    }


    public void reloadReferences(){
        //This function checks if the drivers are loaded or not
    }

}
