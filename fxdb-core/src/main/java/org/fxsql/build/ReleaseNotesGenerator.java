package org.fxsql.build;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Build-time utility that generates release-notes.json from git log.
 * Invoked by exec-maven-plugin during generate-resources phase.
 *
 * Usage: java org.fxsql.build.ReleaseNotesGenerator <outputDir>
 */
public class ReleaseNotesGenerator {

    private static final String FIELD_SEPARATOR = "<%>";

    public static void main(String[] args) throws Exception {
        if (args.length < 1) {
            System.err.println("Usage: ReleaseNotesGenerator <outputDir>");
            System.exit(1);
        }

        File outputDir = new File(args[0]);
        outputDir.mkdirs();
        File outputFile = new File(outputDir, "release-notes.json");

        List<Map<String, String>> notes = new ArrayList<>();

        ProcessBuilder pb = new ProcessBuilder(
                "git", "log",
                "--pretty=format:%h" + FIELD_SEPARATOR + "%s" + FIELD_SEPARATOR + "%ai"
        );
        pb.redirectErrorStream(true);

        Process process = pb.start();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(FIELD_SEPARATOR, 3);
                if (parts.length == 3) {
                    Map<String, String> entry = new LinkedHashMap<>();
                    entry.put("hash", parts[0].trim());
                    entry.put("subject", parts[1].trim());
                    entry.put("date", parts[2].trim());
                    notes.add(entry);
                }
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            System.err.println("git log exited with code " + exitCode);
            System.exit(1);
        }

        ObjectMapper mapper = new ObjectMapper();
        mapper.enable(SerializationFeature.INDENT_OUTPUT);
        mapper.writeValue(outputFile, notes);

        System.out.println("Generated " + notes.size() + " release notes to " + outputFile.getAbsolutePath());

        // Generate build-info.json with latest tag
        String latestTag = getLatestTag();
        Map<String, String> buildInfo = new LinkedHashMap<>();
        buildInfo.put("version", latestTag);
        File buildInfoFile = new File(outputDir, "build-info.json");
        mapper.writeValue(buildInfoFile, buildInfo);

        System.out.println("Build info: version=" + latestTag);
    }

    private static String getLatestTag() {
        try {
            ProcessBuilder pb = new ProcessBuilder("git", "describe", "--tags", "--abbrev=0");
            pb.redirectErrorStream(true);
            Process process = pb.start();
            try (BufferedReader reader = new BufferedReader(
                    new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
                String tag = reader.readLine();
                int exitCode = process.waitFor();
                if (exitCode == 0 && tag != null && !tag.isBlank()) {
                    return tag.trim();
                }
            }
        } catch (Exception e) {
            System.err.println("Could not determine latest tag: " + e.getMessage());
        }
        return "unknown";
    }
}
