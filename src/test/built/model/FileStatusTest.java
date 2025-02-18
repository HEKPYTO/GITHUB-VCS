package test.built.model;

import model.FileStatus;
import org.junit.jupiter.api.Test;
import java.util.*;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

class FileStatusTest {
    @Test
    void testEnumValues() {
        FileStatus[] statuses = FileStatus.values();
        assertEquals(6, statuses.length);

        Set<FileStatus> statusSet = EnumSet.allOf(FileStatus.class);
        assertTrue(statusSet.contains(FileStatus.UNTRACKED));
        assertTrue(statusSet.contains(FileStatus.TRACKED));
        assertTrue(statusSet.contains(FileStatus.MODIFIED));
        assertTrue(statusSet.contains(FileStatus.DELETED));
        assertTrue(statusSet.contains(FileStatus.STAGED));
        assertTrue(statusSet.contains(FileStatus.CONFLICTED));
    }

    @Test
    void testEnumOrdinals() {
        assertEquals(0, FileStatus.UNTRACKED.ordinal());
        assertEquals(1, FileStatus.TRACKED.ordinal());
        assertEquals(2, FileStatus.MODIFIED.ordinal());
        assertEquals(3, FileStatus.DELETED.ordinal());
        assertEquals(4, FileStatus.STAGED.ordinal());
        assertEquals(5, FileStatus.CONFLICTED.ordinal());
    }

    @Test
    void testValueOf() {
        for (FileStatus status : FileStatus.values()) {
            assertEquals(status, FileStatus.valueOf(status.name()));
        }
    }

    @Test
    void testInvalidValueOf() {
        assertThrows(IllegalArgumentException.class, () -> FileStatus.valueOf("INVALID_STATUS"));
        assertThrows(NullPointerException.class, () -> FileStatus.valueOf(null));
    }

    @Test
    void testToString() {
        for (FileStatus status : FileStatus.values()) {
            assertEquals(status.name(), status.toString());
        }
    }

    @Test
    void testEquality() {
        FileStatus status1 = FileStatus.TRACKED;
        FileStatus status2 = FileStatus.TRACKED;
        FileStatus status3 = FileStatus.MODIFIED;

        assertEquals(status1, status2);
        assertNotEquals(status1, status3);
        assertNotEquals(null, status1);
    }

    @Test
    void testHashCode() {
        FileStatus status1 = FileStatus.TRACKED;
        FileStatus status2 = FileStatus.TRACKED;
        assertEquals(status1.hashCode(), status2.hashCode());
    }

    @Test
    void testEnumSwitch() {
        for (FileStatus status : FileStatus.values()) {
            String result = switch (status) {
                case UNTRACKED -> "untracked";
                case TRACKED -> "tracked";
                case MODIFIED -> "modified";
                case DELETED -> "deleted";
                case STAGED -> "staged";
                case CONFLICTED -> "conflicted";
            };
            assertNotNull(result);
        }
    }

    @Test
    void testConcurrentAccess() {
        int threadCount = 100;
        CountDownLatch latch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        ConcurrentMap<FileStatus, Integer> occurrences = new ConcurrentHashMap<>();

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    FileStatus[] values = FileStatus.values();
                    for (FileStatus status : values) {
                        occurrences.merge(status, 1, Integer::sum);
                    }
                } finally {
                    latch.countDown();
                }
            });
        }

        try {
            assertTrue(latch.await(5, TimeUnit.SECONDS));
            assertEquals(6, occurrences.size());
            for (int count : occurrences.values()) {
                assertEquals(threadCount, count);
            }
        } catch (InterruptedException e) {
            fail("Concurrent test interrupted");
        } finally {
            executor.shutdown();
        }
    }
}