package org.fxsql.driverload;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.google.inject.Singleton;
import javafx.concurrent.Task;
import org.fxsql.driverload.model.DriverReference;
import org.fxsql.events.DriverDownloadEvent;
import org.fxsql.events.EventBus;
import org.fxsql.exceptions.DriverNotInstalledException;
import org.fxsql.service.BackgroundJarDownloadService;

import org.fxsql.config.AppPaths;

import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.logging.Level;
import java.util.logging.Logger;


@Singleton
public class DriverDownloader {

    private static final Logger logger = Logger.getLogger(DriverDownloader.class.getName());
    private static final String DIRECTORY = AppPaths.getDir("META-DATA").getAbsolutePath();
    private static final String FILE_NAME = "driver_repository.json";
    private static final String BUNDLED_RESOURCE = "/META-DATA/driver_repository.json";
    private static final String JAR_DIRECTORY = AppPaths.getDir("dynamic-jars").getAbsolutePath();

    private final ObjectMapper mapper;
    private List<DriverReference> references = new ArrayList<>();

    public DriverDownloader() {
        this(new ObjectMapper());
    }

    public DriverDownloader(ObjectMapper mapper) {
        this.mapper = Objects.requireNonNull(mapper, "mapper");
        loadReferences(); // eager-load; you can remove if you prefer lazy
        System.out.println("Driver downloader initialized, Total references loaded: " + references.size());
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
            seedFromClasspath(referenceFile);
        }
        if (!referenceFile.exists()) {
            this.references = new ArrayList<>();
            return;
        }

        try {
            List<DriverReference> loaded = mapper.readValue(referenceFile, new TypeReference<List<DriverReference>>() {
            });
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
        return references.stream().filter(r -> jarFileName.equalsIgnoreCase(r.getJarFileName())).findFirst();
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


    public void reloadReferences() {
        //This function checks if the drivers are loaded or not
    }

    private void seedFromClasspath(File targetFile) {
        try (InputStream in = getClass().getResourceAsStream(BUNDLED_RESOURCE)) {
            if (in != null) {
                Files.copy(in, targetFile.toPath(), StandardCopyOption.REPLACE_EXISTING);
                logger.info("Seeded driver repository from bundled resource to: " + targetFile.getAbsolutePath());
            }
        } catch (IOException e) {
            logger.log(Level.WARNING, "Failed to seed driver repository from classpath", e);
        }
    }

    private void downloadJDBCDriver(String driverName, String downloadUrl) throws IOException {
        Task<Void> task = new Task<Void>() {
            private final Logger logger = Logger.getLogger("Background-download-thread");

            @Override
            protected Void call() throws Exception {
                logger.info("Download started");
                BackgroundJarDownloadService.downloadJarFile(JAR_DIRECTORY, driverName, downloadUrl);
                logger.info("Download finished");
                return null;
            }
        };
        Thread t = new Thread(task);
        t.start();
    }

    public void downloadByReference(DriverReference ref) {

        assert ref != null;
        try {
            String driverName = ref.getDatabaseName().trim().toLowerCase().replace(" ", "-") + "-jdbc.jar";
            System.out.println("Starting download for driver" + driverName);
            downloadJDBCDriver(driverName, ref.getDownloadLink());

            //Dispatch an Event that notifies the app to reload the drivers
            EventBus.fireEvent(new DriverDownloadEvent("Driver downloaded"));
        } catch (IOException e) {
            throw new DriverNotInstalledException("Driver for " + ref.getDatabaseName() + " could not be installed");
        }

    }
}
