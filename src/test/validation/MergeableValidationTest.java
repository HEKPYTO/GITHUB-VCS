package test.validation;

import interfaces.Mergeable;
import model.*;
import exceptions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MergeableValidationTest {
    private TestMergeable mergeable;

    @BeforeEach
    void setUp() {
        mergeable = new TestMergeable();
    }

    @Test
    void shouldSucceedWhenNoConflictsExist() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.NO_CONFLICTS);
        assertTrue(mergeable.merge("master", "feature"));
        assertTrue(mergeable.getConflicts().isEmpty());
    }

    @Test
    void shouldFailAndReportConflictsWhenPresent() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.WITH_CONFLICTS);
        assertFalse(mergeable.merge("master", "feature"));

        List<ConflictInfo> conflicts = mergeable.getConflicts();
        assertFalse(conflicts.isEmpty());
        assertEquals(1, conflicts.size());

        ConflictInfo conflict = conflicts.getFirst();
        assertEquals("main.java", conflict.getFilePath());
        assertEquals(ConflictInfo.ConflictStatus.UNRESOLVED, conflict.getStatus());

        List<ConflictInfo.ConflictBlock> blocks = conflict.getConflicts();
        assertEquals(2, blocks.size());

        assertEquals(0, blocks.getFirst().startLine());
        assertEquals(1, blocks.getFirst().endLine());
        assertEquals("master content 0", blocks.get(0).sourceContent());
        assertEquals("feature content 0", blocks.get(0).targetContent());

        assertEquals(1, blocks.get(1).startLine());
        assertEquals(2, blocks.get(1).endLine());
        assertEquals("master content 1", blocks.get(1).sourceContent());
        assertEquals("feature content 1", blocks.get(1).targetContent());
    }

    @Test
    void shouldHandleMultipleFileConflicts() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.MULTIPLE_FILES);
        assertFalse(mergeable.merge("master", "feature"));

        List<ConflictInfo> conflicts = mergeable.getConflicts();
        assertEquals(3, conflicts.size());
        assertEquals("Main.java", conflicts.get(0).getFilePath());
        assertEquals("Service.java", conflicts.get(1).getFilePath());
        assertEquals("Model.java", conflicts.get(2).getFilePath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n"})
    void shouldRejectInvalidVersions(String invalidVersion) {
        assertThrows(VCSException.class, () -> mergeable.merge(invalidVersion, "feature"));
        assertThrows(VCSException.class, () -> mergeable.merge("master", invalidVersion));
    }

    @Test
    void shouldRejectNullVersions() {
        assertThrows(VCSException.class, () -> mergeable.merge(null, "feature"));
        assertThrows(VCSException.class, () -> mergeable.merge("master", null));
        assertThrows(VCSException.class, () -> mergeable.merge(null, null));
    }

    @Test
    void shouldThrowExceptionForNonexistentVersions() {
        mergeable.setMergeScenario(MergeScenario.NONEXISTENT_VERSION);
        assertThrows(VCSException.class, () -> mergeable.merge("nonexistent", "feature"));
    }

    @Test
    void shouldResolveConflictWithSourceStrategy() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.WITH_CONFLICTS);
        mergeable.merge("master", "feature");

        ConflictResolution resolution = new ConflictResolution(
                "main.java",
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_SOURCE
        );

        mergeable.resolveConflict("main.java", resolution);
        assertTrue(isConflictResolved("main.java"));
    }

    @Test
    void shouldResolveConflictWithTargetStrategy() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.WITH_CONFLICTS);
        mergeable.merge("master", "feature");

        ConflictResolution resolution = new ConflictResolution(
                "main.java",
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_TARGET
        );

        mergeable.resolveConflict("main.java", resolution);
        assertTrue(isConflictResolved("main.java"));
    }

    @Test
    void shouldResolveConflictWithCustomStrategy() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.WITH_CONFLICTS);
        mergeable.merge("master", "feature");

        Map<Integer, String> customResolutions = new HashMap<>();
        customResolutions.put(1, "custom resolution 1");
        customResolutions.put(2, "custom resolution 2");

        ConflictResolution resolution = new ConflictResolution(
                "main.java",
                customResolutions,
                ConflictResolution.ResolutionStrategy.CUSTOM
        );

        mergeable.resolveConflict("main.java", resolution);
        assertTrue(isConflictResolved("main.java"));
    }

    @Test
    void shouldThrowExceptionForNonexistentFile() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.WITH_CONFLICTS);
        mergeable.merge("master", "feature");

        ConflictResolution resolution = new ConflictResolution(
                "nonexistent.java",
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_SOURCE
        );

        assertThrows(VCSException.class, () ->
                mergeable.resolveConflict("nonexistent.java", resolution));
    }

    @Test
    void shouldThrowExceptionWhenNoConflictsExist() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.NO_CONFLICTS);
        mergeable.merge("master", "feature");

        ConflictResolution resolution = new ConflictResolution(
                "main.java",
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_SOURCE
        );

        assertThrows(VCSException.class, () ->
                mergeable.resolveConflict("main.java", resolution));
    }

    @Test
    void shouldRejectNullResolution() {
        assertThrows(VCSException.class, () ->
                mergeable.resolveConflict("main.java", null));
    }

    @Test
    void shouldRejectInvalidFilePath() {
        ConflictResolution resolution = new ConflictResolution(
                "main.java",
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_SOURCE
        );

        assertThrows(VCSException.class, () ->
                mergeable.resolveConflict("", resolution));
        assertThrows(VCSException.class, () ->
                mergeable.resolveConflict(null, resolution));
    }

    private boolean isConflictResolved(String filePath) {
        return mergeable.getConflicts().stream()
                .noneMatch(conflict -> conflict.getFilePath().equals(filePath));
    }

    private enum MergeScenario {
        NO_CONFLICTS,
        WITH_CONFLICTS,
        MULTIPLE_FILES,
        NONEXISTENT_VERSION
    }

    private static class TestMergeable implements Mergeable {
        private MergeScenario scenario = MergeScenario.NO_CONFLICTS;
        private final List<ConflictInfo> conflicts = new ArrayList<>();

        void setMergeScenario(MergeScenario scenario) {
            this.scenario = scenario;
            this.conflicts.clear();
        }

        @Override
        public boolean merge(String sourceVersion, String targetVersion) throws VCSException {
            validateVersions(sourceVersion, targetVersion);

            if (scenario == MergeScenario.NONEXISTENT_VERSION) {
                throw new VCSException("Version does not exist: " + sourceVersion);
            }

            conflicts.clear();

            if (scenario == MergeScenario.WITH_CONFLICTS) {
                createConflicts("main.java", 2);
                return false;
            } else if (scenario == MergeScenario.MULTIPLE_FILES) {
                createConflicts("Main.java", 1);
                createConflicts("Service.java", 1);
                createConflicts("Model.java", 1);
                return false;
            }

            return true;
        }

        @Override
        public List<ConflictInfo> getConflicts() {
            return new ArrayList<>(conflicts);
        }

        @Override
        public void resolveConflict(String filePath, ConflictResolution resolution)
                throws VCSException {
            if (filePath == null || filePath.trim().isEmpty()) {
                throw new VCSException("File path cannot be empty");
            }
            if (resolution == null) {
                throw new VCSException("Resolution cannot be null");
            }

            Optional<ConflictInfo> conflict = conflicts.stream()
                    .filter(c -> c.getFilePath().equals(filePath))
                    .findFirst();

            if (conflict.isEmpty()) {
                throw new VCSException("No conflict found for file: " + filePath);
            }

            conflicts.remove(conflict.get());
        }

        private void validateVersions(String sourceVersion, String targetVersion)
                throws VCSException {
            if (sourceVersion == null || targetVersion == null) {
                throw new VCSException("Version cannot be null");
            }
            if (sourceVersion.trim().isEmpty() || targetVersion.trim().isEmpty()) {
                throw new VCSException("Version cannot be empty");
            }
        }

        private void createConflicts(String filePath, int count) {
            List<ConflictInfo.ConflictBlock> blocks = new ArrayList<>();
            for (int i = 0; i < count; i++) {
                blocks.add(new ConflictInfo.ConflictBlock(
                        i, i + 1,
                        "master content " + i,
                        "feature content " + i
                ));
            }

            conflicts.add(new ConflictInfo(filePath, "hash1", "hash2", blocks));
        }
    }
}