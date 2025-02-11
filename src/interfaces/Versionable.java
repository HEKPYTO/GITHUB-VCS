package interfaces;

import exceptions.VCSException;
import model.VersionInfo;

import java.io.IOException;
import java.util.List;
import java.util.Optional;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public interface Versionable {
    String createVersion(String message) throws VCSException;
    void revertToVersion(String versionId) throws VCSException, IOException;
    List<VersionInfo> getVersionHistory();
    VersionInfo getCurrentVersion();

    default Optional<VersionInfo> findVersion(Predicate<VersionInfo> criteria) {
        return getVersionHistory().stream()
                .filter(criteria)
                .findFirst();
    }

    default List<VersionInfo> getVersionsByAuthor(String author) {
        return getVersionHistory().stream()
                .filter(v -> v.getAuthor().equals(author))
                .collect(Collectors.toList());
    }
}