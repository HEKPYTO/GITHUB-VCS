package test;

import impl.*;
import model.*;
import exceptions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import utils.HashUtils;

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
    void setUp() throws FileOperationException {
        versionManager = new VersionManager(tempDir.toString());
        FileTracker fileTracker = new FileTracker(tempDir.toString());
        DiffGenerator diffGenerator = new DiffGenerator(versionManager, fileTracker);
        mergeHandler = new MergeHandler(versionManager, diffGenerator);
    }

    @Test
    void testMergeWithoutConflicts() throws Exception {
        // Test simple merge with no conflicts
        String baseHash = createAndStoreFile("test.txt", "base content\ncommon line");
        String baseVersion = createVersion("Base", Map.of("test.txt", baseHash));

        String sourceHash = createAndStoreFile("test.txt", "modified content\ncommon line");
        String sourceVersion = createVersion("Source", Map.of("test.txt", sourceHash));

        assertTrue(mergeHandler.merge(sourceVersion, baseVersion));
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testMergeWithConflicts() throws Exception {
        // Test merge with conflicting changes
        String baseHash = createAndStoreFile("test.txt", "base\ncommon\nend");
        String baseVersion = createVersion("Base", Map.of("test.txt", baseHash));

        String sourceHash = createAndStoreFile("test.txt", "source\ncommon\nend");
        String sourceVersion = createVersion("Source", Map.of("test.txt", sourceHash));

        assertFalse(mergeHandler.merge(sourceVersion, baseVersion));
        assertFalse(mergeHandler.getConflicts().isEmpty());
        assertEquals(1, mergeHandler.getConflicts().size());
    }

    @Test
    void testMergeWithInvalidVersions() {
        // Test merge with non-existent versions
        assertThrows(VersionException.class,
                () -> mergeHandler.merge("nonexistent1", "nonexistent2"));
    }

    @Test
    void testConflictResolutionKeepSource() throws Exception {
        // Test resolving conflict by keeping source version
        setupConflictScenario();

        ConflictResolution resolution = new ConflictResolution(
                "test.txt",
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_SOURCE
        );

        mergeHandler.resolveConflict("test.txt", resolution);
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testConflictResolutionKeepTarget() throws Exception {
        // Test resolving conflict by keeping target version
        setupConflictScenario();

        ConflictResolution resolution = new ConflictResolution(
                "test.txt",
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_TARGET
        );

        mergeHandler.resolveConflict("test.txt", resolution);
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testConflictResolutionCustom() throws Exception {
        // Test resolving conflict with custom content
        setupConflictScenario();

        Map<Integer, String> customResolutions = new HashMap<>();
        customResolutions.put(0, "custom resolution");

        ConflictResolution resolution = new ConflictResolution(
                "test.txt",
                customResolutions,
                ConflictResolution.ResolutionStrategy.CUSTOM
        );

        mergeHandler.resolveConflict("test.txt", resolution);
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testResolvingNonexistentConflict() {
        // Test attempting to resolve a non-existent conflict
        ConflictResolution resolution = new ConflictResolution(
                "nonexistent.txt",
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_SOURCE
        );

        assertThrows(MergeConflictException.class,
                () -> mergeHandler.resolveConflict("nonexistent.txt", resolution));
    }

    @Test
    void testMultipleFileConflicts() throws Exception {
        // Test handling conflicts in multiple files
        Map<String, String> baseHashes = new HashMap<>();
        baseHashes.put("file1.txt", createAndStoreFile("file1.txt", "base1"));
        baseHashes.put("file2.txt", createAndStoreFile("file2.txt", "base2"));
        String baseVersion = createVersion("Base", baseHashes);

        Map<String, String> sourceHashes = new HashMap<>();
        sourceHashes.put("file1.txt", createAndStoreFile("file1.txt", "source1"));
        sourceHashes.put("file2.txt", createAndStoreFile("file2.txt", "source2"));
        String sourceVersion = createVersion("Source", sourceHashes);

        assertFalse(mergeHandler.merge(sourceVersion, baseVersion));
        assertEquals(2, mergeHandler.getConflicts().size());
    }

    private void setupConflictScenario() throws Exception {
        String baseHash = createAndStoreFile("test.txt", "base\ncommon");
        String baseVersion = createVersion("Base", Map.of("test.txt", baseHash));

        String sourceHash = createAndStoreFile("test.txt", "source\ncommon");
        String sourceVersion = createVersion("Source", Map.of("test.txt", sourceHash));

        mergeHandler.merge(sourceVersion, baseVersion);
    }

    private String createAndStoreFile(String name, String content) throws Exception {
        File file = tempDir.resolve(name).toFile();
        Files.writeString(file.toPath(), content);
        String hash = HashUtils.calculateFileHash(file);

        // Ensure objects directory exists and copy file
        Path objectsDir = tempDir.resolve(".vcs").resolve("objects");
        Files.createDirectories(objectsDir);
        Files.copy(file.toPath(), objectsDir.resolve(hash), StandardCopyOption.REPLACE_EXISTING);

        return hash;
    }

    private String createVersion(String message, Map<String, String> fileHashes) throws Exception {
        return versionManager.createVersion(message, fileHashes);
    }
}

