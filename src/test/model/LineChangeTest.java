package test.model;

import model.LineChange;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.Arguments;
import org.junit.jupiter.params.provider.EnumSource;
import org.junit.jupiter.params.provider.MethodSource;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.stream.Stream;

import static org.junit.jupiter.api.Assertions.*;

class LineChangeTest {

    @Test
    void testConstructorAndGetters() {
        LineChange change = new LineChange(1, "old", "new", LineChange.ChangeType.MODIFICATION);
        assertEquals(1, change.lineNumber());
        assertEquals("old", change.oldContent());
        assertEquals("new", change.newContent());
        assertEquals(LineChange.ChangeType.MODIFICATION, change.type());
    }

    @Test
    void testAdditionType() {
        LineChange addition = new LineChange(1, null, "new line", LineChange.ChangeType.ADDITION);
        assertNull(addition.oldContent());
        assertEquals("new line", addition.newContent());
        assertEquals(LineChange.ChangeType.ADDITION, addition.type());
    }

    @Test
    void testDeletionType() {
        LineChange deletion = new LineChange(1, "old line", null, LineChange.ChangeType.DELETION);
        assertEquals("old line", deletion.oldContent());
        assertNull(deletion.newContent());
        assertEquals(LineChange.ChangeType.DELETION, deletion.type());
    }

    @Test
    void testModificationType() {
        LineChange modification = new LineChange(1, "old content", "new content", LineChange.ChangeType.MODIFICATION);
        assertEquals("old content", modification.oldContent());
        assertEquals("new content", modification.newContent());
        assertEquals(LineChange.ChangeType.MODIFICATION, modification.type());
    }

    @ParameterizedTest
    @ValueSource(ints = {0, 1, 100, Integer.MAX_VALUE})
    void testDifferentLineNumbers(int lineNumber) {
        LineChange change = new LineChange(lineNumber, "old", "new", LineChange.ChangeType.MODIFICATION);
        assertEquals(lineNumber, change.lineNumber());
    }

    @ParameterizedTest
    @EnumSource(LineChange.ChangeType.class)
    void testAllChangeTypes(LineChange.ChangeType type) {
        LineChange change = new LineChange(1, "old", "new", type);
        assertEquals(type, change.type());
    }

    @Test
    void testEquality() {
        LineChange change1 = new LineChange(1, "old", "new", LineChange.ChangeType.MODIFICATION);
        LineChange change2 = new LineChange(1, "old", "new", LineChange.ChangeType.MODIFICATION);
        LineChange change3 = new LineChange(2, "old", "new", LineChange.ChangeType.MODIFICATION);

        assertEquals(change1, change2);
        assertNotEquals(change1, change3);
        assertEquals(change1.hashCode(), change2.hashCode());
    }

    @ParameterizedTest
    @MethodSource("provideSpecialContents")
    void testSpecialContentStrings(String oldContent, String newContent) {
        LineChange change = new LineChange(1, oldContent, newContent, LineChange.ChangeType.MODIFICATION);
        assertEquals(oldContent, change.oldContent());
        assertEquals(newContent, change.newContent());
    }

    private static Stream<Arguments> provideSpecialContents() {
        return Stream.of(
                Arguments.of("", ""),
                Arguments.of(" ", " "),
                Arguments.of("\n", "\n"),
                Arguments.of("\t", "\t"),
                Arguments.of("Line\nwith\nnewlines", "Another\nline\nwith\nnewlines"),
                Arguments.of("Tab\tseparated", "More\ttabs"),
                Arguments.of("Unicode: 你好", "More Unicode: こんにちは"),
                Arguments.of("With emoji 😊", "Different emoji 🌟"),
                Arguments.of(null, null)
        );
    }

    @Test
    void testToString() {
        LineChange change = new LineChange(1, "old", "new", LineChange.ChangeType.MODIFICATION);
        String toString = change.toString();

        assertTrue(toString.contains("lineNumber=1"));
        assertTrue(toString.contains("oldContent=old"));
        assertTrue(toString.contains("newContent=new"));
        assertTrue(toString.contains("type=MODIFICATION"));
    }

    @Test
    void testNullContents() {
        assertDoesNotThrow(() -> new LineChange(1, null, null, LineChange.ChangeType.MODIFICATION));
        assertDoesNotThrow(() -> new LineChange(1, "old", null, LineChange.ChangeType.DELETION));
        assertDoesNotThrow(() -> new LineChange(1, null, "new", LineChange.ChangeType.ADDITION));
    }

    @Test
    void testLineNumberWithContentCombinations() {
        verifyLineChangeValidity(1, "old", "new", LineChange.ChangeType.MODIFICATION);
        verifyLineChangeValidity(1, "old", null, LineChange.ChangeType.DELETION);
        verifyLineChangeValidity(1, null, "new", LineChange.ChangeType.ADDITION);
    }

    private void verifyLineChangeValidity(int lineNumber, String oldContent, String newContent,
                                          LineChange.ChangeType type) {
        LineChange change = new LineChange(lineNumber, oldContent, newContent, type);
        assertEquals(lineNumber, change.lineNumber());
        assertEquals(oldContent, change.oldContent());
        assertEquals(newContent, change.newContent());
        assertEquals(type, change.type());
    }

    @Test
    void testHashCodeConsistency() {
        LineChange change = new LineChange(1, "old", "new", LineChange.ChangeType.MODIFICATION);
        int initialHashCode = change.hashCode();

        assertEquals(initialHashCode, change.hashCode());
        assertEquals(initialHashCode, change.hashCode());
    }

    @Test
    void testEqualityWithNull() {
        LineChange change = new LineChange(1, "old", "new", LineChange.ChangeType.MODIFICATION);
        assertNotEquals(null, change);
    }

    @Test
    void testEqualityWithDifferentClass() {
        LineChange change = new LineChange(1, "old", "new", LineChange.ChangeType.MODIFICATION);
        assertNotEquals(new Object(), change);
    }

    @Test
    void testChangeTypeValues() {
        LineChange.ChangeType[] types = LineChange.ChangeType.values();
        assertEquals(3, types.length);
        assertTrue(Stream.of(types).anyMatch(t -> t == LineChange.ChangeType.ADDITION));
        assertTrue(Stream.of(types).anyMatch(t -> t == LineChange.ChangeType.DELETION));
        assertTrue(Stream.of(types).anyMatch(t -> t == LineChange.ChangeType.MODIFICATION));
    }
}