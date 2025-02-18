package test.validation;

import impl.MergeHandler;
import impl.VersionManager;
import model.ConflictInfo;
import model.ConflictResolution;
import exceptions.MergeConflictException;
import exceptions.VersionException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.nio.file.Files;
import java.nio.file.StandardCopyOption;
import java.io.File;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import static org.junit.jupiter.api.Assertions.*;

class MergeHandlerValidationTest {
    private MergeHandler mergeHandler;
    private VersionManager versionManager;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        versionManager = new VersionManager(tempDir.toString());
        mergeHandler = new MergeHandler(versionManager);
        Files.createDirectories(tempDir.resolve(".vcs").resolve("objects"));
    }

    private String storeTestFile(String fileName, String content) throws Exception {
        File file = tempDir.resolve(fileName).toFile();
        Files.writeString(file.toPath(), content);
        String hash = utils.HashUtils.calculateFileHash(file);
        Files.copy(file.toPath(), tempDir.resolve(".vcs").resolve("objects").resolve(hash), StandardCopyOption.REPLACE_EXISTING);
        return hash;
    }

    private String createVersion(String message, Map<String, String> fileHashes) throws Exception {
        return versionManager.createVersion(message, fileHashes);
    }

    @Test
    void testMergeIdenticalFiles() throws Exception {
        String filePath = tempDir.resolve("test.txt").toString();
        String content = "same content";
        String hash = storeTestFile("test.txt", content);
        String v1 = createVersion("v1", Map.of(filePath, hash));
        String v2 = createVersion("v2", Map.of(filePath, hash));
        assertTrue(mergeHandler.merge(v1, v2));
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testMergeDifferentFilesConflict() throws Exception {
        String filePath = tempDir.resolve("test.txt").toString();
        String hash1 = storeTestFile("file1.txt", "foo");
        String hash2 = storeTestFile("file2.txt", "bar");
        String v1 = createVersion("v1", Map.of(filePath, hash1));
        String v2 = createVersion("v2", Map.of(filePath, hash2));
        assertFalse(mergeHandler.merge(v1, v2));
        List<ConflictInfo> conflicts = mergeHandler.getConflicts();
        assertFalse(conflicts.isEmpty());
        assertEquals(filePath, conflicts.getFirst().getFilePath());
    }

    @Test
    void testMergeEmptyFiles() throws Exception {
        String filePath = tempDir.resolve("empty.txt").toString();
        String hash1 = storeTestFile("empty1.txt", "");
        String hash2 = storeTestFile("empty2.txt", "");
        String v1 = createVersion("v1", Map.of(filePath, hash1));
        String v2 = createVersion("v2", Map.of(filePath, hash2));
        assertTrue(mergeHandler.merge(v1, v2));
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testMergeInvalidVersions() {
        assertThrows(VersionException.class, () -> mergeHandler.merge("invalid", "invalid"));
        assertThrows(VersionException.class, () -> mergeHandler.merge(null, "v1"));
        assertThrows(VersionException.class, () -> mergeHandler.merge("v1", null));
    }

    @Test
    void testResolveConflictKeepSource() throws Exception {
        String filePath = tempDir.resolve("test.txt").toString();
        String hash1 = storeTestFile("source.txt", "source");
        String hash2 = storeTestFile("target.txt", "target");
        String v1 = createVersion("v1", Map.of(filePath, hash1));
        String v2 = createVersion("v2", Map.of(filePath, hash2));
        mergeHandler.merge(v1, v2);
        ConflictResolution resolution = new ConflictResolution(filePath, new HashMap<>(), ConflictResolution.ResolutionStrategy.KEEP_SOURCE);
        mergeHandler.resolveConflict(filePath, resolution);
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testResolveConflictKeepTarget() throws Exception {
        String filePath = tempDir.resolve("test.txt").toString();
        String hash1 = storeTestFile("source.txt", "source");
        String hash2 = storeTestFile("target.txt", "target");
        String v1 = createVersion("v1", Map.of(filePath, hash1));
        String v2 = createVersion("v2", Map.of(filePath, hash2));
        mergeHandler.merge(v1, v2);
        ConflictResolution resolution = new ConflictResolution(filePath, new HashMap<>(), ConflictResolution.ResolutionStrategy.KEEP_TARGET);
        mergeHandler.resolveConflict(filePath, resolution);
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testResolveConflictCustom() throws Exception {
        String filePath = tempDir.resolve("test.txt").toString();
        String hash1 = storeTestFile("file1.txt", "A\nB\nC");
        String hash2 = storeTestFile("file2.txt", "X\nB\nC");
        String v1 = createVersion("v1", Map.of(filePath, hash1));
        String v2 = createVersion("v2", Map.of(filePath, hash2));
        mergeHandler.merge(v1, v2);
        Map<Integer, String> custom = new HashMap<>();
        custom.put(0, "CUSTOM");
        ConflictResolution resolution = new ConflictResolution(filePath, custom, ConflictResolution.ResolutionStrategy.CUSTOM);
        mergeHandler.resolveConflict(filePath, resolution);
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testResolveNonexistentConflict() {
        String filePath = tempDir.resolve("nonexistent.txt").toString();
        ConflictResolution resolution = new ConflictResolution(filePath, new HashMap<>(), ConflictResolution.ResolutionStrategy.KEEP_SOURCE);
        assertThrows(MergeConflictException.class, () -> mergeHandler.resolveConflict(filePath, resolution));
    }

    @Test
    void testMultipleFileConflicts() throws Exception {
        String filePath1 = tempDir.resolve("file1.txt").toString();
        String filePath2 = tempDir.resolve("file2.txt").toString();
        String hashA1 = storeTestFile("A1.txt", "ALPHA");
        String hashA2 = storeTestFile("A2.txt", "BRAVO");
        String hashB1 = storeTestFile("B1.txt", "CHARLIE");
        String hashB2 = storeTestFile("B2.txt", "DELTA");
        String v1 = createVersion("v1", Map.of(filePath1, hashA1, filePath2, hashB1));
        String v2 = createVersion("v2", Map.of(filePath1, hashA2, filePath2, hashB2));
        assertFalse(mergeHandler.merge(v1, v2));
        List<ConflictInfo> conflicts = mergeHandler.getConflicts();
        assertEquals(2, conflicts.size());
    }
}