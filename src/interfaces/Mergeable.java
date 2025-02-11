package interfaces;

import exceptions.VCSException;
import model.ConflictInfo;
import model.ConflictResolution;
import java.util.List;

public interface Mergeable {
    boolean merge(String sourceVersion, String targetVersion) throws VCSException;
    List<ConflictInfo> getConflicts();
    void resolveConflict(String filePath, ConflictResolution resolution) throws VCSException;
}