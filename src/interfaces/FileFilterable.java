package interfaces;

import java.io.File;

@FunctionalInterface
public interface FileFilterable {
    boolean filter(File file);
}
