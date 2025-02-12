package test.build.interfaces;

import interfaces.Versionable;
import model.VersionInfo;
import exceptions.VCSException;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class VersionableTest {
    private TestVersionable versionable;

    @BeforeEach
    void setUp() {
        versionable = new TestVersionable();
    }

    @Nested
    class VersionCreationTest {
        @Test
        void shouldCreateInitialVersion() throws VCSException {
            String versionId = versionable.createVersion("Initial commit");

            assertNotNull(versionId);
            VersionInfo version = versionable.getCurrentVersion();
            assertEquals(versionId, version.getVersionId());
            assertEquals("Initial commit", version.getMessage());
            assertEquals("testUser", version.getAuthor());
        }

        @Test
        void shouldCreateMultipleVersions() throws VCSException {
            String version1 = versionable.createVersion("First version");
            String version2 = versionable.createVersion("Second version");

            List<VersionInfo> history = versionable.getVersionHistory();
            assertEquals(2, history.size());
            assertEquals(version1, history.get(0).getVersionId());
            assertEquals(version2, history.get(1).getVersionId());
        }

        @Test
        void shouldRejectNullMessage() {
            assertThrows(VCSException.class, () ->
                    versionable.createVersion(null));
        }

        @ParameterizedTest
        @ValueSource(strings = {"", " ", "\t", "\n"})
        void shouldRejectEmptyMessage(String message) {
            assertThrows(VCSException.class, () ->
                    versionable.createVersion(message));
        }
    }

    @Nested
    class VersionRevertTest {
        @Test
        void shouldRevertToPreviousVersion() throws VCSException {
            String version1 = versionable.createVersion("First version");
            versionable.createVersion("Second version");

            versionable.revertToVersion(version1);
            assertEquals(version1, versionable.getCurrentVersion().getVersionId());
            assertEquals(1, versionable.getVersionHistory().size());
        }

        @Test
        void shouldRejectRevertToNonexistentVersion() {
            assertThrows(VCSException.class, () ->
                    versionable.revertToVersion("nonexistent"));
        }

        @Test
        void shouldRejectRevertToNullVersion() {
            assertThrows(VCSException.class, () ->
                    versionable.revertToVersion(null));
        }
    }

    @Nested
    class VersionHistoryTest {
        @Test
        void shouldMaintainVersionOrder() throws VCSException {
            List<String> messages = Arrays.asList(
                    "First commit",
                    "Second commit",
                    "Third commit"
            );

            for (String message : messages) {
                versionable.createVersion(message);
            }

            List<VersionInfo> history = versionable.getVersionHistory();
            assertEquals(messages.size(), history.size());

            for (int i = 0; i < messages.size(); i++) {
                assertEquals(messages.get(i), history.get(i).getMessage());
            }
        }

        @Test
        void shouldReturnEmptyHistoryInitially() {
            assertTrue(versionable.getVersionHistory().isEmpty());
            assertNull(versionable.getCurrentVersion());
        }

        @Test
        void shouldReturnImmutableHistory() throws VCSException {
            versionable.createVersion("Test version");
            List<VersionInfo> history = versionable.getVersionHistory();
            assertThrows(UnsupportedOperationException.class, () ->
                    history.add(new VersionInfo("message", "author", new HashMap<>())));
        }
    }

    @Nested
    class VersionFilterTest {
        @Test
        void shouldFindVersionByPredicate() throws VCSException {
            String targetMessage = "Target version";
            versionable.createVersion("First version");
            String targetId = versionable.createVersion(targetMessage);
            versionable.createVersion("Third version");

            Optional<VersionInfo> found = versionable.findVersion(v ->
                    v.getMessage().equals(targetMessage));

            assertTrue(found.isPresent());
            assertEquals(targetId, found.get().getVersionId());
        }

        @Test
        void shouldReturnEmptyOptionalWhenNoMatch() throws VCSException {
            versionable.createVersion("Test version");
            Optional<VersionInfo> result = versionable.findVersion(v ->
                    v.getMessage().equals("nonexistent"));

            assertTrue(result.isEmpty());
        }

        @Test
        void shouldFindVersionsByAuthor() throws VCSException {
            versionable.createVersion("First version");
            versionable.createVersion("Second version");

            List<VersionInfo> authorVersions = versionable.getVersionsByAuthor("testUser");
            assertEquals(2, authorVersions.size());
            assertTrue(authorVersions.stream()
                    .allMatch(v -> v.getAuthor().equals("testUser")));
        }

        @Test
        void shouldReturnEmptyListForUnknownAuthor() {
            List<VersionInfo> result = versionable.getVersionsByAuthor("unknown");
            assertTrue(result.isEmpty());
        }
    }

    private static class TestVersionable implements Versionable {
        private final List<VersionInfo> versions = new ArrayList<>();

        @Override
        public String createVersion(String message) throws VCSException {
            if (message == null || message.trim().isEmpty()) {
                throw new VCSException("Version message cannot be empty");
            }

            VersionInfo version = new VersionInfo(message, "testUser", new HashMap<>());
            versions.add(version);
            return version.getVersionId();
        }

        @Override
        public void revertToVersion(String versionId) throws VCSException {
            if (versionId == null) {
                throw new VCSException("Version ID cannot be null");
            }

            int index = findVersionIndex(versionId);
            if (index == -1) {
                throw new VCSException("Version not found: " + versionId);
            }

            while (versions.size() > index + 1) {
                versions.removeLast();
            }
        }

        @Override
        public List<VersionInfo> getVersionHistory() {
            return Collections.unmodifiableList(versions);
        }

        @Override
        public VersionInfo getCurrentVersion() {
            return versions.isEmpty() ? null : versions.getLast();
        }

        private int findVersionIndex(String versionId) {
            for (int i = 0; i < versions.size(); i++) {
                if (versions.get(i).getVersionId().equals(versionId)) {
                    return i;
                }
            }
            return -1;
        }
    }
}
