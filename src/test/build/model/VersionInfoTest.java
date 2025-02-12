package test.build.model;

import model.VersionInfo;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;

import java.io.*;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class VersionInfoTest {
    private VersionInfo versionInfo;
    private static final String TEST_MESSAGE = "Test commit message";
    private static final String TEST_AUTHOR = "Test Author";
    private Map<String, String> testFileHashes;

    @BeforeEach
    void setUp() {
        testFileHashes = new HashMap<>();
        testFileHashes.put("file1.txt", "hash1");
        testFileHashes.put("file2.txt", "hash2");
        versionInfo = new VersionInfo(TEST_MESSAGE, TEST_AUTHOR, testFileHashes);
    }

    @Test
    void testConstructorAndBasicGetters() {
        assertNotNull(versionInfo.getVersionId());
        assertEquals(TEST_MESSAGE, versionInfo.getMessage());
        assertEquals(TEST_AUTHOR, versionInfo.getAuthor());
        assertNotNull(versionInfo.getTimestamp());
        assertTrue(ChronoUnit.SECONDS.between(versionInfo.getTimestamp(), LocalDateTime.now()) < 5);
    }

    @Test
    void testVersionIdGeneration() {
        Set<String> versionIds = new HashSet<>();
        for (int i = 0; i < 1000; i++) {
            String versionId = new VersionInfo(TEST_MESSAGE, TEST_AUTHOR, testFileHashes).getVersionId();
            assertTrue(versionIds.add(versionId), "Version ID collision detected");
        }
        assertEquals(1000, versionIds.size());
    }

    @Test
    void testFileHashesDefensiveCopy() {
        Map<String, String> returnedHashes = versionInfo.getFileHashes();
        Map<String, String> originalHashes = new HashMap<>(returnedHashes);

        // Modify the returned map
        returnedHashes.put("newFile.txt", "newHash");

        // Verify original map is unchanged
        Map<String, String> secondGet = versionInfo.getFileHashes();
        assertEquals(originalHashes, secondGet);
        assertNotSame(returnedHashes, secondGet);
    }

    @Test
    void testConstructorDefensiveCopy() {
        // Modify original map after construction
        testFileHashes.put("newFile.txt", "newHash");

        // Verify version info's map is unchanged
        Map<String, String> versionHashes = versionInfo.getFileHashes();
        assertFalse(versionHashes.containsKey("newFile.txt"));
        assertEquals(2, versionHashes.size());
    }

    @Test
    void testSerialization() throws IOException, ClassNotFoundException {
        // Serialize
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(versionInfo);
        }

        // Deserialize
        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        VersionInfo deserializedVersion;
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            deserializedVersion = (VersionInfo) ois.readObject();
        }

        // Verify
        assertEquals(versionInfo.getVersionId(), deserializedVersion.getVersionId());
        assertEquals(versionInfo.getMessage(), deserializedVersion.getMessage());
        assertEquals(versionInfo.getAuthor(), deserializedVersion.getAuthor());
        assertEquals(versionInfo.getFileHashes(), deserializedVersion.getFileHashes());
        assertEquals(versionInfo.getTimestamp(), deserializedVersion.getTimestamp());
    }

    @Test
    void testToString() {
        String toString = versionInfo.toString();
        assertTrue(toString.contains(versionInfo.getVersionId()));
        assertTrue(toString.contains(TEST_MESSAGE));
        assertTrue(toString.contains(TEST_AUTHOR));
        assertTrue(toString.contains(String.valueOf(testFileHashes.size())));
    }

    @ParameterizedTest
    @MethodSource("provideSpecialMessages")
    void testSpecialMessages(String message) {
        VersionInfo version = new VersionInfo(message, TEST_AUTHOR, testFileHashes);
        assertEquals(message, version.getMessage());
    }

    private static Stream<Arguments> provideSpecialMessages() {
        return Stream.of(
                Arguments.of(""),
                Arguments.of(" "),
                Arguments.of("Multi\nLine\nMessage"),
                Arguments.of("Message with unicode: 你好"),
                Arguments.of("Message with emoji: 😊"),
                Arguments.of("Message with special chars: !@#$%^&*()"),
                Arguments.of("Very long message " + "a".repeat(1000))
        );
    }

    @Test
    void testConcurrentAccess() {
        int threadCount = 100;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);
        Set<String> versionIds = Collections.synchronizedSet(new HashSet<>());

        for (int i = 0; i < threadCount; i++) {
            executor.submit(() -> {
                try {
                    startLatch.await();
                    VersionInfo version = new VersionInfo(TEST_MESSAGE, TEST_AUTHOR, testFileHashes);
                    versionIds.add(version.getVersionId());
                    // Access all fields concurrently
                    version.getMessage();
                    version.getAuthor();
                    version.getTimestamp();
                    version.getFileHashes();
                } catch (InterruptedException e) {
                    fail("Thread interrupted");
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        try {
            assertTrue(endLatch.await(5, TimeUnit.SECONDS));
            assertEquals(threadCount, versionIds.size());
        } catch (InterruptedException e) {
            fail("Test interrupted");
        } finally {
            executor.shutdown();
        }
    }

    @Test
    void testNullHandling() {
        assertDoesNotThrow(() -> new VersionInfo(null, TEST_AUTHOR, new HashMap<>()));
        assertDoesNotThrow(() -> new VersionInfo(TEST_MESSAGE, null, new HashMap<>()));

        VersionInfo nullVersion = new VersionInfo(null, null, new HashMap<>());
        assertNotNull(nullVersion.getVersionId());
        assertNull(nullVersion.getMessage());
        assertNull(nullVersion.getAuthor());
        assertNotNull(nullVersion.getFileHashes());
        assertTrue(nullVersion.getFileHashes().isEmpty());
    }

    @Test
    void testEmptyFileHashes() {
        VersionInfo emptyVersion = new VersionInfo(TEST_MESSAGE, TEST_AUTHOR, new HashMap<>());
        assertTrue(emptyVersion.getFileHashes().isEmpty());
    }

    @Test
    void testLargeFileHashes() {
        Map<String, String> largeMap = new HashMap<>();
        for (int i = 0; i < 10000; i++) {
            largeMap.put("file" + i + ".txt", "hash" + i);
        }

        VersionInfo largeVersion = new VersionInfo(TEST_MESSAGE, TEST_AUTHOR, largeMap);
        assertEquals(10000, largeVersion.getFileHashes().size());
    }

    @Test
    void testFileHashesWithSpecialPaths() {
        Map<String, String> specialPaths = new HashMap<>();
        specialPaths.put("C:\\Windows\\Path\\File.txt", "hash1");
        specialPaths.put("/usr/local/bin/file", "hash2");
        specialPaths.put("./relative/path/file", "hash3");
        specialPaths.put("file with spaces.txt", "hash4");
        specialPaths.put("file\\with\\backslashes.txt", "hash5");

        VersionInfo specialVersion = new VersionInfo(TEST_MESSAGE, TEST_AUTHOR, specialPaths);
        Map<String, String> retrievedHashes = specialVersion.getFileHashes();
        assertEquals(specialPaths.size(), retrievedHashes.size());
        for (Map.Entry<String, String> entry : specialPaths.entrySet()) {
            assertEquals(entry.getValue(), retrievedHashes.get(entry.getKey()));
        }
    }
}