package test.validation;

import impl.FileTracker;
import model.FileStatus;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class FileTrackerValidationTest {
    private FileTracker fileTracker;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("validate_test");
        fileTracker = new FileTracker(tempDir.toString());
        Files.createDirectories(tempDir.resolve(".vcs").resolve("objects"));
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteDirectory(tempDir);
    }

    @Test
    void testUntrackFile() throws Exception {
        Path file = createFile("test.txt", "content");
        fileTracker.trackFile(file.toString());
        fileTracker.untrackFile(file.toString());
        assertFalse(fileTracker.getTrackedFiles().contains(file.toString()));
    }

    @Test
    void testUntrackFileNotTracked() {
        assertDoesNotThrow(() -> fileTracker.untrackFile("nonexistent.txt"));
    }

    @Test
    void testGetTrackedFilesEmpty() {
        assertTrue(fileTracker.getTrackedFiles().isEmpty());
    }

    @Test
    void testGetTrackedFilesMultiple() throws Exception {
        Path file1 = createFile("file1.txt", "content1");
        Path file2 = createFile("file2.txt", "content2");

        fileTracker.trackFile(file1.toString());
        fileTracker.trackFile(file2.toString());

        List<String> trackedFiles = fileTracker.getTrackedFiles();
        assertEquals(2, trackedFiles.size());
        assertTrue(trackedFiles.containsAll(Arrays.asList(file1.toString(), file2.toString())));
    }

    @Test
    void testGetFileStatusesEmpty() {
        assertTrue(fileTracker.getFileStatuses().isEmpty());
    }

    @Test
    void testGetFileStatusesNewlyTracked() throws Exception {
        Path file = createFile("test.txt", "content");
        fileTracker.trackFile(file.toString());
        assertEquals(FileStatus.TRACKED, fileTracker.getFileStatuses().get(file.toString()));
    }

    @Test
    void testGetFileStatusesAfterUntrack() throws Exception {
        Path file = createFile("test.txt", "content");
        fileTracker.trackFile(file.toString());
        fileTracker.untrackFile(file.toString());
        assertFalse(fileTracker.getFileStatuses().containsKey(file.toString()));
    }

    @Test
    void testAddFileChangeListener() throws Exception {
        List<String> notifications = new ArrayList<>();
        fileTracker.addFileChangeListener(notifications::add);

        Path file = createFile("test.txt", "content");
        fileTracker.trackFile(file.toString());

        assertEquals(1, notifications.size());
        assertEquals(file.toString(), notifications.get(0));
    }

    @Test
    void testRemoveFileChangeListener() throws Exception {
        List<String> notifications = new ArrayList<>();
        Consumer<String> listener = notifications::add;

        fileTracker.addFileChangeListener(listener);
        fileTracker.removeFileChangeListener(listener);

        Path file = createFile("test.txt", "content");
        fileTracker.trackFile(file.toString());

        assertTrue(notifications.isEmpty());
    }

    @Test
    void testMultipleListeners() throws Exception {
        List<String> notifications1 = new ArrayList<>();
        List<String> notifications2 = new ArrayList<>();

        fileTracker.addFileChangeListener(notifications1::add);
        fileTracker.addFileChangeListener(notifications2::add);

        Path file = createFile("test.txt", "content");
        fileTracker.trackFile(file.toString());

        assertEquals(1, notifications1.size());
        assertEquals(1, notifications2.size());
        assertEquals(file.toString(), notifications1.getFirst());
        assertEquals(file.toString(), notifications2.getFirst());
    }

    private Path createFile(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private void deleteDirectory(Path dir) throws Exception {
        if (dir != null) {
            Files.walk(dir)
                .sorted(Comparator.reverseOrder())
                .forEach(path -> {
                    try {
                        Files.delete(path);
                    } catch (Exception e) {
                        e.printStackTrace();
                    }
                });
        }
    }
}