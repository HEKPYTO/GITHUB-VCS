package interfaces;

import exceptions.VCSException;
import models.ConflictInfo;
import models.ConflictResolution;
import java.util.List;

public interface Mergeable {
    boolean merge(String sourceVersion, String targetVersion) throws VCSException;
    List<ConflictInfo> getConflicts();
    void resolveConflict(String filePath, ConflictResolution resolution) throws VCSException;
}