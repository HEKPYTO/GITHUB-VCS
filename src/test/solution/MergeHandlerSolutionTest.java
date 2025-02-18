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

class MergeHandlerSolutionTest {
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
        String content = "line1\nline2\nline3\nline4";
        String hash = storeTestFile("test.txt", content);
        String v1 = createVersion("v1", Map.of(filePath, hash));
        String v2 = createVersion("v2", Map.of(filePath, hash));
        assertTrue(mergeHandler.merge(v1, v2));
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testMergeDifferentFilesConflict() throws Exception {
        String filePath = tempDir.resolve("test.txt").toString();
        String hash1 = storeTestFile("file1.txt", "This is completely different content\nwith multiple lines\nthat has no similarity");
        String hash2 = storeTestFile("file2.txt", "Entirely new and different text\nwith unique content\nthat should trigger conflict");
        String v1 = createVersion("v1", Map.of(filePath, hash1));
        String v2 = createVersion("v2", Map.of(filePath, hash2));
        assertFalse(mergeHandler.merge(v1, v2));
        List<ConflictInfo> conflicts = mergeHandler.getConflicts();
        assertFalse(conflicts.isEmpty());
        assertEquals(filePath, conflicts.getFirst().getFilePath());
    }

    @Test
    void testMergeEmptyFiles() throws Exception {
        String filePath = tempDir.resolve("test.txt").toString();
        String hash1 = storeTestFile("empty1.txt", "\n\n\n");
        String hash2 = storeTestFile("empty2.txt", "\n\n\n");
        String v1 = createVersion("v1", Map.of(filePath, hash1));
        String v2 = createVersion("v2", Map.of(filePath, hash2));
        assertTrue(mergeHandler.merge(v1, v2));
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testMergeInvalidVersions() {
        assertThrows(VersionException.class, () -> mergeHandler.merge("v999", "v888"));
        assertThrows(VersionException.class, () -> mergeHandler.merge(null, "valid"));
        assertThrows(VersionException.class, () -> mergeHandler.merge("valid", null));
    }

    @Test
    void testResolveConflictKeepSource() throws Exception {
        String filePath = tempDir.resolve("test.txt").toString();
        String hash1 = storeTestFile("source.txt", "line1\nline2\nline3");
        String hash2 = storeTestFile("target.txt", "LINE-A\nLINE-B\nLINE-C");
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
        String hash1 = storeTestFile("source.txt", "original\ncontent\nhere");
        String hash2 = storeTestFile("target.txt", "modified\ncontent\nhere");
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
        String hash1 = storeTestFile("file1.txt", "first\nsecond\nthird");
        String hash2 = storeTestFile("file2.txt", "one\ntwo\nthree");
        String v1 = createVersion("v1", Map.of(filePath, hash1));
        String v2 = createVersion("v2", Map.of(filePath, hash2));
        mergeHandler.merge(v1, v2);
        Map<Integer, String> custom = new HashMap<>();
        custom.put(0, "resolved_line1");
        custom.put(1, "resolved_line2");
        custom.put(2, "resolved_line3");
        ConflictResolution resolution = new ConflictResolution(filePath, custom, ConflictResolution.ResolutionStrategy.CUSTOM);
        mergeHandler.resolveConflict(filePath, resolution);
        assertTrue(mergeHandler.getConflicts().isEmpty());
    }

    @Test
    void testResolveNonexistentConflict() {
        String filePath = tempDir.resolve("missing.txt").toString();
        ConflictResolution resolution = new ConflictResolution(filePath, new HashMap<>(), ConflictResolution.ResolutionStrategy.KEEP_SOURCE);
        assertThrows(MergeConflictException.class, () -> mergeHandler.resolveConflict(filePath, resolution));
    }

    @Test
    void testMultipleFileConflicts() throws Exception {
        String file1Path = tempDir.resolve("code.java").toString();
        String file2Path = tempDir.resolve("test.java").toString();
        String srcA = storeTestFile("srcA.txt", "Complex implementation with\nmultiple methods and\ncompletely different structure");
        String srcB = storeTestFile("srcB.txt", "Totally different implementation\nwith unique methods\nand another structure");
        String testA = storeTestFile("testA.txt", "First test suite with\nunique test cases\nand setup code");
        String testB = storeTestFile("testB.txt", "Different test suite with\ncompletely different tests\nand different setup");
        String v1 = createVersion("v1", Map.of(file1Path, srcA, file2Path, testA));
        String v2 = createVersion("v2", Map.of(file1Path, srcB, file2Path, testB));
        assertFalse(mergeHandler.merge(v1, v2));
        List<ConflictInfo> conflicts = mergeHandler.getConflicts();
        assertEquals(2, conflicts.size());
    }
}