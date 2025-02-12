package test.impl;

import impl.*;
import model.*;
import exceptions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import utils.HashUtils;

import java.io.IOException;
import java.nio.file.Path;
import java.io.File;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MergeHandlerTest {
    private MergeHandler mergeHandler;
    private VersionManager versionManager;

    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException {
        versionManager = new VersionManager(tempDir.toString());
        mergeHandler = new MergeHandler(versionManager);
        Files.createDirectories(tempDir.resolve(".vcs").resolve("objects"));
    }

    private String storeFile(String name, String content) throws Exception {
        File file = tempDir.resolve(name).toFile();
        Files.writeString(file.toPath(), content);
        String hash = HashUtils.calculateFileHash(file);
        Files.copy(file.toPath(), tempDir.resolve(".vcs").resolve("objects").resolve(hash),
                StandardCopyOption.REPLACE_EXISTING);
        return hash;
    }

    @Test
    void testMergeWithoutConflicts() throws Exception {
        String filePath = tempDir.resolve("test.txt").toString();
        String baseVersion = createVersion("Base", Map.of(filePath,
                storeFile("test.txt", "base content\ncommon line")));
        String sourceVersion = createVersion("Source", Map.of(filePath,
                storeFile("test.txt", "modified content\ncommon line")));

        assertTrue(mergeHandler.merge(sourceVersion, baseVersion));
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testMergeWithConflicts() throws Exception {
        String filePath = tempDir.resolve("test.txt").toString();
        String baseVersion = createVersion("Base", Map.of(filePath,
                storeFile("test.txt", "base\ncommon\nend")));
        String sourceVersion = createVersion("Source", Map.of(filePath,
                storeFile("test.txt", "source\ncommon\nend")));

        assertFalse(mergeHandler.merge(sourceVersion, baseVersion));
        assertFalse(mergeHandler.getConflicts().isEmpty());
        assertEquals(1, mergeHandler.getConflicts().size());
    }

    @Test
    void testMergeWithInvalidVersions() {
        assertThrows(VersionException.class,
                () -> mergeHandler.merge("nonexistent1", "nonexistent2"));
    }

    @Test
    void testConflictResolutionKeepSource() throws Exception {
        setupConflictScenario();

        String filePath = tempDir.resolve("test.txt").toString();
        ConflictResolution resolution = new ConflictResolution(
                filePath,
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_SOURCE
        );

        mergeHandler.resolveConflict(filePath, resolution);
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testConflictResolutionKeepTarget() throws Exception {
        setupConflictScenario();

        String filePath = tempDir.resolve("test.txt").toString();
        ConflictResolution resolution = new ConflictResolution(
                filePath,
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_TARGET
        );

        mergeHandler.resolveConflict(filePath, resolution);
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testConflictResolutionCustom() throws Exception {
        setupConflictScenario();

        String filePath = tempDir.resolve("test.txt").toString();
        Map<Integer, String> customResolutions = new HashMap<>();
        customResolutions.put(0, "custom resolution");

        ConflictResolution resolution = new ConflictResolution(
                filePath,
                customResolutions,
                ConflictResolution.ResolutionStrategy.CUSTOM
        );

        mergeHandler.resolveConflict(filePath, resolution);
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testResolvingNonexistentConflict() {
        String filePath = tempDir.resolve("nonexistent.txt").toString();
        ConflictResolution resolution = new ConflictResolution(
                filePath,
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_SOURCE
        );

        assertThrows(MergeConflictException.class,
                () -> mergeHandler.resolveConflict(filePath, resolution));
    }

    @Test
    void testMultipleFileConflicts() throws Exception {
        String filePath1 = tempDir.resolve("file1.txt").toString();
        String filePath2 = tempDir.resolve("file2.txt").toString();

        Map<String, String> baseHashes = new HashMap<>();
        baseHashes.put(filePath1, storeFile("file1.txt", "base1"));
        baseHashes.put(filePath2, storeFile("file2.txt", "base2"));
        String baseVersion = createVersion("Base", baseHashes);

        Map<String, String> sourceHashes = new HashMap<>();
        sourceHashes.put(filePath1, storeFile("file1.txt", "source1"));
        sourceHashes.put(filePath2, storeFile("file2.txt", "source2"));
        String sourceVersion = createVersion("Source", sourceHashes);

        assertFalse(mergeHandler.merge(sourceVersion, baseVersion));
        assertEquals(2, mergeHandler.getConflicts().size());
    }

    private void setupConflictScenario() throws Exception {
        String filePath = tempDir.resolve("test.txt").toString();
        String baseVersion = createVersion("Base", Map.of(filePath,
                storeFile("test.txt", "base\ncommon")));
        String sourceVersion = createVersion("Source", Map.of(filePath,
                storeFile("test.txt", "source\ncommon")));
        mergeHandler.merge(sourceVersion, baseVersion);
    }

    @Test
    void testMergeWithEmptyFiles() throws Exception {
        String filePath = tempDir.resolve("test.txt").toString();
        String baseVersion = createVersion("Base", Map.of(filePath,
                storeFile("test.txt", "")));
        String sourceVersion = createVersion("Source", Map.of(filePath,
                storeFile("test.txt", "new content")));

        assertFalse(mergeHandler.merge(sourceVersion, baseVersion));
        assertFalse(mergeHandler.getConflicts().isEmpty());
    }

    private String createVersion(String message, Map<String, String> fileHashes) throws Exception {
        return versionManager.createVersion(message, fileHashes);
    }
}
