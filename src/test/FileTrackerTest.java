package test;

import impl.FileTracker;
import model.FileMetadata;
import model.FileStatus;
import exceptions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.api.io.TempDir;
import java.io.File;
import java.io.IOException;
import java.nio.file.*;
import java.util.List;
import static org.junit.jupiter.api.Assertions.*;

class FileTrackerTest {
    private FileTracker fileTracker;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws IOException, FileOperationException {
        fileTracker = new FileTracker(tempDir.toString());
        Path objectsDir = tempDir.resolve(".vcs").resolve("objects");
        Files.createDirectories(objectsDir);
    }

    @Test
    void testTrackNewValidFile() throws Exception {
        // Test tracking a new valid file
        File testFile = createTestFile("test.txt", "content");
        assertTrue(fileTracker.trackFile(testFile));
        FileMetadata metadata = fileTracker.getFileMetadata(testFile.getPath());
        assertNotNull(metadata);
        assertEquals(FileStatus.TRACKED, metadata.getStatus());
    }

    @Test
    void testTrackNonexistentFile() {
        // Edge case: tracking a file that doesn't exist
        File nonExistentFile = new File(tempDir.toString(), "nonexistent.txt");
        assertThrows(FileOperationException.class, () -> fileTracker.trackFile(nonExistentFile));
    }

    @Test
    void testTrackDirectory() {
        // Edge case: trying to track a directory instead of a file
        assertThrows(FileOperationException.class,
                () -> fileTracker.trackFile(tempDir.toFile()));
    }

    @Test
    void testTrackEmptyFile() throws Exception {
        // Edge case: tracking an empty file
        File emptyFile = createTestFile("empty.txt", "");
        assertTrue(fileTracker.trackFile(emptyFile));
        assertNotNull(fileTracker.getFileMetadata(emptyFile.getPath()));
    }

    @Test
    void testUpdateFileStatus() throws Exception {
        // Test file status updates
        File testFile = createTestFile("test.txt", "initial content");
        fileTracker.trackFile(testFile);

        // Modify file
        Files.writeString(testFile.toPath(), "modified content");
        fileTracker.updateFileStatus(testFile.getPath());

        FileMetadata metadata = fileTracker.getFileMetadata(testFile.getPath());
        assertEquals(FileStatus.MODIFIED, metadata.getStatus());
    }

    @Test
    void testUpdateDeletedFile() throws Exception {
        File testFile = createTestFile("test.txt", "content");
        fileTracker.trackFile(testFile);

        Files.delete(testFile.toPath());
        fileTracker.updateFileStatus(testFile.getPath());

        FileMetadata metadata = fileTracker.getFileMetadata(testFile.getPath());
        assertEquals(FileStatus.DELETED, metadata.getStatus());
    }

    @Test
    void testUntrackFile() throws Exception {
        // Test file untracking
        File testFile = createTestFile("test.txt", "content");
        fileTracker.trackFile(testFile);

        fileTracker.untrackFile(testFile.getPath());
        assertNull(fileTracker.getFileMetadata(testFile.getPath()));
        assertFalse(fileTracker.getTrackedFiles().contains(testFile));
    }

    @Test
    void testGetTrackedFiles() throws Exception {
        // Test tracking multiple files and retrieving them
        File file1 = createTestFile("file1.txt", "content1");
        File file2 = createTestFile("file2.txt", "content2");

        fileTracker.trackFile(file1);
        fileTracker.trackFile(file2);

        List<String> trackedFiles = fileTracker.getTrackedFiles();
        assertEquals(2, trackedFiles.size());
        assertTrue(trackedFiles.contains(file1.getPath()));
        assertTrue(trackedFiles.contains(file2.getPath()));
    }

    @Test
    void testIsFileModified() throws Exception {
        // Test file modification detection
        File testFile = createTestFile("test.txt", "original content");
        fileTracker.trackFile(testFile);

        assertFalse(fileTracker.isFileModified(testFile.getPath()));

        Files.writeString(testFile.toPath(), "modified content");
        assertTrue(fileTracker.isFileModified(testFile.getPath()));
    }

    @Test
    void testCommitFile() throws Exception {
        // Test committing a file
        File testFile = createTestFile("test.txt", "content");
        fileTracker.trackFile(testFile);

        String versionId = "test-version-1";
        fileTracker.commitFile(testFile.getPath(), versionId);

        FileMetadata metadata = fileTracker.getFileMetadata(testFile.getPath());
        assertEquals(FileStatus.TRACKED, metadata.getStatus());
        assertTrue(metadata.getVersions().contains(versionId));
    }

    private File createTestFile(String name, String content) throws Exception {
        File file = new File(tempDir.toFile(), name);
        Files.writeString(file.toPath(), content);
        return file;
    }
}
