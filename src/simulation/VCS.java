package simulation;

import impl.*;
import models.*;
import exceptions.*;
import java.io.File;
import java.nio.file.*;
import java.util.*;

public class VCS {
    public static void main(String[] args) {
        String repoPath = System.getProperty("java.io.tmpdir") + "/vcs_demo_" + System.currentTimeMillis();
        System.out.println("Creating repository at: " + repoPath);

        try {
            Files.createDirectories(Paths.get(repoPath));
            VersionControlSystem vcs = new VersionControlSystem(repoPath);

            // Create test files
            System.out.println("\n=== Creating Test Files ===");
            Path file1 = Paths.get(repoPath, "file1.txt");
            Path file2 = Paths.get(repoPath, "file2.txt");

            String initialContent1 = "Initial content for file 1\nSecond line\nThird line";
            String initialContent2 = "Initial content for file 2\nAnother line\nFinal line";

            Files.writeString(file1, initialContent1);
            Files.writeString(file2, initialContent2);

            System.out.println("Created files:");
            System.out.println("- " + file1 + "\n  Content:\n" + initialContent1);
            System.out.println("- " + file2 + "\n  Content:\n" + initialContent2);

            // Track files
            System.out.println("\n=== Tracking Files ===");
            vcs.trackFile(file1.toString());
            vcs.trackFile(file2.toString());

            // Show tracked files
            System.out.println("Tracked files:");
            for (String path : vcs.getTrackedFiles()) {
                FileMetadata metadata = vcs.getFileTracker().getFileMetadata(path);
                System.out.println("- " + path);
                System.out.println("  Status: " + metadata.getStatus());
                System.out.println("  Hash: " + metadata.getCurrentHash());
            }

            // Create initial version
            System.out.println("\n=== Creating Initial Version ===");
            String version1 = vcs.createVersion("Initial commit");
            System.out.println("Created version: " + version1);

            // Modify file1
            System.out.println("\n=== Modifying File ===");
            String modifiedContent = "Modified content\nNew line added\nChanged content";
            Files.writeString(file1, modifiedContent);
            System.out.println("Modified file1 content:\n" + modifiedContent);

            // Update file status
            vcs.getFileTracker().updateFileStatus(file1.toString());

            System.out.println("\nFile statuses after modification:");
            Map<String, FileStatus> statusesAfterMod = vcs.getFileStatuses();
            for (Map.Entry<String, FileStatus> entry : statusesAfterMod.entrySet()) {
                System.out.println("- " + entry.getKey() + ": " + entry.getValue());
            }

            // Create second version
            System.out.println("\n=== Creating Second Version ===");
            String version2 = vcs.createVersion("Modified file1");
            System.out.println("Created version: " + version2);

            // Show version history
            System.out.println("\n=== Version History ===");
            for (VersionInfo version : vcs.getVersionHistory()) {
                System.out.println(String.format("- %s: %s (by %s at %s)",
                        version.getVersionId(),
                        version.getMessage(),
                        version.getAuthor(),
                        version.getTimestamp()));
            }

            // Generate diff
            System.out.println("\n=== Diff Between Versions ===");
            DiffResult diff = vcs.getDiffGenerator().getDiff(version1, version2);

            for (Map.Entry<String, ChangedLines> entry : diff.getChanges().entrySet()) {
                System.out.println("\nChanges in: " + entry.getKey());
                ChangedLines changes = entry.getValue();

                if (!changes.getAdditions().isEmpty()) {
                    System.out.println("\nAdditions:");
                    for (LineChange addition : changes.getAdditions()) {
                        System.out.println("+ " + addition.getNewContent());
                    }
                }

                if (!changes.getDeletions().isEmpty()) {
                    System.out.println("\nDeletions:");
                    for (LineChange deletion : changes.getDeletions()) {
                        System.out.println("- " + deletion.getOldContent());
                    }
                }

                if (!changes.getModifications().isEmpty()) {
                    System.out.println("\nModifications:");
                    for (LineChange modification : changes.getModifications()) {
                        System.out.println("< " + modification.getOldContent());
                        System.out.println("> " + modification.getNewContent());
                    }
                }
            }

            // Show final status
            System.out.println("\n=== Final File Statuses ===");
            Map<String, FileStatus> finalStatuses = vcs.getFileStatuses();
            for (Map.Entry<String, FileStatus> entry : finalStatuses.entrySet()) {
                System.out.println("- " + entry.getKey() + ": " + entry.getValue());
            }

            System.out.println("\nDemo completed successfully!");

        } catch (Exception e) {
            System.err.println("Demo failed: " + e.getMessage());
            e.printStackTrace();
        }
    }
}