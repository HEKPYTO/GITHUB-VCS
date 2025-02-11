package test.model;

import model.ConflictInfo;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.MethodSource;
import java.util.stream.Stream;
import static org.junit.jupiter.api.Assertions.*;

class ConflictBlockTest {

    @Test
    void testValidConflictBlock() {
        ConflictInfo.ConflictBlock block = new ConflictInfo.ConflictBlock(0, 1, "source", "target");
        assertEquals(0, block.startLine());
        assertEquals(1, block.endLine());
        assertEquals("source", block.sourceContent());
        assertEquals("target", block.targetContent());
    }

    @Test
    void testSingleLineConflict() {
        ConflictInfo.ConflictBlock block = new ConflictInfo.ConflictBlock(5, 5, "source", "target");
        assertEquals(5, block.startLine());
        assertEquals(5, block.endLine());
    }

    @ParameterizedTest
    @MethodSource("provideValidRanges")
    void testValidRanges(int start, int end) {
        assertDoesNotThrow(() -> new ConflictInfo.ConflictBlock(start, end, "source", "target"));
    }

    private static Stream<Arguments> provideValidRanges() {
        return Stream.of(
                Arguments.of(0, 0),
                Arguments.of(0, 1),
                Arguments.of(5, 10),
                Arguments.of(100, 100),
                Arguments.of(1000, 1001)
        );
    }

    @ParameterizedTest
    @MethodSource("provideInvalidRanges")
    void testInvalidRanges(int start, int end) {
        assertThrows(IllegalArgumentException.class,
                () -> new ConflictInfo.ConflictBlock(start, end, "source", "target"));
    }

    private static Stream<Arguments> provideInvalidRanges() {
        return Stream.of(
                Arguments.of(-1, 0),
                Arguments.of(0, -1),
                Arguments.of(5, 4),
                Arguments.of(-1, -1),
                Arguments.of(Integer.MAX_VALUE, Integer.MIN_VALUE)
        );
    }

    @Test
    void testNullAndEmptyContent() {
        assertThrows(IllegalArgumentException.class,
                () -> new ConflictInfo.ConflictBlock(0, 0, null, "target"));
        assertThrows(IllegalArgumentException.class,
                () -> new ConflictInfo.ConflictBlock(0, 0, "source", null));

        assertDoesNotThrow(() ->
                new ConflictInfo.ConflictBlock(0, 1, "", ""));
    }

    @Test
    void testLargeRange() {
        ConflictInfo.ConflictBlock block = new ConflictInfo.ConflictBlock(0, Integer.MAX_VALUE - 1,
                "source", "target");
        assertEquals(0, block.startLine());
        assertEquals(Integer.MAX_VALUE - 1, block.endLine());
    }
}