package test.build.model;

import model.FileMetadata;
import model.FileStatus;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class FileMetadataTest {
    private FileMetadata metadata;
    private static final String TEST_FILE_PATH = "/test/path/file.txt";
    private static final String INITIAL_HASH = "abc123";

    @BeforeEach
    void setUp() {
        metadata = new FileMetadata(TEST_FILE_PATH, INITIAL_HASH);
    }

    @Test
    void testConstructorAndInitialState() {
        assertEquals(TEST_FILE_PATH, metadata.getFilePath());
        assertEquals(INITIAL_HASH, metadata.getCurrentHash());
        assertEquals(FileStatus.TRACKED, metadata.getStatus());
        assertTrue(metadata.getVersions().isEmpty());
        assertNotNull(metadata.getLastModified());
        assertTrue(ChronoUnit.SECONDS.between(metadata.getLastModified(), LocalDateTime.now()) < 5);
    }

    @Test
    void testSetCurrentHash() {
        String newHash = "def456";
        LocalDateTime beforeUpdate = metadata.getLastModified();
        try {
            Thread.sleep(10); // Ensure time difference
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        metadata.setCurrentHash(newHash);

        assertEquals(newHash, metadata.getCurrentHash());
        assertEquals(FileStatus.MODIFIED, metadata.getStatus());
        assertTrue(metadata.getLastModified().isAfter(beforeUpdate));
    }

    @Test
    void testSetCurrentHashSameValue() {
        LocalDateTime beforeUpdate = metadata.getLastModified();
        try {
            Thread.sleep(10); // Ensure time difference
        } catch (InterruptedException e) {
            fail("Test interrupted");
        }

        metadata.setCurrentHash(INITIAL_HASH);

        assertEquals(INITIAL_HASH, metadata.getCurrentHash());
        assertEquals(FileStatus.TRACKED, metadata.getStatus());
        assertTrue(metadata.getLastModified().isAfter(beforeUpdate));
    }

    @Test
    void testVersionHandling() {
        List<String> versions = List.of("v1", "v2", "v3");
        for (String version : versions) {
            metadata.addVersion(version);
        }

        List<String> retrievedVersions = metadata.getVersions();
        assertEquals(versions.size(), retrievedVersions.size());
        assertTrue(retrievedVersions.containsAll(versions));
    }

    @Test
    void testVersionListModification() {
        metadata.addVersion("v1");
        List<String> versions = metadata.getVersions();

        // The list returned by getVersions() is a new ArrayList, so these operations should work
        versions.add("v2");
        versions.clear();

        // Original metadata's versions should remain unchanged
        assertEquals(1, metadata.getVersions().size());
        assertEquals("v1", metadata.getVersions().get(0));
    }

    @ParameterizedTest
    @EnumSource(FileStatus.class)
    void testSetStatus(FileStatus status) {
        metadata.setStatus(status);
        assertEquals(status, metadata.getStatus());
    }

    @Test
    void testLargeNumberOfVersions() {
        int versionCount = 10000;
        for (int i = 0; i < versionCount; i++) {
            metadata.addVersion("v" + i);
        }

        assertEquals(versionCount, metadata.getVersions().size());
        assertTrue(metadata.getVersions().contains("v0"));
        assertTrue(metadata.getVersions().contains("v" + (versionCount - 1)));
    }

    @Test
    void testConcurrentAccess() {
        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final String version = "v" + i;
            executor.submit(() -> {
                try {
                    startLatch.await(); // Wait for all threads to be ready
                    metadata.addVersion(version);
                    metadata.getVersions();
                    metadata.getCurrentHash();
                    metadata.getLastModified();
                } catch (InterruptedException e) {
                    fail("Thread interrupted");
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown(); // Start all threads simultaneously
        try {
            assertTrue(endLatch.await(5, TimeUnit.SECONDS));
            List<String> versions = metadata.getVersions();
            assertEquals(threadCount, versions.size());
            // Verify all versions are present
            for (int i = 0; i < threadCount; i++) {
                assertTrue(versions.contains("v" + i));
            }
        } catch (InterruptedException e) {
            fail("Test interrupted");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testStatusTransitions() {
        metadata.setStatus(FileStatus.MODIFIED);
        metadata.addVersion("v1");
        assertEquals(FileStatus.TRACKED, metadata.getStatus());

        metadata.setCurrentHash("newHash");
        assertEquals(FileStatus.MODIFIED, metadata.getStatus());

        metadata.addVersion("v2");
        assertEquals(FileStatus.TRACKED, metadata.getStatus());
    }

    @Test
    void testHashUpdateTimestamps() {
        List<LocalDateTime> timestamps = new ArrayList<>();
        timestamps.add(metadata.getLastModified());

        for (int i = 0; i < 5; i++) {
            try {
                Thread.sleep(10); // Ensure time difference
                metadata.setCurrentHash("hash" + i);
                timestamps.add(metadata.getLastModified());
            } catch (InterruptedException e) {
                fail("Test interrupted");
            }
        }

        for (int i = 1; i < timestamps.size(); i++) {
            assertTrue(timestamps.get(i).isAfter(timestamps.get(i - 1)));
        }
    }

    @ParameterizedTest
    @MethodSource("provideSpecialFilePaths")
    void testSpecialFilePaths(String filePath) {
        FileMetadata specialMetadata = new FileMetadata(filePath, INITIAL_HASH);
        assertEquals(filePath, specialMetadata.getFilePath());
        assertEquals(INITIAL_HASH, specialMetadata.getCurrentHash());
    }

    private static Stream<Arguments> provideSpecialFilePaths() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of(" "),
                Arguments.of("C:\\Windows\\Path\\File.txt"),
                Arguments.of("/usr/local/bin/file"),
                Arguments.of("./relative/path/file"),
                Arguments.of("../parent/path/file"),
                Arguments.of("file:with:colons"),
                Arguments.of("file with spaces.txt"),
                Arguments.of("filewith汉字.txt"),
                Arguments.of("fileWith😊emoji.txt")
        );
    }

    @Test
    void testNullHandling() {
        assertDoesNotThrow(() -> new FileMetadata(null, INITIAL_HASH));
        assertDoesNotThrow(() -> new FileMetadata(TEST_FILE_PATH, null));

        FileMetadata nullMetadata = new FileMetadata(null, null);
        assertNull(nullMetadata.getFilePath());
        assertNull(nullMetadata.getCurrentHash());
    }

    @Test
    void testVersionOrderPreservation() {
        List<String> versions = List.of("v1", "v2", "v3", "v4", "v5");
        for (String version : versions) {
            metadata.addVersion(version);
        }

        List<String> retrievedVersions = metadata.getVersions();
        for (int i = 0; i < versions.size(); i++) {
            assertEquals(versions.get(i), retrievedVersions.get(i));
        }
    }

    @Test
    void testRapidStatusChanges() {
        FileStatus[] statuses = FileStatus.values();
        for (int i = 0; i < 1000; i++) {
            metadata.setStatus(statuses[i % statuses.length]);
        }
        assertEquals(statuses[(999) % statuses.length], metadata.getStatus());
    }
}