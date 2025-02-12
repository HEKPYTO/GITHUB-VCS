package test.build.model;

import model.DiffResult;
import model.ChangedLines;
import model.LineChange;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DiffResultTest {
    private DiffResult diffResult;
    private Map<String, ChangedLines> changes;
    private static final String OLD_VERSION = "v1.0";
    private static final String NEW_VERSION = "v2.0";

    @BeforeEach
    void setUp() {
        changes = new HashMap<>();
        List<LineChange> additions = Arrays.asList(
                new LineChange(1, null, "new line 1", LineChange.ChangeType.ADDITION),
                new LineChange(3, null, "new line 2", LineChange.ChangeType.ADDITION)
        );
        List<LineChange> deletions = Arrays.asList(
                new LineChange(2, "old line 1", null, LineChange.ChangeType.DELETION),
                new LineChange(4, "old line 2", null, LineChange.ChangeType.DELETION)
        );
        List<LineChange> modifications = List.of(
                new LineChange(5, "old content", "new content", LineChange.ChangeType.MODIFICATION)
        );
        changes.put("file1.txt", new ChangedLines(additions, deletions, modifications));
        diffResult = new DiffResult(OLD_VERSION, NEW_VERSION, changes);
    }

    @Test
    void testConstructorAndGetters() {
        assertEquals(OLD_VERSION, diffResult.oldVersion());
        assertEquals(NEW_VERSION, diffResult.newVersion());
        assertNotNull(diffResult.changes());
        assertEquals(1, diffResult.changes().size());
        assertTrue(diffResult.changes().containsKey("file1.txt"));
    }

    @Test
    void testChangesImmutability() {
        Map<String, ChangedLines> retrievedChanges = diffResult.changes();
        assertThrows(UnsupportedOperationException.class,
                () -> retrievedChanges.put("newfile.txt", new ChangedLines(
                        new ArrayList<>(), new ArrayList<>(), new ArrayList<>())));
        assertThrows(UnsupportedOperationException.class,
                () -> retrievedChanges.remove("file1.txt"));
        assertThrows(UnsupportedOperationException.class, retrievedChanges::clear);
    }

    @Test
    void testConstructorDefensiveCopy() {
        Map<String, ChangedLines> originalChanges = new HashMap<>(changes);
        DiffResult result = new DiffResult(OLD_VERSION, NEW_VERSION, changes);
        changes.clear();

        assertFalse(result.changes().isEmpty());
        assertEquals(originalChanges.size(), result.changes().size());
    }

    @Test
    void testEmptyChanges() {
        DiffResult emptyDiff = new DiffResult(OLD_VERSION, NEW_VERSION, new HashMap<>());
        assertTrue(emptyDiff.changes().isEmpty());
        assertFalse(emptyDiff.hasChanges());
        assertEquals(0, emptyDiff.getTotalChanges());
    }

    @Test
    void testLargeNumberOfChanges() {
        Map<String, ChangedLines> largeChanges = new HashMap<>();
        for (int i = 0; i < 1000; i++) {
            List<LineChange> additions = Collections.singletonList(
                    new LineChange(i, null, "new line " + i, LineChange.ChangeType.ADDITION));
            largeChanges.put("file" + i + ".txt",
                    new ChangedLines(additions, new ArrayList<>(), new ArrayList<>()));
        }

        DiffResult largeDiff = new DiffResult(OLD_VERSION, NEW_VERSION, largeChanges);
        assertEquals(1000, largeDiff.changes().size());
        assertEquals(1000, largeDiff.getTotalChanges());
    }

    @Test
    void testGetTotalChanges() {
        assertEquals(5, diffResult.getTotalChanges());

        Map<String, ChangedLines> multiFileChanges = new HashMap<>(changes);
        multiFileChanges.put("file2.txt", new ChangedLines(
                Collections.singletonList(new LineChange(1, null, "new", LineChange.ChangeType.ADDITION)),
                Collections.emptyList(),
                Collections.emptyList()
        ));

        DiffResult multiFileDiff = new DiffResult(OLD_VERSION, NEW_VERSION, multiFileChanges);
        assertEquals(6, multiFileDiff.getTotalChanges());
    }

    @Test
    void testToString() {
        String expectedString = String.format("DiffResult{oldVersion='%s', newVersion='%s', changedFiles=%d}",
                OLD_VERSION, NEW_VERSION, changes.size());
        assertEquals(expectedString, diffResult.toString());
    }

    @Test
    void testConcurrentAccess() {
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Map<String, ChangedLines> concurrentChanges = diffResult.changes();
                    assertEquals(1, concurrentChanges.size());
                    assertTrue(concurrentChanges.containsKey("file1.txt"));
                    ChangedLines fileChanges = concurrentChanges.get("file1.txt");
                    assertEquals(2, fileChanges.getAdditions().size());
                    assertEquals(2, fileChanges.getDeletions().size());
                    assertEquals(1, fileChanges.getModifications().size());
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
        } catch (InterruptedException e) {
            fail("Concurrent test interrupted");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testNullVersions() {
        assertDoesNotThrow(() -> new DiffResult(null, null, changes));
        DiffResult nullVersions = new DiffResult(null, null, changes);
        assertNull(nullVersions.oldVersion());
        assertNull(nullVersions.newVersion());
        assertEquals(changes.size(), nullVersions.changes().size());
    }

    @Test
    void testHashMapWithNullValues() {
        Map<String, ChangedLines> changesWithNull = new HashMap<>();
        changesWithNull.put("file1.txt", null);
        DiffResult diffWithNull = new DiffResult(OLD_VERSION, NEW_VERSION, changesWithNull);
        assertNotNull(diffWithNull.changes());
        assertTrue(diffWithNull.changes().containsKey("file1.txt"));
        assertNull(diffWithNull.changes().get("file1.txt"));
    }

    @ParameterizedTest
    @MethodSource("provideVersionPairs")
    void testDifferentVersionFormats(String oldVer, String newVer) {
        DiffResult diff = new DiffResult(oldVer, newVer, changes);
        assertEquals(oldVer, diff.oldVersion());
        assertEquals(newVer, diff.newVersion());
        assertEquals(changes.size(), diff.changes().size());
    }

    private static Stream<Arguments> provideVersionPairs() {
        return Stream.of(
                Arguments.of("1.0.0", "1.0.1"),
                Arguments.of("", ""),
                Arguments.of("master", "feature-branch"),
                Arguments.of("abc123", "def456"),
                Arguments.of("v1.0-alpha", "v1.0-beta"),
                Arguments.of("20240101", "20240102")
        );
    }
}