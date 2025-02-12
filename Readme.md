# Java Git - Version Control System (VCS)

A robust, extensible version control system implementation in Java that provides core version control functionality including file tracking, versioning, diffing, and merge conflict resolution.

## Features

- **File Management**
    - Track and Untrack files
    - Monitor file status changes (modified, deleted, staged)
    - Support for binary and text files
    - Automatic file hash calculation and verification

- **Version Control**
    - Create versions with commit messages
    - Maintain version history
    - Revert to previous versions
    - Track version metadata (author, timestamp, file hashes)

- **Diff Generation**
    - Line-by-line diff analysis
    - Support for additions, deletions, and modifications
    - Efficient diff algorithm using dynamic programming
    - Generate diffs between versions or working changes

- **Merge Handling**
    - Detect and resolve merge conflicts
    - Multiple conflict resolution strategies (keep source, keep target, custom)
    - Automatic merging for non-conflicting changes
    - Detailed conflict information tracking

## Architecture

The system is built using a modular architecture with clear separation of concerns:

### Core Components

- `VersionControlSystem`: Main entry point and coordinator
- `FileTracker`: Handles file status monitoring and tracking
- `VersionManager`: Manages version creation and history
- `DiffGenerator`: Generates and compares file differences
- `MergeHandler`: Handles merge operations and conflict resolution

### Key Interfaces

- `Trackable`: File tracking operations
- `Versionable`: Version management operations
- `Diffable`: Diff generation operations
- `Mergeable`: Merge handling operations
- `Uploadable`: File upload operations

### Models

- `VersionInfo`: Version metadata and file hashes
- `FileMetadata`: File tracking information
- `ChangedLines`: Diff information
- `ConflictInfo`: Merge conflict details
- `LineChange`: Individual line modifications

## Getting Started

### Prerequisites

- Java 23 or higher
- IntelliJ IDEA (Community or Ultimate Edition)

### Installation

1. Clone the repository:
```bash
git clone https://github.com/yourusername/vcs.git
```

2. Open the project in IntelliJ IDEA:
    - Launch IntelliJ IDEA
    - Select `File -> Open`
    - Navigate to the cloned repository directory
    - Click OK to open the project
    - Wait for IntelliJ to finish importing and indexing the project

3. Configure Project SDK:
    - Right-click the project root in the Project view
    - Select `Module Settings` (F4)
    - Under `Project`, ensure the Project SDK is set to Java 17 or higher
    - Click OK to apply the changes

4. Build the project:
    - Select `Build -> Build Project` or press Ctrl+F9 (⌘F9 on macOS)

### Basic Usage

1. Initialize a new VCS repository:
```java
VersionControlSystem vcs = new VersionControlSystem("/path/to/repository");
```

2. Track files:
```java
vcs.trackFile("myfile.txt");
```

3. Create a version:
```java
String versionId = vcs.createVersion("Initial commit");
```

4. Generate diff:
```java
DiffResult diff = vcs.getDiffGenerator().getDiff("oldVersion", "newVersion");
```

5. Handle merge conflicts:
```java
boolean merged = vcs.getMergeHandler().merge("sourceVersion", "targetVersion");
if (!merged) {
    List<ConflictInfo> conflicts = vcs.getMergeHandler().getConflicts();
    // Handle conflicts
}
```

## Error Handling

The system uses a hierarchical exception system:

- `VCSException`: Base exception class
- `FileOperationException`: File-related errors
- `VersionException`: Version-related errors
- `MergeConflictException`: Merge-related errors

Each exception type includes specific subclasses for detailed error handling.

## Testing

The project includes comprehensive tests:

- Unit tests for all components
- Integration tests for workflows
- Concurrency tests
- Edge case handling
- Error condition testing

Run tests using:
- Right-click on the `test` directory in the Project view
- Select `Run 'All Tests'`
- Or use the keyboard shortcut Ctrl+Shift+F10 (⌘Shift+F10 on macOS)

## Design Patterns Used

- **Observer Pattern**: File change notifications
- **Strategy Pattern**: Diff algorithms and merge strategies
- **Factory Pattern**: Exception creation
- **Builder Pattern**: Version creation
- **Command Pattern**: File operations

## Acknowledgments

- The diff algorithm is inspired by the Myers diff algorithm
- File hashing uses SHA-256 for content verification
- Conflict resolution approach based on modern VCS systems