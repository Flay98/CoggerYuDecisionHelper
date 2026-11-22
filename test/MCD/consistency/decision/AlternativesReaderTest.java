package MCD.consistency.decision;

import org.junit.Rule;
import org.junit.Test;
import org.junit.rules.TemporaryFolder;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.charset.StandardCharsets;
import java.util.List;
import static org.junit.Assert.*;

public class AlternativesReaderTest {
    @Rule public TemporaryFolder temporary=new TemporaryFolder();

    @Test public void readsUtf8NamesNumbersAndWhitespaceInHeaderOrder() throws Exception {
        var table=read("alternative, скорость ,стоимость\n Вариант Б, 90,120\nВариант А,70,75\n",2,"max,min");
        assertEquals(List.of("скорость","стоимость"),table.criteria());assertEquals(List.of("Вариант Б","Вариант А"),table.alternatives());
        assertEquals(90,table.get(0,0),0);assertEquals(75,table.get(1,1),0); assertEquals(List.of(Direction.MAX,Direction.MIN),table.directions());
    }

    @Test public void rejectsWrongMissingEmptyAndDuplicateHeaders() {
        for(String text:List.of("wrong,x,y\nA,1,2\n","alternative,x,\nA,1,2\n","alternative,x,x\nA,1,2\n","alternative\nA\n"))
            assertThrows(text,IllegalArgumentException.class,()->read(text,2,"max,min"));
    }

    @Test public void rejectsEmptyOrRepeatedAlternativeNames() {
        for(String text:List.of("alternative,x,y\n,1,2\n","alternative,x,y\nA,1,2\nA,2,3\n"))
            assertThrows(IllegalArgumentException.class,()->read(text,2,"max,min"));
    }

    @Test public void rejectsNonrectangularBlankAndQuotedRows() {
        for(String text:List.of("alternative,x,y\nA,1\n","alternative,x,y\nA,1,2,3\n","alternative,x,y\n\nA,1,2\n","alternative,x,y\n\"A\",1,2\n"))
            assertThrows(IllegalArgumentException.class,()->read(text,2,"max,min"));
    }

    @Test public void rejectsInvalidNonfiniteNonpositiveAndEmptyNumericCells() {
        for(String value:List.of("text","NaN","Infinity","-Infinity","0","-1",""))
            assertThrows(value,IllegalArgumentException.class,()->read("alternative,x,y\nA,"+value+",2\n",2,"max,min"));
    }

    @Test public void validatesCriterionAndDirectionCountsAndDirectionVocabulary() {
        assertThrows(IllegalArgumentException.class,()->read("alternative,x,y\nA,1,2\n",3,"max,min,max"));
        assertThrows(IllegalArgumentException.class,()->read("alternative,x,y\nA,1,2\n",2,"max"));
        for(String value:List.of("max,other","max,","","minimize")) assertThrows(IllegalArgumentException.class,()->Direction.parse(value));
        assertEquals(List.of(Direction.MAX,Direction.MIN),Direction.parse(" MAX, min "));
    }

    @Test public void missingAndEmptyFilesProduceExplicitErrors() throws Exception {
        assertThrows(java.nio.file.NoSuchFileException.class,()->new AlternativesReader().read(temporary.getRoot().toPath().resolve("missing.csv"),Direction.parse("max,min"),2));
        assertThrows(IllegalArgumentException.class,()->read("",2,"max,min"));
        assertThrows(IllegalArgumentException.class,()->read("alternative,x,y\n",2,"max,min"));
    }

    private AlternativeTable read(String text,int size,String directions) throws Exception {
        Path path=temporary.newFile().toPath();Files.writeString(path,text,StandardCharsets.UTF_8);
        return new AlternativesReader().read(path,Direction.parse(directions),size);
    }
}
