package test.impl;

import impl.DiffGenerator;
import impl.FileTracker;
import impl.VersionManager;
import model.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import utils.HashUtils;

import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.*;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class DiffGeneratorTest {
    private DiffGenerator diffGenerator;
    private VersionManager versionManager;
    private FileTracker fileTracker;
    @TempDir
    Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        Path objectsDir = tempDir.resolve(".vcs").resolve("objects");
        Files.createDirectories(objectsDir);
        versionManager = new VersionManager(tempDir.toString());
        fileTracker = new FileTracker(tempDir.toString());
        diffGenerator = new DiffGenerator(versionManager, fileTracker);
    }

    @Test
    void testBasicDiffBetweenVersions() throws Exception {
        String file1Hash = createAndStoreFile("file1.txt", "initial\ncontent\nhere");
        Map<String, String> initialHashes = Collections.singletonMap("file1.txt", file1Hash);
        String version1 = versionManager.createVersion("Initial commit", initialHashes);

        String file2Hash = createAndStoreFile("file1.txt", "modified\ncontent\nhere");
        Map<String, String> modifiedHashes = Collections.singletonMap("file1.txt", file2Hash);
        String version2 = versionManager.createVersion("Modified commit", modifiedHashes);

        DiffResult diff = diffGenerator.getDiff(version1, version2);
        assertTrue(diff.hasChanges());

        ChangedLines changes = diff.changes().get("file1.txt");
        assertNotNull(changes);
        assertEquals(1, changes.getModifications().size());

        LineChange mod = changes.getModifications().get(0);
        assertEquals("initial", mod.oldContent());
        assertEquals("modified", mod.newContent());
    }

    @Test
    void testComplexFileDiff() throws Exception {
        String originalContent = String.join("\n", Arrays.asList(
                "public class Test {",
                "    private int x;",
                "    private String y;",
                "",
                "    public void method() {",
                "        // TODO",
                "    }",
                "}"
        ));

        String modifiedContent = String.join("\n", Arrays.asList(
                "public class Test {",
                "    private int x;",
                "    private String y;",
                "    private boolean z;",
                "",
                "    public void method() {",
                "        System.out.println(x);",
                "        return;",
                "    }",
                "}"
        ));

        String file1Hash = createAndStoreFile("Test.java", originalContent);
        Map<String, String> initialHashes = Collections.singletonMap("Test.java", file1Hash);
        String version1 = versionManager.createVersion("Initial", initialHashes);

        String file2Hash = createAndStoreFile("Test.java", modifiedContent);
        Map<String, String> modifiedHashes = Collections.singletonMap("Test.java", file2Hash);
        String version2 = versionManager.createVersion("Modified", modifiedHashes);

        DiffResult diff = diffGenerator.getDiff(version1, version2);
        assertTrue(diff.hasChanges());

        ChangedLines changes = diff.changes().get("Test.java");
        assertNotNull(changes);
        assertFalse(changes.getAdditions().isEmpty());
        assertFalse(changes.getModifications().isEmpty());
    }


    @Test
    void testMultipleFileChanges() throws Exception {
        // Initial version with two files
        Map<String, String> initialHashes = new HashMap<>();
        initialHashes.put("file1.txt", createAndStoreFile("file1.txt", "content1"));
        initialHashes.put("file2.txt", createAndStoreFile("file2.txt", "content2"));
        String version1 = versionManager.createVersion("Initial commit", initialHashes);

        // Modified version: change file1, delete file2, add file3
        Map<String, String> modifiedHashes = new HashMap<>();
        modifiedHashes.put("file1.txt", createAndStoreFile("file1.txt", "modified1"));
        modifiedHashes.put("file3.txt", createAndStoreFile("file3.txt", "content3"));
        String version2 = versionManager.createVersion("Multiple changes", modifiedHashes);

        DiffResult diff = diffGenerator.getDiff(version1, version2);
        assertTrue(diff.hasChanges());
        assertEquals(3, diff.changes().size());

        assertTrue(diff.changes().containsKey("file1.txt")); // Modified
        assertTrue(diff.changes().containsKey("file2.txt")); // Deleted
        assertTrue(diff.changes().containsKey("file3.txt")); // Added
    }

    @ParameterizedTest
    @MethodSource("provideSpecialContents")
    void testSpecialContentDiffs(String original, String modified, int expectedChanges) throws Exception {
        String file1Hash = createAndStoreFile("test.txt", original);
        Map<String, String> initialHashes = Collections.singletonMap("test.txt", file1Hash);
        String version1 = versionManager.createVersion("Initial", initialHashes);

        String file2Hash = createAndStoreFile("test.txt", modified);
        Map<String, String> modifiedHashes = Collections.singletonMap("test.txt", file2Hash);
        String version2 = versionManager.createVersion("Modified", modifiedHashes);

        DiffResult diff = diffGenerator.getDiff(version1, version2);
        if (expectedChanges == 0) {
            assertFalse(diff.hasChanges(), "No changes expected, but diff.hasChanges() returned true");
        } else {
            assertTrue(diff.hasChanges(), "Expected changes, but diff.hasChanges() returned false");
        }

        ChangedLines changes = diff.changes().get("test.txt");
        int totalChanges = (changes != null) ?
                changes.getAdditions().size() + changes.getDeletions().size() + changes.getModifications().size() : 0;
        assertEquals(expectedChanges, totalChanges);
    }

    private static Stream<Arguments> provideSpecialContents() {
        return Stream.of(
                Arguments.of("", "new content\n", 1),
                Arguments.of("content\n", "", 1),
                Arguments.of("a\nb\nc", "a\nd\nc", 1),
                Arguments.of("a\nb\nc", "a\nb\nc\nd", 1),
                Arguments.of("a\nb\nc", "x\na\nb\nc", 1),
                Arguments.of("line with spaces  ", "line with spaces", 1),
                Arguments.of("line\nwith\nno\nchange", "line\nwith\nno\nchange", 0),
                Arguments.of("α\nβ\nγ", "α\nδ\nγ", 1),
                Arguments.of("a\nb\nc", "d\ne\nf", 3)
        );
    }

    @Test
    void testLargeFileDiff() throws Exception {
        StringBuilder originalContent = new StringBuilder();
        StringBuilder modifiedContent = new StringBuilder();

        for (int i = 0; i < 1000; i++) {
            originalContent.append("Line ").append(i).append("\n");
            if (i % 10 == 0) {
                modifiedContent.append("Modified line ").append(i).append("\n");
            } else {
                modifiedContent.append("Line ").append(i).append("\n");
            }
        }

        String file1Hash = createAndStoreFile("large.txt", originalContent.toString());
        Map<String, String> initialHashes = Collections.singletonMap("large.txt", file1Hash);
        String version1 = versionManager.createVersion("Initial large file", initialHashes);

        String file2Hash = createAndStoreFile("large.txt", modifiedContent.toString());
        Map<String, String> modifiedHashes = Collections.singletonMap("large.txt", file2Hash);
        String version2 = versionManager.createVersion("Modified large file", modifiedHashes);

        DiffResult diff = diffGenerator.getDiff(version1, version2);
        assertTrue(diff.hasChanges());

        ChangedLines changes = diff.changes().get("large.txt");
        assertNotNull(changes);
        assertEquals(100, changes.getModifications().size());
    }

    @Test
    void testConcurrentDiffOperations() throws Exception {
        int threadCount = 10;
        CountDownLatch startLatch = new CountDownLatch(1);
        CountDownLatch endLatch = new CountDownLatch(threadCount);
        ExecutorService executor = Executors.newFixedThreadPool(threadCount);

        // Create initial version
        String file1Hash = createAndStoreFile("concurrent.txt", "initial content");
        Map<String, String> initialHashes = Collections.singletonMap("concurrent.txt", file1Hash);
        String version1 = versionManager.createVersion("Initial", initialHashes);

        // Create multiple versions with different content
        List<String> versions = new ArrayList<>();
        for (int i = 0; i < threadCount; i++) {
            String fileHash = createAndStoreFile("concurrent.txt", "content version " + i);
            Map<String, String> hashes = Collections.singletonMap("concurrent.txt", fileHash);
            versions.add(versionManager.createVersion("Version " + i, hashes));
        }

        // Perform concurrent diff operations
        List<Exception> exceptions = Collections.synchronizedList(new ArrayList<>());
        for (int i = 0; i < threadCount; i++) {
            final String version = versions.get(i);
            executor.submit(() -> {
                try {
                    startLatch.await();
                    DiffResult diff = diffGenerator.getDiff(version1, version);
                    assertNotNull(diff);
                    assertTrue(diff.hasChanges());
                } catch (Exception e) {
                    exceptions.add(e);
                } finally {
                    endLatch.countDown();
                }
            });
        }

        startLatch.countDown();
        assertTrue(endLatch.await(30, TimeUnit.SECONDS));
        executor.shutdown();
        assertTrue(executor.awaitTermination(5, TimeUnit.SECONDS));
        assertTrue(exceptions.isEmpty(), "Concurrent operations failed: " + exceptions);
    }

    @Test
    void testIncrementalDiffs() throws Exception {
        List<String> versions = new ArrayList<>();
        String previousHash = null;

        // Create 5 incremental versions
        for (int i = 0; i < 5; i++) {
            String content = String.join("\n",
                    "version " + i,
                    previousHash != null ? "previous content" : "",
                    "new content " + i);

            String hash = createAndStoreFile("incremental.txt", content);
            Map<String, String> hashes = Collections.singletonMap("incremental.txt", hash);
            versions.add(versionManager.createVersion("Version " + i, hashes));
            previousHash = hash;
        }

        // Verify diffs between consecutive versions
        for (int i = 1; i < versions.size(); i++) {
            DiffResult diff = diffGenerator.getDiff(versions.get(i-1), versions.get(i));
            assertTrue(diff.hasChanges());

            ChangedLines changes = diff.changes().get("incremental.txt");
            assertNotNull(changes);
            assertTrue(!changes.getModifications().isEmpty() ||
                    !changes.getAdditions().isEmpty() ||
                    !changes.getDeletions().isEmpty());
        }
    }

    private String createAndStoreFile(String name, String content) throws Exception {
        Path filePath = tempDir.resolve(name);
        Files.writeString(filePath, content);
        String hash = HashUtils.calculateFileHash(filePath.toFile());

        Path objectsPath = tempDir.resolve(".vcs").resolve("objects");
        Path objectFile = objectsPath.resolve(hash);
        Files.copy(filePath, objectFile, StandardCopyOption.REPLACE_EXISTING);

        return hash;
    }
}