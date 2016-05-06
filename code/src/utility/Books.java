package utility;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

/**
 * Created by michael on 05.05.16.
 */
public class Books {

    protected Map<Integer, String> bookName = new HashMap<>();
    protected Map<Integer, String> bookNameAbr = new HashMap<>();

    public Books() {
        Path path = Paths.get("/Users/michael/projects/bible_search/code/data/books.txt");
        List<String> lines = null;
        try {
            lines = Files.readAllLines(path);
        } catch (IOException e) {
            e.printStackTrace();
        }

        int i = 0;
        for (String line : lines) {
            i++;
            String[] parts = line.split("=");
            bookName.put(i, parts[1].trim());
            bookNameAbr.put(i, parts[0].trim());
        }
    }

    public Map<Integer, String> getBookName() {
        return bookName;
    }

    public Map<Integer, String> getBookNameAbr() {
        return bookNameAbr;
    }

    // todo: add bookgroups (https://www.uni-due.de/~gev020/courses/course-stuff/komm-overview.htm)

}
