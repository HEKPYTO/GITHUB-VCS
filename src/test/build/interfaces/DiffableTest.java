package test.build.interfaces;

import interfaces.Diffable;
import model.*;
import exceptions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;
import java.nio.file.*;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class DiffableTest {
    private TestDiffable diffable;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("diffable_test");
        diffable = new TestDiffable();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null) {
            Files.walk(tempDir)
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

    @Nested
    class VersionDiffTest {
        @Test
        void shouldReturnAdditionsOnly() throws VCSException {
            DiffResult result = diffable.getDiff("v1", "v2");

            assertNotNull(result);
            assertEquals("v1", result.oldVersion());
            assertEquals("v2", result.newVersion());
            assertTrue(result.hasChanges());

            ChangedLines changes = result.changes().get("test.txt");
            assertNotNull(changes);
            assertEquals(1, changes.additions().size());
            assertTrue(changes.deletions().isEmpty());
            assertTrue(changes.modifications().isEmpty());
        }

        @Test
        void shouldReturnAllChangeTypes() throws VCSException {
            diffable.setChangeTypes(ChangeSet.ALL);
            DiffResult result = diffable.getDiff("v1", "v2");

            ChangedLines changes = result.changes().get("test.txt");
            assertNotNull(changes);
            assertEquals(1, changes.additions().size());
            assertEquals(1, changes.deletions().size());
            assertEquals(1, changes.modifications().size());
        }

        @Test
        void shouldReturnEmptyResultWhenNoChanges() throws VCSException {
            diffable.setChangeTypes(ChangeSet.NONE);
            DiffResult result = diffable.getDiff("v1", "v2");

            assertTrue(result.changes().isEmpty());
            assertFalse(result.hasChanges());
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t", "\n"})
        void shouldRejectInvalidVersions(String invalidVersion) {
            assertThrows(VCSException.class, () -> diffable.getDiff(invalidVersion, "v2"));
            assertThrows(VCSException.class, () -> diffable.getDiff("v1", invalidVersion));
        }

        @Test
        void shouldRejectNullVersions() {
            assertThrows(VCSException.class, () -> diffable.getDiff(null, "v2"));
            assertThrows(VCSException.class, () -> diffable.getDiff("v1", null));
            assertThrows(VCSException.class, () -> diffable.getDiff(null, null));
        }
    }

    @Nested
    class FileDiffTest {
        @Test
        void shouldReturnChangesForValidFile() throws Exception {
            Path testFile = createTestFile("test.txt", "test content");
            DiffResult result = diffable.getDiff(testFile.toString());

            assertNotNull(result);
            assertEquals("current", result.oldVersion());
            assertEquals("working", result.newVersion());
            assertTrue(result.hasChanges());

            ChangedLines changes = result.changes().get(testFile.toString());
            assertNotNull(changes);
            assertFalse(changes.additions().isEmpty());
        }

        @Test
        void shouldRejectNonexistentFile() {
            String nonexistentFile = tempDir.resolve("nonexistent.txt").toString();
            assertThrows(VCSException.class, () -> diffable.getDiff(nonexistentFile));
        }

        @Test
        void shouldRejectDirectory() {
            assertThrows(VCSException.class, () -> diffable.getDiff(tempDir.toString()));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t", "\n"})
        void shouldRejectInvalidPaths(String invalidPath) {
            assertThrows(VCSException.class, () -> diffable.getDiff(invalidPath));
        }
    }

    @Nested
    class CustomDiffStrategyTest {
        @ParameterizedTest
        @MethodSource("provideDiffStrategyCases")
        void shouldComputeDiffWithValidInputs(String oldContent, String newContent, int expectedDiff)
                throws VCSException {
            Diffable.DiffStrategy<Integer> strategy = (old, newC) ->
                    Math.abs(old.length() - newC.length());

            assertEquals(expectedDiff, diffable.computeCustomDiff(oldContent, newContent, strategy));
        }

        private static Stream<Arguments> provideDiffStrategyCases() {
            return Stream.of(
                    Arguments.of("old", "new", 0),
                    Arguments.of("short", "longer", 1),
                    Arguments.of("", "new", 3),
                    Arguments.of("old", "", 3),
                    Arguments.of("same", "same", 0)
            );
        }

        @Test
        void shouldRejectNullStrategy() {
            assertThrows(VCSException.class, () ->
                    diffable.computeCustomDiff("old", "new", null));
        }

        @Test
        void shouldRejectNullContent() {
            Diffable.DiffStrategy<Integer> strategy = (old, newC) -> 0;
            assertThrows(VCSException.class, () ->
                    diffable.computeCustomDiff(null, "new", strategy));
            assertThrows(VCSException.class, () ->
                    diffable.computeCustomDiff("old", null, strategy));
        }
    }

    @Nested
    class ChangedLinesTest {
        @Test
        void shouldReturnModificationsForValidFile() throws Exception {
            Path testFile = createTestFile("test.txt", "test content");
            Map<String, ChangedLines> changes = diffable.getChangedLines(testFile.toString());

            assertNotNull(changes);
            assertTrue(changes.containsKey(testFile.toString()));

            ChangedLines fileChanges = changes.get(testFile.toString());
            assertEquals(1, fileChanges.modifications().size());
        }

        @Test
        void shouldRejectNonexistentFile() {
            String nonexistentFile = tempDir.resolve("nonexistent.txt").toString();
            assertThrows(VCSException.class, () ->
                    diffable.getChangedLines(nonexistentFile));
        }

        @Test
        void shouldRejectNullPath() {
            assertThrows(VCSException.class, () -> diffable.getChangedLines(null));
        }
    }

    private Path createTestFile(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file;
    }

    private enum ChangeSet {
        NONE,
        ADDITIONS_ONLY,
        DELETIONS_ONLY,
        MODIFICATIONS_ONLY,
        ALL
    }

    private static class TestDiffable implements Diffable {
        private ChangeSet changeTypes = ChangeSet.ADDITIONS_ONLY;

        void setChangeTypes(ChangeSet types) {
            this.changeTypes = types;
        }

        @Override
        public DiffResult getDiff(String oldVersion, String newVersion) throws VCSException {
            validateVersions(oldVersion, newVersion);
            Map<String, ChangedLines> changes = new HashMap<>();
            if (changeTypes != ChangeSet.NONE) {
                changes.put("test.txt", createChangedLines());
            }
            return new DiffResult(oldVersion, newVersion, changes);
        }

        @Override
        public DiffResult getDiff(String filePath) throws VCSException {
            validateFilePath(filePath);
            Map<String, ChangedLines> changes = new HashMap<>();
            changes.put(filePath, createChangedLines());
            return new DiffResult("current", "working", changes);
        }

        @Override
        public Map<String, ChangedLines> getChangedLines(String filePath) throws VCSException {
            validateFilePath(filePath);
            Map<String, ChangedLines> changes = new HashMap<>();
            changes.put(filePath, createBasicChanges());
            return changes;
        }

        @Override
        public <T> T computeCustomDiff(String oldVersion, String newVersion, DiffStrategy<T> strategy)
                throws VCSException {
            if (strategy == null) {
                throw new VCSException("DiffStrategy cannot be null");
            }
            if (oldVersion == null || newVersion == null) {
                throw new VCSException("Version strings cannot be null");
            }
            return strategy.computeDiff(oldVersion, newVersion);
        }

        private ChangedLines createChangedLines() {
            List<LineChange> additions = new ArrayList<>();
            List<LineChange> deletions = new ArrayList<>();
            List<LineChange> modifications = new ArrayList<>();

            if (changeTypes == ChangeSet.ADDITIONS_ONLY || changeTypes == ChangeSet.ALL) {
                additions.add(new LineChange(1, null, "new line", LineChange.ChangeType.ADDITION));
            }
            if (changeTypes == ChangeSet.DELETIONS_ONLY || changeTypes == ChangeSet.ALL) {
                deletions.add(new LineChange(2, "old line", null, LineChange.ChangeType.DELETION));
            }
            if (changeTypes == ChangeSet.MODIFICATIONS_ONLY || changeTypes == ChangeSet.ALL) {
                modifications.add(new LineChange(3, "old content", "new content",
                        LineChange.ChangeType.MODIFICATION));
            }

            return new ChangedLines(additions, deletions, modifications);
        }

        private ChangedLines createBasicChanges() {
            List<LineChange> modifications = new ArrayList<>();
            modifications.add(new LineChange(1, "old", "new", LineChange.ChangeType.MODIFICATION));
            return new ChangedLines(new ArrayList<>(), new ArrayList<>(), modifications);
        }

        private void validateVersions(String oldVersion, String newVersion) throws VCSException {
            if (oldVersion == null || newVersion == null) {
                throw new VCSException("Version cannot be null");
            }
            if (oldVersion.trim().isEmpty() || newVersion.trim().isEmpty()) {
                throw new VCSException("Version cannot be empty");
            }
        }

        private void validateFilePath(String filePath) throws VCSException {
            if (filePath == null) {
                throw new VCSException("File path cannot be null");
            }
            if (filePath.trim().isEmpty()) {
                throw new VCSException("File path cannot be empty");
            }

            Path path = Paths.get(filePath);
            if (!Files.exists(path)) {
                throw new VCSException("File does not exist: " + filePath);
            }
            if (Files.isDirectory(path)) {
                throw new VCSException("Path is a directory: " + filePath);
            }
        }
    }
}