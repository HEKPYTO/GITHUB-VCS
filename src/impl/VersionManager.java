package impl;

import interfaces.Versionable;
import model.*;
import exceptions.*;
import java.io.*;
import java.nio.file.*;
import java.util.*;

public class VersionManager implements Versionable {
    private final String repositoryPath;
    private final List<VersionInfo> versionHistory;
    private final Map<String, VersionInfo> versionMap;

    public VersionManager(String repositoryPath) {
        this.repositoryPath = repositoryPath;
        this.versionHistory = new ArrayList<>();
        this.versionMap = new HashMap<>();
        loadVersionHistory();
    }

    private void loadVersionHistory() {
        Path versionsDir = Paths.get(repositoryPath, ".vcs", "versions");
        if (Files.exists(versionsDir)) {
            try (DirectoryStream<Path> stream = Files.newDirectoryStream(versionsDir)) {
                for (Path path : stream) {
                    try {
                        VersionInfo version = loadVersionFile(path.toFile());
                        if (version != null) {
                            versionHistory.add(version);
                            versionMap.put(version.getVersionId(), version);
                        }
                    } catch (Exception e) {
                        System.err.println("Failed to load version file: " + path + ", Error: " + e.getMessage());
                    }
                }
            } catch (IOException e) {
                System.err.println("Failed to read versions directory: " + e.getMessage());
            }
        }
    }

    private VersionInfo loadVersionFile(File file) throws VCSException {
        try (ObjectInputStream ois = new ObjectInputStream(new FileInputStream(file))) {
            Object obj = ois.readObject();
            if (obj instanceof VersionInfo) {
                return (VersionInfo) obj;
            }
        } catch (Exception e) {
            throw new VCSException("Failed to load version from file: " + file.getName(), e);
        }
        return null;
    }

    @Override
    public String createVersion(String message) throws VCSException {
        return createVersion(message, new HashMap<>());
    }

    public String createVersion(String message, Map<String, String> fileHashes) throws VersionException {
        if (message == null) {
            throw new VersionException.InvalidVersionException("Version message cannot be null");
        }

        try {
            VersionInfo version = new VersionInfo(message, System.getProperty("user.name"), new HashMap<>(fileHashes));

            Path versionsDir = Paths.get(repositoryPath, ".vcs", "versions");
            if (!Files.exists(versionsDir)) {
                Files.createDirectories(versionsDir);
            }

            Path versionFile = versionsDir.resolve(version.getVersionId());
            saveVersionToFile(version, versionFile.toFile());

            versionHistory.add(version);
            versionMap.put(version.getVersionId(), version);

            return version.getVersionId();
        } catch (Exception e) {
            throw new VersionException.VersionCreationException(e.getMessage());
        }
    }

    private void saveVersionToFile(VersionInfo version, File file) throws VCSException {
        try (ObjectOutputStream oos = new ObjectOutputStream(new FileOutputStream(file))) {
            oos.writeObject(version);
        } catch (IOException e) {
            throw new VCSException("Failed to save version to file", e);
        }
    }

    @Override
    public void revertToVersion(String versionId) throws VCSException, IOException {
        VersionInfo version = getVersion(versionId);
        if (version == null) {
            throw new VersionException("Version not found: " + versionId);
        }
        Path versionsDir = Paths.get(repositoryPath, ".vcs", "versions");
        Path versionFile = versionsDir.resolve(versionId);
        if (!Files.exists(versionFile)) {
            throw new VersionException("Version file not found: " + versionId);
        }
    }

    public VersionInfo getVersion(String versionId) {
        return versionMap.get(versionId);
    }

    @Override
    public List<VersionInfo> getVersionHistory() {
        return new ArrayList<>(versionHistory);
    }

    @Override
    public VersionInfo getCurrentVersion() {
        if (versionHistory.isEmpty()) {
            return null;
        }
        return versionHistory.getLast();
    }

    public String getRepositoryPath() {
        return repositoryPath;
    }
}