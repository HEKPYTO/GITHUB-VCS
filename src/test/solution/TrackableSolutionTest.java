package test.solution;

import interfaces.Trackable;
import model.FileStatus;
import exceptions.VCSException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import java.util.function.Consumer;
import static org.junit.jupiter.api.Assertions.*;

class TrackableSolutionTest {
    private TestTrackable trackable;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("solution_test");
        trackable = new TestTrackable();
    }

    @AfterEach
    void tearDown() throws Exception {
        deleteDirectory(tempDir);
    }

    @Test
    void shouldTrackValidFile() throws Exception {
        Path testFile = createFile("Repository.kt", "data");
        trackable.trackFile(testFile.toString());
        assertTrue(trackable.getTrackedFiles().contains(testFile.toString()));
        assertEquals(FileStatus.TRACKED, trackable.getFileStatuses().get(testFile.toString()));
    }

    @Test
    void shouldNotifyListenersOnTrack() throws Exception {
        List<String> notifications = new ArrayList<>();
        Consumer<String> listener = notifications::add;
        trackable.addFileChangeListener(listener);

        Path testFile = createFile("Repository.kt", "data");
        trackable.trackFile(testFile.toString());

        assertEquals(1, notifications.size());
        assertEquals(testFile.toString(), notifications.getFirst());
    }

    @Test
    void shouldRejectNonexistentFile() {
        String nonexistentFile = tempDir.resolve("missing.kt").toString();
        assertThrows(VCSException.class, () -> trackable.trackFile(nonexistentFile));
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n"})
    void shouldRejectInvalidPaths(String invalidPath) {
        assertThrows(VCSException.class, () -> trackable.trackFile(invalidPath));
    }

    @Test
    void shouldRejectNullPath() {
        assertThrows(VCSException.class, () -> trackable.trackFile(null));
    }

    @Test
    void shouldUntrackFile() throws Exception {
        Path testFile = createFile("Repository.kt", "data");
        trackable.trackFile(testFile.toString());
        trackable.untrackFile(testFile.toString());

        assertFalse(trackable.getTrackedFiles().contains(testFile.toString()));
        assertFalse(trackable.getFileStatuses().containsKey(testFile.toString()));
    }

    @Test
    void shouldNotifyListenersOnUntrack() throws Exception {
        List<String> notifications = new ArrayList<>();
        Consumer<String> listener = notifications::add;
        trackable.addFileChangeListener(listener);

        Path testFile = createFile("Repository.kt", "data");
        trackable.trackFile(testFile.toString());
        notifications.clear();

        trackable.untrackFile(testFile.toString());
        assertEquals(1, notifications.size());
        assertEquals(testFile.toString(), notifications.getFirst());
    }

    @Test
    void shouldIgnoreUntrackingNonexistentFile() {
        assertDoesNotThrow(() -> trackable.untrackFile("missing.kt"));
    }

    @Test
    void shouldManageMultipleListeners() throws Exception {
        List<String> notifications1 = new ArrayList<>();
        List<String> notifications2 = new ArrayList<>();

        Consumer<String> listener1 = notifications1::add;
        Consumer<String> listener2 = notifications2::add;

        trackable.addFileChangeListener(listener1);
        trackable.addFileChangeListener(listener2);

        Path testFile = createFile("Repository.kt", "data");
        trackable.trackFile(testFile.toString());

        assertEquals(1, notifications1.size());
        assertEquals(1, notifications2.size());

        trackable.removeFileChangeListener(listener1);
        trackable.untrackFile(testFile.toString());

        assertEquals(1, notifications1.size());
        assertEquals(2, notifications2.size());
    }

    @Test
    void shouldHandleNullListener() {
        assertThrows(NullPointerException.class, () -> trackable.addFileChangeListener(null));
        assertThrows(NullPointerException.class, () -> trackable.removeFileChangeListener(null));
    }

    @Test
    void shouldReportCorrectFileStatuses() throws Exception {
        Path testFile = createFile("Repository.kt", "data");
        trackable.trackFile(testFile.toString());
        trackable.setFileStatus(testFile.toString(), FileStatus.MODIFIED);

        Map<String, FileStatus> statuses = trackable.getFileStatuses();
        assertEquals(FileStatus.MODIFIED, statuses.get(testFile.toString()));
    }

    @Test
    void shouldHandleMultipleFileStatuses() throws Exception {
        Path file1 = createFile("Service.kt", "data1");
        Path file2 = createFile("Database.kt", "data2");

        trackable.trackFile(file1.toString());
        trackable.trackFile(file2.toString());

        trackable.setFileStatus(file1.toString(), FileStatus.MODIFIED);
        trackable.setFileStatus(file2.toString(), FileStatus.STAGED);

        Map<String, FileStatus> statuses = trackable.getFileStatuses();
        assertEquals(FileStatus.MODIFIED, statuses.get(file1.toString()));
        assertEquals(FileStatus.STAGED, statuses.get(file2.toString()));
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

    private static class TestTrackable implements Trackable {
        private final Set<String> trackedFiles = new HashSet<>();
        private final Map<String, FileStatus> fileStatuses = new HashMap<>();
        private final List<Consumer<String>> listeners = new ArrayList<>();

        @Override
        public void trackFile(String filePath) throws VCSException {
            validateFilePath(filePath);
            trackedFiles.add(filePath);
            fileStatuses.put(filePath, FileStatus.TRACKED);
            notifyListeners(filePath);
        }

        @Override
        public void untrackFile(String filePath) {
            if (trackedFiles.remove(filePath)) {
                fileStatuses.remove(filePath);
                notifyListeners(filePath);
            }
        }

        @Override
        public List<String> getTrackedFiles() {
            return new ArrayList<>(trackedFiles);
        }

        @Override
        public Map<String, FileStatus> getFileStatuses() {
            return new HashMap<>(fileStatuses);
        }

        @Override
        public void addFileChangeListener(Consumer<String> listener) {
            Objects.requireNonNull(listener);
            listeners.add(listener);
        }

        @Override
        public void removeFileChangeListener(Consumer<String> listener) {
            Objects.requireNonNull(listener);
            listeners.remove(listener);
        }

        void setFileStatus(String filePath, FileStatus status) {
            if (trackedFiles.contains(filePath)) {
                fileStatuses.put(filePath, status);
            }
        }

        private void validateFilePath(String filePath) throws VCSException {
            if (filePath == null || filePath.trim().isEmpty()) {
                throw new VCSException("File path cannot be empty");
            }

            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new VCSException("File does not exist: " + filePath);
            }
        }

        private void notifyListeners(String filePath) {
            for (Consumer<String> listener : listeners) {
                listener.accept(filePath);
            }
        }
    }
}
