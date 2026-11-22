package MCD.consistency.cli;

import MCD.consistency.ConsistencyMetrics;
import MCD.consistency.PairwiseComparisonMatrix;
import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.Assert.*;

public class MatrixReadersTest {
    @Rule public TemporaryFolder temporary = new TemporaryFolder();

    @Test
    public void loadsCsvAndJsonExamplesWithExactlyEqualDoubles() throws IOException {
        MatrixInput csv = new MatrixFileReader().read(Path.of("examples/matrices/sayadi_4x4.csv"));
        MatrixInput json = new MatrixFileReader().read(Path.of("examples/matrices/sayadi_4x4.json"));
        assertEquals("Sayadi-Janicki 2026: quantitative example", json.name());
        assertMatricesEqual(csv.matrix(), json.matrix());
        assertEquals(4, csv.matrix().size());
        assertEquals(0.55, ConsistencyMetrics.koczkodajIndex(csv.matrix()).value(), 1e-14);
        assertEquals(0.0245317176445452, ConsistencyMetrics.saatyIndex(json.matrix()), 1e-12);
    }

    @Test
    public void acceptsCsvWhitespaceScientificNotationAndUtf8Bom() throws IOException {
        Path path = file("matrix.CSV", "\uFEFF 1 , 2e0 \n 0.5 , 1 \n");
        assertEquals(2, new MatrixFileReader().read(path).matrix().get(0, 1), 0);
    }

    @Test
    public void readsOptionalJsonNameAndUtf8TextWithoutCoercion() throws IOException {
        MatrixInput named = new MatrixFileReader().read(file("named.json", "{\"name\":\"Сравнения\",\"matrix\":[[1,2],[0.5,1]]}"));
        assertEquals("Сравнения", named.name());
        MatrixInput unnamed = new MatrixFileReader().read(file("unnamed.json", "{\"matrix\":[[1,2],[0.5,1]]}"));
        assertEquals("unnamed.json", unnamed.name());
    }

    @Test
    public void rejectsEmptyCsvCellsAndRowsWithCoordinates() throws IOException {
        for (String contents : new String[] {"1,\n0.5,1", "1,2,\n0.5,1", "1,2\n\n0.5,1"}) {
            var error = assertThrows(IllegalArgumentException.class, () -> new MatrixFileReader().read(file("empty.csv", contents)));
            assertTrue(error.getMessage(), error.getMessage().contains("Empty CSV"));
        }
    }

    @Test
    public void rejectsEmptyAndMissingFilesAndUnknownExtensions() throws IOException {
        assertThrows(IllegalArgumentException.class, () -> new MatrixFileReader().read(file("empty.csv", "")));
        assertThrows(IllegalArgumentException.class, () -> new MatrixFileReader().read(file("empty.json", "")));
        assertThrows(IOException.class, () -> new MatrixFileReader().read(temporary.getRoot().toPath().resolve("missing.csv")));
        assertThrows(IllegalArgumentException.class, () -> new MatrixFileReader().read(file("matrix.txt", "1,2\n0.5,1")));
        assertThrows(IOException.class, () -> new MatrixFileReader().read(temporary.getRoot().toPath()));
    }

    @Test
    public void rejectsNonnumericCsvAndAllMathematicalViolationsThroughTheCore() throws IOException {
        String[][] cases = {
                {"1,text\n0.5,1", "row 1, column 2"},
                {"1,2\n0.5", "square"}, {"1,2,3\n0.5,1,4", "square"},
                {"1,NaN\n0.5,1", "finite"}, {"1,Infinity\n0.5,1", "finite"},
                {"1,0\n0.5,1", "positive"}, {"1,-2\n-0.5,1", "positive"},
                {"2,2\n0.5,1", "Diagonal"}, {"1,2\n0.4,1", "reciprocal"}
        };
        for (String[] test : cases) {
            var error = assertThrows(IllegalArgumentException.class, () -> new MatrixFileReader().read(file("bad.csv", test[0])));
            assertTrue(error.getMessage(), error.getMessage().contains(test[1]));
        }
    }

    @Test
    public void rejectsJsonStringsBooleansNullsAndWrongShapes() throws IOException {
        for (String content : new String[] {
                "{}", "[]", "{\"matrix\":null}", "{\"matrix\":[]}",
                "{\"matrix\":[[1,\"2\"],[0.5,1]]}", "{\"matrix\":[[1,true],[0.5,1]]}",
                "{\"matrix\":[[1,null],[0.5,1]]}", "{\"matrix\":[1,2]}",
                "{\"matrix\":[[1,2],[0.5]]}", "{\"name\":3,\"matrix\":[[1,2],[0.5,1]]}"
        }) {
            assertThrows(content, IllegalArgumentException.class, () -> new MatrixFileReader().read(file("bad.json", content)));
        }
    }

    @Test
    public void rejectsMalformedLenientDuplicateAndTrailingJson() throws IOException {
        for (String content : new String[] {
                "{", "{matrix:[[1,2],[0.5,1]]}", "{'matrix':[[1,2],[0.5,1]]}",
                "{\"matrix\":[[1,2],[0.5,1]],}", "{/*comment*/\"matrix\":[[1,2],[0.5,1]]}",
                "{\"matrix\":[[1,NaN],[0.5,1]]}", "{\"matrix\":[[1,2],[0.5,1]]}{}",
                "{\"matrix\":[[1,2],[0.5,1]],\"matrix\":[[1,1],[1,1]]}"
        }) {
            assertThrows(content, IllegalArgumentException.class, () -> new MatrixFileReader().read(file("bad.json", content)));
        }
    }

    @Test
    public void rejectsJsonOverflowAndBrokenMatrixProperties() throws IOException {
        for (String content : new String[] {
                "{\"matrix\":[[1,1e999],[0.5,1]]}", "{\"matrix\":[[1,-2],[-0.5,1]]}",
                "{\"matrix\":[[1,2],[0.4,1]]}", "{\"matrix\":[[2,2],[0.5,1]]}"
        }) {
            assertThrows(IllegalArgumentException.class, () -> new MatrixFileReader().read(file("bad.json", content)));
        }
    }

    static void assertMatricesEqual(PairwiseComparisonMatrix expected, PairwiseComparisonMatrix actual) {
        assertEquals(expected.size(), actual.size());
        for (int i = 0; i < expected.size(); i++) {
            assertArrayEquals(expected.toArray()[i], actual.toArray()[i], 0);
        }
    }

    private Path file(String name, String contents) throws IOException {
        Path path = temporary.getRoot().toPath().resolve(name);
        Files.writeString(path, contents, StandardCharsets.UTF_8);
        return path;
    }
}
