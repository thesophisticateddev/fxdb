package org.fxsql.service;

import java.io.IOException;
import java.io.InputStream;
import java.net.URL;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

public class BackgroundJarDownloadService {


    public static boolean checkJarAlreadyExists(String destinationDir, String fileName){
        Path dynamicJarsPath = Paths.get(destinationDir);
        Path jarFilePath = dynamicJarsPath.resolve(fileName);

        //If jar file already exist then do not download it!
        if (Files.exists(jarFilePath)) {
            System.out.println("Jar file already exists");
            return true;
        }
        return false;
    }
    public static void downloadJarFile(String downloadPath,String fileName, String downloadUrl) throws
            IOException {
        // Define the destination path for the JAR file
        Path dynamicJarsPath = Paths.get(downloadPath);
        Path jarFilePath = dynamicJarsPath.resolve(fileName);

        // Create the dynamic-jars directory if it doesn't exist
        if (Files.notExists(dynamicJarsPath)) {
            Files.createDirectories(dynamicJarsPath);
        }
        //Check if driver already exists
        if (checkJarAlreadyExists(downloadPath, fileName)) {
            System.out.println("Jar file exists in directory");
            return;
        }

        // Download the JAR file
        try (InputStream in = new URL(downloadUrl).openStream()) {
            Files.copy(in, jarFilePath);
            System.out.println("Jar file downloaded successfully to " + jarFilePath);
        }
        catch (IOException e) {
            System.err.println("Failed to download the Jar file: " + e.getMessage());
            throw e;
        }
    }
}
