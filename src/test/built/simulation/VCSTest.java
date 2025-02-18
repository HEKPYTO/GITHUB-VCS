package test.built.simulation;

import impl.VersionControlSystem;
import model.*;
import exceptions.VCSException;
import org.junit.jupiter.api.*;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VCSTest {
    private Path repoPath;
    private VersionControlSystem vcs;
    private Path file1;
    private Path file2;
    private static final String INITIAL_CONTENT_1 = "Initial content for file 1\nSecond line\nThird line";
    private static final String INITIAL_CONTENT_2 = "Initial content for file 2\nAnother line\nFinal line";
    private static final String MODIFIED_CONTENT = "Modified content\nNew line added\nChanged content";

    @BeforeEach
    void setUp() throws Exception {
        repoPath = Files.createTempDirectory("vcs_test");
        vcs = new VersionControlSystem(repoPath.toString());
        setupTestFiles();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (repoPath != null) {
            Files.walk(repoPath)
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

    private void setupTestFiles() throws Exception {
        file1 = repoPath.resolve("file1.txt");
        file2 = repoPath.resolve("file2.txt");
        Files.writeString(file1, INITIAL_CONTENT_1);
        Files.writeString(file2, INITIAL_CONTENT_2);
    }

    @Nested
    class FileOperationsTest {
        @Test
        void shouldTrackMultipleFiles() throws Exception {
            vcs.trackFile(file1.toString());
            vcs.trackFile(file2.toString());

            List<String> trackedFiles = vcs.getTrackedFiles();
            assertEquals(2, trackedFiles.size());
            assertTrue(trackedFiles.contains(file1.toString()));
            assertTrue(trackedFiles.contains(file2.toString()));
        }

        @Test
        void shouldReportCorrectFileStatuses() throws Exception {
            vcs.trackFile(file1.toString());
            vcs.trackFile(file2.toString());

            Map<String, FileStatus> statuses = vcs.getFileStatuses();
            assertEquals(FileStatus.TRACKED, statuses.get(file1.toString()));
            assertEquals(FileStatus.TRACKED, statuses.get(file2.toString()));
        }

        @Test
        void shouldDetectFileModifications() throws Exception {
            vcs.trackFile(file1.toString());
            String version1 = vcs.createVersion("Initial commit");

            Files.writeString(file1, MODIFIED_CONTENT);
            vcs.getFileTracker().updateFileStatus(file1.toString());

            Map<String, FileStatus> statuses = vcs.getFileStatuses();
            assertEquals(FileStatus.MODIFIED, statuses.get(file1.toString()));
        }
    }

    @Nested
    class VersioningTest {
        @Test
        void shouldCreateVersions() throws Exception {
            vcs.trackFile(file1.toString());
            vcs.trackFile(file2.toString());

            String version1 = vcs.createVersion("Initial commit");
            assertNotNull(version1);

            Files.writeString(file1, MODIFIED_CONTENT);
            vcs.getFileTracker().updateFileStatus(file1.toString());

            String version2 = vcs.createVersion("Modified file1");
            assertNotNull(version2);
            assertNotEquals(version1, version2);
        }

        @Test
        void shouldMaintainVersionHistory() throws Exception {
            vcs.trackFile(file1.toString());
            String version1 = vcs.createVersion("Initial commit");

            Files.writeString(file1, MODIFIED_CONTENT);
            vcs.getFileTracker().updateFileStatus(file1.toString());
            String version2 = vcs.createVersion("Modified file1");

            List<VersionInfo> history = vcs.getVersionHistory();
            assertEquals(2, history.size());
            assertEquals(version1, history.get(0).getVersionId());
            assertEquals(version2, history.get(1).getVersionId());
        }
    }

    @Nested
    class DiffGenerationTest {
        @Test
        void shouldGenerateAccurateDiff() throws Exception {
            vcs.trackFile(file1.toString());
            String version1 = vcs.createVersion("Initial commit");

            Files.writeString(file1, MODIFIED_CONTENT);
            vcs.getFileTracker().updateFileStatus(file1.toString());
            String version2 = vcs.createVersion("Modified file1");

            DiffResult diff = vcs.getDiffGenerator().getDiff(version1, version2);
            assertTrue(diff.hasChanges());

            ChangedLines changes = diff.changes().get(file1.toString());
            assertNotNull(changes);
            assertFalse(changes.modifications().isEmpty());
        }

        @Test
        void shouldHandleUnmodifiedFiles() throws Exception {
            vcs.trackFile(file1.toString());
            vcs.trackFile(file2.toString());

            String version1 = vcs.createVersion("Initial commit");
            String version2 = vcs.createVersion("No changes");

            DiffResult diff = vcs.getDiffGenerator().getDiff(version1, version2);
            assertFalse(diff.hasChanges());
        }
    }

    @Nested
    class ErrorHandlingTest {
        @Test
        void shouldHandleNonexistentFiles() {
            Path nonexistentFile = repoPath.resolve("nonexistent.txt");
            assertThrows(VCSException.class, () ->
                    vcs.trackFile(nonexistentFile.toString()));
        }

        @Test
        void shouldHandleVersionCreationWithoutTrackedFiles() throws Exception {
            // Ensure no files are tracked
            for (String file : vcs.getTrackedFiles()) {
                vcs.untrackFile(file);
            }
            assertThrows(VCSException.class, () ->
                    vcs.createVersion("First version"));
        }

        @Test
        void shouldHandleEmptyVersionMessage() throws Exception {
            // First ensure we have a tracked file to create a valid version
            vcs.trackFile(file1.toString());
            vcs.getFileTracker().updateFileStatus(file1.toString());

            // Test each invalid message case separately
            assertThrows(VCSException.class, () -> vcs.createVersion(null),
                    "Should throw exception for null message");

            assertThrows(VCSException.class, () -> vcs.createVersion(""),
                    "Should throw exception for empty message");

            assertThrows(VCSException.class, () -> vcs.createVersion(" "),
                    "Should throw exception for blank message");

            assertThrows(VCSException.class, () -> vcs.createVersion("\t"),
                    "Should throw exception for tab message");

            assertThrows(VCSException.class, () -> vcs.createVersion("\n"),
                    "Should throw exception for newline message");
        }

        @Test
        void shouldHandleNonexistentVersions() throws Exception {
            // First create a valid version
            vcs.trackFile(file1.toString());
            vcs.createVersion("Initial version");

            assertThrows(VCSException.class, () ->
                    vcs.getDiffGenerator().getDiff("nonexistent1", "nonexistent2"));
        }

        @Test
        void shouldHandleNullDiffVersions() throws Exception {
            vcs.trackFile(file1.toString());
            String version1 = vcs.createVersion("Initial version");

            assertThrows(VCSException.class, () ->
                    vcs.getDiffGenerator().getDiff(null, version1));
            assertThrows(VCSException.class, () ->
                    vcs.getDiffGenerator().getDiff(version1, null));
            assertThrows(VCSException.class, () ->
                    vcs.getDiffGenerator().getDiff(null, null));
        }
    }

    @Nested
    class WorkflowTest {
        @Test
        void shouldExecuteCompleteWorkflow() throws Exception {
            vcs.trackFile(file1.toString());
            vcs.trackFile(file2.toString());

            String version1 = vcs.createVersion("Initial commit");
            assertNotNull(version1);

            Files.writeString(file1, MODIFIED_CONTENT);
            vcs.getFileTracker().updateFileStatus(file1.toString());
            assertEquals(FileStatus.MODIFIED, vcs.getFileStatuses().get(file1.toString()));

            String version2 = vcs.createVersion("Modified file1");
            assertNotNull(version2);

            DiffResult diff = vcs.getDiffGenerator().getDiff(version1, version2);
            assertTrue(diff.hasChanges());
            assertFalse(diff.changes().isEmpty());

            List<VersionInfo> history = vcs.getVersionHistory();
            assertEquals(2, history.size());
        }
    }
}