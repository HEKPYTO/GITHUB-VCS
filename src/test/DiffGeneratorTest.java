package test;

import impl.DiffGenerator;
import impl.FileTracker;
import impl.VersionManager;
import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import utils.HashUtils;

import java.io.File;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;

import static org.junit.jupiter.api.Assertions.*;

class DiffGeneratorTest {
    private DiffGenerator diffGenerator;
    private VersionManager versionManager;
    private FileTracker fileTracker;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        Path objectsDir = tempDir.resolve(".vcs").resolve("objects");
        Files.createDirectories(objectsDir);
        versionManager = new VersionManager(tempDir.toString());
        fileTracker = new FileTracker(tempDir.toString());
        diffGenerator = new DiffGenerator(versionManager, fileTracker);
    }

    @Test
    void testGetDiffBetweenVersions() throws Exception {
        // Test diffing between two versions
        Map<String, String> initialHashes = new HashMap<>();
        String file1Hash = createAndStoreFile("file1.txt", "initial\ncontent\nhere");
        initialHashes.put("file1.txt", file1Hash);
        String version1 = versionManager.createVersion("Initial commit", initialHashes);

        Map<String, String> modifiedHashes = new HashMap<>();
        String file2Hash = createAndStoreFile("file1.txt", "modified\ncontent\nhere");
        modifiedHashes.put("file1.txt", file2Hash);
        String version2 = versionManager.createVersion("Modified commit", modifiedHashes);

        DiffResult diff = diffGenerator.getDiff(version1, version2);
        assertTrue(diff.hasChanges());
        ChangedLines changes = diff.changes().get("file1.txt");
        assertNotNull(changes);
        assertEquals(1, changes.modifications().size());
    }

    @Test
    void testGetDiffWithAddedFile() throws Exception {
        // Test diffing when a new file is added
        Map<String, String> initialHashes = new HashMap<>();
        String file1Hash = createAndStoreFile("file1.txt", "content");
        initialHashes.put("file1.txt", file1Hash);
        String version1 = versionManager.createVersion("Initial", initialHashes);

        Map<String, String> newHashes = new HashMap<>(initialHashes);
        String file2Hash = createAndStoreFile("file2.txt", "new file");
        newHashes.put("file2.txt", file2Hash);
        String version2 = versionManager.createVersion("Added file", newHashes);

        DiffResult diff = diffGenerator.getDiff(version1, version2);
        assertTrue(diff.hasChanges());
        assertTrue(diff.changes().containsKey("file2.txt"));
    }

    @Test
    void testGetDiffWithRemovedFile() throws Exception {
        // Test diffing when a file is removed
        Map<String, String> initialHashes = new HashMap<>();
        String file1Hash = createAndStoreFile("file1.txt", "content1");
        String file2Hash = createAndStoreFile("file2.txt", "content2");
        initialHashes.put("file1.txt", file1Hash);
        initialHashes.put("file2.txt", file2Hash);
        String version1 = versionManager.createVersion("Initial", initialHashes);

        Map<String, String> modifiedHashes = new HashMap<>();
        modifiedHashes.put("file1.txt", file1Hash);
        String version2 = versionManager.createVersion("Removed file", modifiedHashes);

        DiffResult diff = diffGenerator.getDiff(version1, version2);
        assertTrue(diff.hasChanges());
        assertTrue(diff.changes().containsKey("file2.txt"));
    }

    @Test
    void testGetDiffForSingleFile() throws Exception {
        String initialContent = "initial content";
        String initialHash = createAndStoreFile("test.txt", initialContent);
        File testFile = new File(tempDir.toFile(), "test.txt");
        fileTracker.trackFile(testFile);

        String modifiedContent = "modified content";
        createAndStoreFile("test.txt", modifiedContent);
        Files.writeString(testFile.toPath(), modifiedContent);

        DiffResult diff = diffGenerator.getDiff(testFile.getPath());
        assertTrue(diff.hasChanges());
        ChangedLines changes = diff.changes().get(testFile.getPath());
        assertNotNull(changes);
        assertFalse(changes.modifications().isEmpty());
    }

    @Test
    void testGetChangedLines() throws Exception {
        String initialContent = "line1\nline2\nline3";
        String initialHash = createAndStoreFile("test.txt", initialContent);
        File testFile = new File(tempDir.toFile(), "test.txt");
        fileTracker.trackFile(testFile);

        String modifiedContent = "line1\nmodified\nline3";
        createAndStoreFile("test.txt", modifiedContent);
        Files.writeString(testFile.toPath(), modifiedContent);

        Map<String, ChangedLines> changes = diffGenerator.getChangedLines(testFile.getPath());
        ChangedLines fileChanges = changes.get(testFile.getPath());
        assertNotNull(fileChanges);
        assertEquals(1, fileChanges.modifications().size());
    }

    private String createAndStoreFile(String name, String content) throws Exception {
        File file = createTestFile(name, content);
        String hash = HashUtils.calculateFileHash(file);

        Path objectsPath = tempDir.resolve(".vcs").resolve("objects");
        Path objectFile = objectsPath.resolve(hash);
        Files.copy(file.toPath(), objectFile, StandardCopyOption.REPLACE_EXISTING);

        return hash;
    }

    private File createTestFile(String name, String content) throws Exception {
        File file = new File(tempDir.toFile(), name);
        Files.writeString(file.toPath(), content);
        return file;
    }
}
