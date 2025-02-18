package test.built.interfaces;

import interfaces.Uploadable;
import exceptions.VCSException;
import org.junit.jupiter.api.*;

import java.io.File;
import java.nio.file.*;
import java.util.*;
import static org.junit.jupiter.api.Assertions.*;

class UploadableTest {
    private TestUploadable uploadable;
    private Path tempDir;

    @BeforeEach
    void setUp() throws Exception {
        tempDir = Files.createTempDirectory("uploadable_test");
        uploadable = new TestUploadable();
    }

    @AfterEach
    void tearDown() throws Exception {
        if (tempDir != null) {
            Files.walk(tempDir)
                    .sorted(Comparator.reverseOrder())
                    .forEach(path -> {
                        try {
                            Files.delete(path);
                        } catch (Exception e) {
                            e.printStackTrace();
                        }
                    });
        }
    }

    @Nested
    class SingleFileUploadTest {
        @Test
        void shouldUploadValidFile() throws Exception {
            File testFile = createTestFile("test.txt", "content");
            assertTrue(uploadable.upload(testFile));
            assertTrue(uploadable.getUploadedFiles().contains(testFile));
        }

        @Test
        void shouldRejectNullFile() {
            assertThrows(VCSException.class, () -> uploadable.upload(null));
        }

        @Test
        void shouldRejectNonexistentFile() {
            File nonexistentFile = tempDir.resolve("nonexistent.txt").toFile();
            assertThrows(VCSException.class, () -> uploadable.upload(nonexistentFile));
        }

        @Test
        void shouldRejectDirectory() {
            assertThrows(VCSException.class, () -> uploadable.upload(tempDir.toFile()));
        }
    }

    @Nested
    class DirectoryUploadTest {
        @Test
        void shouldUploadAllFilesInDirectory() throws Exception {
            File directory = createDirectory("testDir");
            File file1 = createTestFile(directory, "file1.txt", "content1");
            File file2 = createTestFile(directory, "file2.txt", "content2");

            assertTrue(uploadable.uploadDirectory(directory));
            List<File> uploadedFiles = uploadable.getUploadedFiles();
            assertTrue(uploadedFiles.contains(file1));
            assertTrue(uploadedFiles.contains(file2));
        }

        @Test
        void shouldHandleEmptyDirectory() throws Exception {
            File directory = createDirectory("emptyDir");
            assertTrue(uploadable.uploadDirectory(directory));
            assertTrue(uploadable.getUploadedFiles().isEmpty());
        }

        @Test
        void shouldRejectNullDirectory() {
            assertThrows(VCSException.class, () -> uploadable.uploadDirectory(null));
        }

        @Test
        void shouldRejectNonexistentDirectory() {
            File nonexistentDir = tempDir.resolve("nonexistent").toFile();
            assertThrows(VCSException.class, () -> uploadable.uploadDirectory(nonexistentDir));
        }

        @Test
        void shouldRejectRegularFile() throws Exception {
            File file = createTestFile("test.txt", "content");
            assertThrows(VCSException.class, () -> uploadable.uploadDirectory(file));
        }
    }

    @Nested
    class FileRemovalTest {
        @Test
        void shouldRemoveUploadedFile() throws Exception {
            File testFile = createTestFile("test.txt", "content");
            uploadable.upload(testFile);
            uploadable.removeFile(testFile.getPath());
            assertFalse(uploadable.getUploadedFiles().contains(testFile));
        }

        @Test
        void shouldHandleRemovingNonexistentFile() {
            assertDoesNotThrow(() -> uploadable.removeFile("nonexistent.txt"));
        }

        @Test
        void shouldRejectNullPath() {
            assertThrows(VCSException.class, () -> uploadable.removeFile(null));
        }
    }

    @Nested
    class FileFilterTest {
        @Test
        void shouldFilterUploadedFiles() throws Exception {
            File txtFile = createTestFile("test.txt", "content");
            File xmlFile = createTestFile("test.xml", "content");
            uploadable.upload(txtFile);
            uploadable.upload(xmlFile);

            List<File> txtFiles = uploadable.getUploadedFilesByFilter(
                    file -> file.getName().endsWith(".txt")
            );

            assertEquals(1, txtFiles.size());
            assertTrue(txtFiles.contains(txtFile));
        }

        @Test
        void shouldReturnEmptyListForNoMatches() throws Exception {
            File txtFile = createTestFile("test.txt", "content");
            uploadable.upload(txtFile);

            List<File> xmlFiles = uploadable.getUploadedFilesByFilter(
                    file -> file.getName().endsWith(".xml")
            );

            assertTrue(xmlFiles.isEmpty());
        }

        @Test
        void shouldHandleNullFilter() {
            assertThrows(NullPointerException.class, () ->
                    uploadable.getUploadedFilesByFilter(null));
        }
    }

    @Nested
    class FileValidationTest {
        @Test
        void shouldValidateExistingFile() throws Exception {
            File validFile = createTestFile("test.txt", "content");
            assertTrue(Uploadable.isValidFile(validFile));
        }

        @Test
        void shouldRejectInvalidFile() {
            assertFalse(Uploadable.isValidFile(null));
            assertFalse(Uploadable.isValidFile(tempDir.toFile()));
            assertFalse(Uploadable.isValidFile(tempDir.resolve("nonexistent.txt").toFile()));
        }
    }

    private File createTestFile(String name, String content) throws Exception {
        Path file = tempDir.resolve(name);
        Files.writeString(file, content);
        return file.toFile();
    }

    private File createTestFile(File directory, String name, String content) throws Exception {
        Path file = directory.toPath().resolve(name);
        Files.writeString(file, content);
        return file.toFile();
    }

    private File createDirectory(String name) throws Exception {
        Path directory = tempDir.resolve(name);
        Files.createDirectory(directory);
        return directory.toFile();
    }

    private static class TestUploadable implements Uploadable {
        private final Set<File> uploadedFiles = new HashSet<>();

        @Override
        public boolean upload(File file) throws VCSException {
            if (!Uploadable.isValidFile(file)) {
                throw new VCSException("Invalid file: " + (file != null ? file.getPath() : "null"));
            }
            return uploadedFiles.add(file);
        }

        @Override
        public boolean uploadDirectory(File directory) throws VCSException {
            if (directory == null || !directory.exists() || !directory.isDirectory()) {
                throw new VCSException("Invalid directory: " +
                        (directory != null ? directory.getPath() : "null"));
            }

            File[] files = directory.listFiles();
            if (files == null) {
                return true;
            }

            boolean success = true;
            for (File file : files) {
                if (file.isFile()) {
                    success &= upload(file);
                }
            }
            return success;
        }

        @Override
        public List<File> getUploadedFiles() {
            return new ArrayList<>(uploadedFiles);
        }

        @Override
        public void removeFile(String filePath) throws VCSException {
            if (filePath == null) {
                throw new VCSException("File path cannot be null");
            }
            uploadedFiles.removeIf(file -> file.getPath().equals(filePath));
        }
    }
}
