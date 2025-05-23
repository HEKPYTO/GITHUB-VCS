package test.built.impl;

import impl.*;
import model.*;
import exceptions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.nio.file.Path;
import java.io.File;
import java.nio.file.Files;
import java.util.*;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;


class VersionControlSystemTest {
    private VersionControlSystem vcs;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        Files.createDirectories(tempDir.resolve(".vcs").resolve("objects"));
        vcs = new VersionControlSystem(tempDir.toString());
    }

    @Test
    void testInitialization() {
        assertNotNull(vcs.getFileTracker());
        assertNotNull(vcs.getMergeHandler());
        assertNotNull(vcs.getDiffGenerator());
        assertTrue(Files.exists(tempDir.resolve(".vcs")));
        assertTrue(Files.exists(tempDir.resolve(".vcs/objects")));
        assertTrue(Files.exists(tempDir.resolve(".vcs/versions")));
    }

    @Test
    void testFileUpload() throws Exception {
        File testFile = createTestFile("test.txt", "content");
        assertTrue(vcs.upload(testFile));
        assertTrue(vcs.getTrackedFiles().contains(testFile.getPath()));
        assertEquals(FileStatus.TRACKED, vcs.getFileStatuses().get(testFile.getPath()));
    }

    @Test
    void testDirectoryUpload() throws Exception {
        Path subDir = tempDir.resolve("subdir");
        Files.createDirectory(subDir);

        File file1 = createTestFile(subDir.resolve("file1.txt").toString(), "content1");
        File file2 = createTestFile(subDir.resolve("file2.txt").toString(), "content2");

        assertTrue(vcs.uploadDirectory(subDir.toFile()));
        assertTrue(vcs.getTrackedFiles().contains(file1.getPath()));
        assertTrue(vcs.getTrackedFiles().contains(file2.getPath()));
    }

    @Test
    void testFileModificationAndVersioning() throws Exception {
        File testFile = createTestFile("test.txt", "initial content");
        vcs.upload(testFile);
        String version1 = vcs.createVersion("Initial commit");

        Files.writeString(testFile.toPath(), "modified content");
        vcs.getFileTracker().updateFileStatus(testFile.getPath());
        String version2 = vcs.createVersion("Modified file");

        List<VersionInfo> history = vcs.getVersionHistory();
        assertEquals(2, history.size());
        assertEquals(version1, history.get(0).getVersionId());
        assertEquals(version2, history.get(1).getVersionId());
    }

    @Test
    void testRevertToVersion() throws Exception {
        File testFile = createTestFile("test.txt", "initial content");
        vcs.upload(testFile);
        String version1 = vcs.createVersion("Initial commit");

        Files.writeString(testFile.toPath(), "modified content");
        vcs.getFileTracker().updateFileStatus(testFile.getPath());
        vcs.createVersion("Modified version");

        vcs.revertToVersion(version1);
        assertEquals("initial content", Files.readString(testFile.toPath()));
    }

    @Test
    void testRevertToInvalidVersion() {
        assertThrows(VersionException.class,
                () -> vcs.revertToVersion("nonexistent-version"));
    }

    @Test
    void testFileChangeListeners() throws Exception {
        List<String> changedFiles = new ArrayList<>();
        vcs.addFileChangeListener(changedFiles::add);

        File testFile = createTestFile("test.txt", "content");
        vcs.upload(testFile);

        assertEquals(1, changedFiles.size());
        assertEquals(testFile.getPath(), changedFiles.getFirst());
    }

    @Test
    void testRemoveFileChangeListener() throws Exception {
        List<String> changedFiles = new ArrayList<>();
        Consumer<String> listener = changedFiles::add;
        vcs.addFileChangeListener(listener);
        vcs.removeFileChangeListener(listener);

        File testFile = createTestFile("test.txt", "content");
        vcs.upload(testFile);

        assertTrue(changedFiles.isEmpty());
    }

    @Test
    void testUntrackFile() throws Exception {
        File testFile = createTestFile("test.txt", "content");
        vcs.upload(testFile);

        vcs.untrackFile(testFile.getPath());
        assertFalse(vcs.getTrackedFiles().contains(testFile.getPath()));
        assertNull(vcs.getFileTracker().getFileMetadata(testFile.getPath()));
    }

    @Test
    void testEmptyVersion() throws Exception {
        File testFile = createTestFile("test.txt", "content");
        vcs.upload(testFile);

        vcs.trackFile(testFile.getPath());

        String initialVersionId = vcs.createVersion("Initial commit");
        assertNotNull(initialVersionId);

        String emptyVersionId = vcs.createVersion("Empty commit");
        VersionInfo version = vcs.getCurrentVersion();

        assertNotNull(version);
        assertEquals(emptyVersionId, version.getVersionId());
        assertTrue(version.getFileHashes().isEmpty());
    }


    @Test
    void testConcurrentModification() throws Exception {
        File testFile = createTestFile("test.txt", "content");
        vcs.upload(testFile);

        CompletableFuture<Void> future1 = CompletableFuture.runAsync(() -> {
            try {
                Files.writeString(testFile.toPath(), "modification 1");
                vcs.getFileTracker().updateFileStatus(testFile.getPath());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture<Void> future2 = CompletableFuture.runAsync(() -> {
            try {
                Files.writeString(testFile.toPath(), "modification 2");
                vcs.getFileTracker().updateFileStatus(testFile.getPath());
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        });

        CompletableFuture.allOf(future1, future2).get();
        assertEquals(FileStatus.MODIFIED, vcs.getFileStatuses().get(testFile.getPath()));
    }

    private File createTestFile(String name, String content) throws Exception {
        Path filePath = tempDir.resolve(name);
        Files.createDirectories(filePath.getParent());
        Files.writeString(filePath, content);
        return filePath.toFile();
    }
}
