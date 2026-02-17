package org.fxsql.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.fxsql.model.ReleaseNote;

import java.io.InputStream;
import java.util.Collections;
import java.util.List;
import java.util.logging.Level;
import java.util.logging.Logger;

public class ReleaseNotesLoader {

    private static final Logger logger = Logger.getLogger(ReleaseNotesLoader.class.getName());
    private static final String RELEASE_NOTES_FILE = "release-notes.json";

    public static List<ReleaseNote> load() {
        try (InputStream is = ReleaseNotesLoader.class.getClassLoader().getResourceAsStream(RELEASE_NOTES_FILE)) {
            if (is == null) {
                logger.warning("Release notes file not found on classpath: " + RELEASE_NOTES_FILE);
                return Collections.emptyList();
            }
            ObjectMapper mapper = new ObjectMapper();
            return mapper.readValue(is, new TypeReference<List<ReleaseNote>>() {});
        } catch (Exception e) {
            logger.log(Level.WARNING, "Failed to load release notes", e);
            return Collections.emptyList();
        }
    }
}
