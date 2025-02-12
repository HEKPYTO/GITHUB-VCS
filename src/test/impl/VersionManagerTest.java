package test.impl;

import impl.VersionManager;
import model.VersionInfo;
import exceptions.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class VersionManagerTest {
    private VersionManager versionManager;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() {
        versionManager = new VersionManager(tempDir.toString());
    }

    @Test
    void testCreateInitialVersion() throws Exception {
        // Test creating the first version
        Map<String, String> fileHashes = new HashMap<>();
        fileHashes.put("test.txt", "hash123");

        String versionId = versionManager.createVersion("Initial commit", fileHashes);
        assertNotNull(versionId);

        VersionInfo version = versionManager.getVersion(versionId);
        assertEquals("Initial commit", version.getMessage());
        assertEquals(fileHashes, version.getFileHashes());
        assertEquals(System.getProperty("user.name"), version.getAuthor());
    }

    @Test
    void testCreateEmptyVersion() throws Exception {
        // Edge case: create version with no files
        String versionId = versionManager.createVersion("Empty commit", new HashMap<>());
        VersionInfo version = versionManager.getVersion(versionId);
        assertTrue(version.getFileHashes().isEmpty());
    }

    @Test
    void testCreateVersionWithNullMessage() {
        Map<String, String> fileHashes = new HashMap<>();
        fileHashes.put("test.txt", "hash123");

        assertThrows(VersionException.class,
                () -> versionManager.createVersion(null, fileHashes));
    }

    @Test
    void testVersionHistory() throws Exception {
        // Test version history tracking
        Map<String, String> fileHashes = new HashMap<>();
        fileHashes.put("test.txt", "hash123");

        String version1 = versionManager.createVersion("First commit", fileHashes);
        fileHashes.put("test2.txt", "hash456");
        String version2 = versionManager.createVersion("Second commit", fileHashes);

        List<VersionInfo> history = versionManager.getVersionHistory();
        assertEquals(2, history.size());
        assertEquals(version1, history.get(0).getVersionId());
        assertEquals(version2, history.get(1).getVersionId());
    }

    @Test
    void testGetNonexistentVersion() {
        // Edge case: getting a version that doesn't exist
        assertNull(versionManager.getVersion("nonexistent-version"));
    }

    @Test
    void testVersionPersistence() throws Exception {
        // Test that versions persist after manager recreation
        Map<String, String> fileHashes = new HashMap<>();
        fileHashes.put("test.txt", "hash123");

        String versionId = versionManager.createVersion("Test commit", fileHashes);

        // Create new instance
        VersionManager newManager = new VersionManager(tempDir.toString());
        VersionInfo version = newManager.getVersion(versionId);
        assertNotNull(version);
        assertEquals("Test commit", version.getMessage());
    }
}