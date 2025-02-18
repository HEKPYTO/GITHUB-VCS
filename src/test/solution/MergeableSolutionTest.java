package test.solution;

import interfaces.Mergeable;
import model.*;
import exceptions.*;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class MergeableSolutionTest {
    private TestMergeable mergeable;

    @BeforeEach
    void setUp() {
        mergeable = new TestMergeable();
    }

    @Test
    void shouldSucceedWhenNoConflictsExist() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.NO_CONFLICTS);
        assertTrue(mergeable.merge("dev", "release"));
        assertTrue(mergeable.getConflicts().isEmpty());
    }

    @Test
    void shouldFailAndReportConflictsWhenPresent() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.WITH_CONFLICTS);
        assertFalse(mergeable.merge("dev", "release"));

        List<ConflictInfo> conflicts = mergeable.getConflicts();
        assertFalse(conflicts.isEmpty());
        assertEquals(1, conflicts.size());

        ConflictInfo conflict = conflicts.getFirst();
        assertEquals("Controller.java", conflict.getFilePath());
        assertEquals(ConflictInfo.ConflictStatus.UNRESOLVED, conflict.getStatus());

        List<ConflictInfo.ConflictBlock> blocks = conflict.getConflicts();
        assertEquals(2, blocks.size());

        assertEquals(0, blocks.getFirst().startLine());
        assertEquals(1, blocks.getFirst().endLine());
        assertEquals("dev content 0", blocks.get(0).sourceContent());
        assertEquals("release content 0", blocks.get(0).targetContent());

        assertEquals(1, blocks.get(1).startLine());
        assertEquals(2, blocks.get(1).endLine());
        assertEquals("dev content 1", blocks.get(1).sourceContent());
        assertEquals("release content 1", blocks.get(1).targetContent());
    }

    @Test
    void shouldHandleMultipleFileConflicts() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.MULTIPLE_FILES);
        assertFalse(mergeable.merge("dev", "release"));

        List<ConflictInfo> conflicts = mergeable.getConflicts();
        assertEquals(3, conflicts.size());
        assertEquals("Repository.java", conflicts.get(0).getFilePath());
        assertEquals("Config.java", conflicts.get(1).getFilePath());
        assertEquals("Database.java", conflicts.get(2).getFilePath());
    }

    @ParameterizedTest
    @ValueSource(strings = {"", " ", "\t", "\n"})
    void shouldRejectInvalidVersions(String invalidVersion) {
        assertThrows(VCSException.class, () -> mergeable.merge(invalidVersion, "release"));
        assertThrows(VCSException.class, () -> mergeable.merge("dev", invalidVersion));
    }

    @Test
    void shouldRejectNullVersions() {
        assertThrows(VCSException.class, () -> mergeable.merge(null, "release"));
        assertThrows(VCSException.class, () -> mergeable.merge("dev", null));
        assertThrows(VCSException.class, () -> mergeable.merge(null, null));
    }

    @Test
    void shouldThrowExceptionForNonexistentVersions() {
        mergeable.setMergeScenario(MergeScenario.NONEXISTENT_VERSION);
        assertThrows(VCSException.class, () -> mergeable.merge("missing", "release"));
    }

    @Test
    void shouldResolveConflictWithSourceStrategy() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.WITH_CONFLICTS);
        mergeable.merge("dev", "release");

        ConflictResolution resolution = new ConflictResolution(
                "Controller.java",
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_SOURCE
        );

        mergeable.resolveConflict("Controller.java", resolution);
        assertTrue(isConflictResolved("Controller.java"));
    }

    @Test
    void shouldResolveConflictWithTargetStrategy() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.WITH_CONFLICTS);
        mergeable.merge("dev", "release");

        ConflictResolution resolution = new ConflictResolution(
                "Controller.java",
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_TARGET
        );

        mergeable.resolveConflict("Controller.java", resolution);
        assertTrue(isConflictResolved("Controller.java"));
    }

    @Test
    void shouldResolveConflictWithCustomStrategy() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.WITH_CONFLICTS);
        mergeable.merge("dev", "release");

        Map<Integer, String> customResolutions = new HashMap<>();
        customResolutions.put(1, "resolved line 1");
        customResolutions.put(2, "resolved line 2");

        ConflictResolution resolution = new ConflictResolution(
                "Controller.java",
                customResolutions,
                ConflictResolution.ResolutionStrategy.CUSTOM
        );

        mergeable.resolveConflict("Controller.java", resolution);
        assertTrue(isConflictResolved("Controller.java"));
    }

    @Test
    void shouldThrowExceptionForNonexistentFile() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.WITH_CONFLICTS);
        mergeable.merge("dev", "release");

        ConflictResolution resolution = new ConflictResolution(
                "unknown.java",
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_SOURCE
        );

        assertThrows(VCSException.class, () ->
                mergeable.resolveConflict("unknown.java", resolution));
    }

    @Test
    void shouldThrowExceptionWhenNoConflictsExist() throws VCSException {
        mergeable.setMergeScenario(MergeScenario.NO_CONFLICTS);
        mergeable.merge("dev", "release");

        ConflictResolution resolution = new ConflictResolution(
                "Controller.java",
                new HashMap<>(),
                ConflictResolution.ResolutionStrategy.KEEP_SOURCE
        );

        assertThrows(VCSException.class, () ->
                mergeable.resolveConflict("Controller.java", resolution));
    }

    @Test
    void shouldRejectNullResolution() {
        assertThrows(VCSException.class, () ->
                mergeable.resolveConflict("Controller.java", null));
    }

    @Test
    void shouldRejectInvalidFilePath() {
        ConflictResolution resolution = new ConflictResolution(
                "Controller.java",
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
                createConflicts("Controller.java", 2);
                return false;
            } else if (scenario == MergeScenario.MULTIPLE_FILES) {
                createConflicts("Repository.java", 1);
                createConflicts("Config.java", 1);
                createConflicts("Database.java", 1);
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
                        "dev content " + i,
                        "release content " + i
                ));
            }

            conflicts.add(new ConflictInfo(filePath, "sha1", "sha2", blocks));
        }
    }
}
