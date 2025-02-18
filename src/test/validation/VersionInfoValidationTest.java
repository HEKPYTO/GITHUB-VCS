package test.validation;

import model.VersionInfo;
import org.junit.jupiter.api.*;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.*;
import java.io.*;
import java.time.*;
import java.time.temporal.ChronoUnit;
import java.util.*;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class VersionInfoValidationTest {
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

        returnedHashes.put("newFile.txt", "newHash");
        returnedHashes.remove("file1.txt");

        Map<String, String> secondGet = versionInfo.getFileHashes();
        assertEquals(originalHashes, secondGet);
        assertNotSame(returnedHashes, secondGet);
    }

    @Test
    void testConstructorDefensiveCopy() {
        testFileHashes.put("newFile.txt", "newHash");
        Map<String, String> versionHashes = versionInfo.getFileHashes();
        assertEquals(2, versionHashes.size());
        assertTrue(versionHashes.containsKey("file1.txt"));
        assertTrue(versionHashes.containsKey("file2.txt"));
    }

    @Test
    void testSerialization() throws IOException, ClassNotFoundException {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ObjectOutputStream oos = new ObjectOutputStream(baos)) {
            oos.writeObject(versionInfo);
        }

        ByteArrayInputStream bais = new ByteArrayInputStream(baos.toByteArray());
        VersionInfo deserializedVersion;
        try (ObjectInputStream ois = new ObjectInputStream(bais)) {
            deserializedVersion = (VersionInfo) ois.readObject();
        }

        assertEquals(versionInfo.getVersionId(), deserializedVersion.getVersionId());
        assertEquals(versionInfo.getMessage(), deserializedVersion.getMessage());
        assertEquals(versionInfo.getAuthor(), deserializedVersion.getAuthor());
        assertEquals(versionInfo.getTimestamp(), deserializedVersion.getTimestamp());
        assertEquals(versionInfo.getFileHashes(), deserializedVersion.getFileHashes());
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
                Arguments.of((Object) "Test message"),
                Arguments.of((Object) ""),
                Arguments.of((Object) null),
                Arguments.of("Multi\nLine\nMessage"),
                Arguments.of((Object) "Special chars: !@#$%^&*()"),
                Arguments.of("Message with emoji: 😊"),
                Arguments.of((Object) "Unicode: 英特納雄耐爾"),
                Arguments.of((Object) "Long message: " + "a".repeat(1000))
        );
    }
}