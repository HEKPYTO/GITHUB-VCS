package test.built.model;

import model.ConflictResolution;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.*;

class ConflictResolutionTest {
    private ConflictResolution resolution;
    private Map<Integer, String> resolvedLines;
    private static final String TEST_FILE = "test.txt";

    @BeforeEach
    void setUp() {
        resolvedLines = new HashMap<>();
        for (int i = 1; i <= 5; i++) {
            resolvedLines.put(i, "resolved line " + i);
        }
        resolution = new ConflictResolution(TEST_FILE, resolvedLines, ConflictResolution.ResolutionStrategy.CUSTOM);
    }

    @Test
    void testConstructorAndBasicGetters() {
        assertEquals(TEST_FILE, resolution.filePath());
        assertEquals(ConflictResolution.ResolutionStrategy.CUSTOM, resolution.strategy());
        assertEquals(5, resolution.resolvedLines().size());
        for (int i = 1; i <= 5; i++) {
            assertEquals("resolved line " + i, resolution.resolvedLines().get(i));
        }
    }

    @ParameterizedTest
    @EnumSource(ConflictResolution.ResolutionStrategy.class)
    void testAllResolutionStrategies(ConflictResolution.ResolutionStrategy strategy) {
        ConflictResolution res = new ConflictResolution(TEST_FILE, resolvedLines, strategy);
        assertEquals(strategy, res.strategy());
    }

    @Test
    void testLargeResolutionSet() {
        Map<Integer, String> largeMap = new HashMap<>();
        for (int i = 1; i <= 10000; i++) {
            largeMap.put(i, "content " + i);
        }
        ConflictResolution largeResolution = new ConflictResolution(
                TEST_FILE, largeMap, ConflictResolution.ResolutionStrategy.CUSTOM
        );
        assertEquals(10000, largeResolution.resolvedLines().size());
    }

    @Test
    void testMapImmutability() {
        Map<Integer, String> retrievedLines = resolution.resolvedLines();
        assertThrows(UnsupportedOperationException.class,
                () -> retrievedLines.put(6, "new line"));
        assertThrows(UnsupportedOperationException.class,
                () -> retrievedLines.remove(1));
        assertThrows(UnsupportedOperationException.class,
                retrievedLines::clear);
    }

    @Test
    void testNullHandling() {
        assertThrows(NullPointerException.class, () ->
                new ConflictResolution(null, new HashMap<>(),
                        ConflictResolution.ResolutionStrategy.CUSTOM));

        assertThrows(NullPointerException.class, () ->
                new ConflictResolution("test.txt", null,
                        ConflictResolution.ResolutionStrategy.CUSTOM));

        assertThrows(NullPointerException.class, () ->
                new ConflictResolution("test.txt", new HashMap<>(), null));
    }

    @Test
    void testConcurrentAccess() {
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    Map<Integer, String> lines = resolution.resolvedLines();
                    assertEquals(5, lines.size());
                    for (int j = 1; j <= 5; j++) {
                        assertEquals("resolved line " + j, lines.get(j));
                    }
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
    void testEmptyResolution() {
        ConflictResolution emptyResolution = new ConflictResolution(
                TEST_FILE, new HashMap<>(), ConflictResolution.ResolutionStrategy.KEEP_SOURCE
        );
        assertTrue(emptyResolution.resolvedLines().isEmpty());
    }
}