package test.model;

import model.ChangedLines;
import model.LineChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.provider.Arguments;

import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class ChangedLinesTest {
    private ChangedLines changedLines;

    @BeforeEach
    void setUp() {
        List<LineChange> additions = new ArrayList<>(Arrays.asList(
                new LineChange(1, null, "new line 1", LineChange.ChangeType.ADDITION),
                new LineChange(3, null, "new line 2", LineChange.ChangeType.ADDITION)
        ));
        List<LineChange> deletions = new ArrayList<>(Arrays.asList(
                new LineChange(2, "old line 1", null, LineChange.ChangeType.DELETION),
                new LineChange(4, "old line 2", null, LineChange.ChangeType.DELETION)
        ));
        List<LineChange> modifications = new ArrayList<>(Arrays.asList(
                new LineChange(5, "old content 1", "new content 1", LineChange.ChangeType.MODIFICATION),
                new LineChange(6, "old content 2", "new content 2", LineChange.ChangeType.MODIFICATION)
        ));
        changedLines = new ChangedLines(additions, deletions, modifications);
    }

    @Test
    void testConstructorWithEmptyLists() {
        ChangedLines empty = new ChangedLines(
                new ArrayList<>(),
                new ArrayList<>(),
                new ArrayList<>()
        );
        assertTrue(empty.getAdditions().isEmpty());
        assertTrue(empty.getDeletions().isEmpty());
        assertTrue(empty.getModifications().isEmpty());
    }

    @Test
    void testConstructorWithLargeNumberOfChanges() {
        List<LineChange> largeAdditions = new ArrayList<>();
        List<LineChange> largeDeletions = new ArrayList<>();
        List<LineChange> largeModifications = new ArrayList<>();

        for (int i = 1; i <= 10000; i++) {
            largeAdditions.add(new LineChange(i, null, "new line " + i, LineChange.ChangeType.ADDITION));
            largeDeletions.add(new LineChange(i + 10000, "old line " + i, null, LineChange.ChangeType.DELETION));
            largeModifications.add(new LineChange(i + 20000, "old " + i, "new " + i, LineChange.ChangeType.MODIFICATION));
        }

        ChangedLines large = new ChangedLines(largeAdditions, largeDeletions, largeModifications);
        assertEquals(10000, large.getAdditions().size());
        assertEquals(10000, large.getDeletions().size());
        assertEquals(10000, large.getModifications().size());
    }

    @Test
    void testConcurrentModification() {
        List<LineChange> concurrentAdditions = Collections.synchronizedList(new ArrayList<>());
        List<LineChange> concurrentDeletions = Collections.synchronizedList(new ArrayList<>());
        List<LineChange> concurrentModifications = Collections.synchronizedList(new ArrayList<>());

        CountDownLatch latch = new CountDownLatch(3);
        ExecutorService executor = Executors.newFixedThreadPool(3);

        executor.submit(() -> {
            try {
                for (int i = 1; i <= 1000; i++) {
                    concurrentAdditions.add(new LineChange(i, null, "new " + i, LineChange.ChangeType.ADDITION));
                }
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                for (int i = 1; i <= 1000; i++) {
                    concurrentDeletions.add(new LineChange(i, "old " + i, null, LineChange.ChangeType.DELETION));
                }
            } finally {
                latch.countDown();
            }
        });

        executor.submit(() -> {
            try {
                for (int i = 1; i <= 1000; i++) {
                    concurrentModifications.add(new LineChange(i, "old " + i, "new " + i, LineChange.ChangeType.MODIFICATION));
                }
            } finally {
                latch.countDown();
            }
        });

        try {
            latch.await(5, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            fail("Concurrent operation timed out");
        }

        ChangedLines concurrent = new ChangedLines(concurrentAdditions, concurrentDeletions, concurrentModifications);
        assertEquals(1000, concurrent.getAdditions().size());
        assertEquals(1000, concurrent.getDeletions().size());
        assertEquals(1000, concurrent.getModifications().size());

        executor.shutdown();
    }

    @Test
    void testInvalidChangesCombinations() {
        assertThrows(NullPointerException.class, () ->
                new ChangedLines(null, new ArrayList<>(), new ArrayList<>()));

        assertThrows(NullPointerException.class, () ->
                new ChangedLines(new ArrayList<>(), null, new ArrayList<>()));

        assertThrows(NullPointerException.class, () ->
                new ChangedLines(new ArrayList<>(), new ArrayList<>(), null));
    }

    private static Stream<Arguments> provideInvalidChanges() {
        return Stream.of(
                Arguments.of(null, new ArrayList<>(), new ArrayList<>()),
                Arguments.of(new ArrayList<>(), null, new ArrayList<>()),
                Arguments.of(new ArrayList<>(), new ArrayList<>(), null),
                Arguments.of(
                        List.of(new LineChange(1, "invalid", "new", LineChange.ChangeType.ADDITION)),
                        new ArrayList<>(),
                        new ArrayList<>()
                ),
                Arguments.of(
                        new ArrayList<>(),
                        List.of(new LineChange(1, null, "invalid", LineChange.ChangeType.DELETION)),
                        new ArrayList<>()
                )
        );
    }

    @Test
    void testDefensiveCopyModification() {
        List<LineChange> retrievedAdditions = changedLines.getAdditions();
        List<LineChange> retrievedDeletions = changedLines.getDeletions();
        List<LineChange> retrievedModifications = changedLines.getModifications();

        retrievedAdditions.clear();
        retrievedDeletions.clear();
        retrievedModifications.clear();

        assertEquals(2, changedLines.getAdditions().size());
        assertEquals(2, changedLines.getDeletions().size());
        assertEquals(2, changedLines.getModifications().size());
    }

    @Test
    void testOrderPreservation() {
        List<LineChange> retrievedAdditions = changedLines.getAdditions();
        assertEquals(1, retrievedAdditions.get(0).lineNumber());
        assertEquals(3, retrievedAdditions.get(1).lineNumber());

        List<LineChange> retrievedDeletions = changedLines.getDeletions();
        assertEquals(2, retrievedDeletions.get(0).lineNumber());
        assertEquals(4, retrievedDeletions.get(1).lineNumber());
    }
}
