package index;
/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.document.Field;
import org.apache.lucene.document.StoredField;
import org.apache.lucene.document.TextField;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.*;

/**
 * Index all text files under a directory.
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing.
 * Run it with no command-line arguments for usage information.
 */
public class Index {

    private final static String BIBLE_PATH = "/Users/michael/projects/bible_search/code/data/luther_utf8.txt";

    /**  */
    private final static int CONTEXT_WINDOWS_VERSE_BASED = 1;

    /**  */
    private final static float CONTEXT_BOOST = (float) 0.7;

    /** valid sentence endings */
    private final static List<String> SENTENCE_ENDINGS = Arrays.asList(".","?","!",";", "\"");

    /** amount of lines to index, 0 means all */
    private final static int MAX_LINES_FOR_INDEX = 0;

    /** max elements to show for processing state */
    private final static int MAX_UI_PROCESS_ELEMENTS = 100;

    private Index() {
    }

    /**
     * Index all text files under a directory.
     */
    public static void main(String[] args) {
        /*
        String usage = "java org.apache.lucene.demo.Index"
                + " [-index INDEX_PATH] [-docs DOCS_PATH] [-update]\n\n"
                + "This indexes the documents in DOCS_PATH, creating a Lucene index"
                + "in INDEX_PATH that can be searched with SearchFiles";
        */

        String indexFile;
        boolean create = true;

        String indexPath = "index";
        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                indexPath = args[i + 1];
                i++;
            } else if ("-update".equals(args[i])) {
                create = false;
            }
        }

        indexFile = BIBLE_PATH;

        Date start = new Date();
        try {
            System.out.println("Indexing...");

            Directory dir = FSDirectory.open(Paths.get(indexPath));
            Analyzer analyzer = new GermanAnalyzer();
            IndexWriterConfig iwc = new IndexWriterConfig(analyzer);

            if (create) {
                // Create a new index in the directory, removing any
                // previously indexed documents:
                iwc.setOpenMode(OpenMode.CREATE);
            } else {
                // Add new documents to an existing index:
                iwc.setOpenMode(OpenMode.CREATE_OR_APPEND);
            }

            // Optional: for better indexing performance, if you
            // are indexing many documents, increase the RAM
            // buffer.  But if you do this, increase the max heap
            // size to the JVM (eg add -Xmx512m or -Xmx1g):
            //
            // iwc.setRAMBufferSizeMB(256.0);

            IndexWriter writer = new IndexWriter(dir, iwc);

            indexFile(writer, indexFile);

            // NOTE: if you want to maximize search performance,
            // you can optionally call forceMerge here.  This can be
            // a terribly costly operation, so generally it's only
            // worth it when your index is relatively static (ie
            // you're done adding documents to it):
            //
            writer.forceMerge(1);

            writer.close();

            Date end = new Date();
            String endMsg = (double)(end.getTime() - start.getTime())/1000 + " total seconds";
            System.out.println(endMsg);
            String endMsgUnderline = new String(new char[endMsg.length()]).replace("\0", "-");
            System.out.println(endMsgUnderline);

        } catch (IOException e) {
            System.out.println(" caught a " + e.getClass() + "\n with message: " + e.getMessage());
        }
    }

    /**
     * Indexes a single document
     */
    static void indexFile(IndexWriter writer, String filePath) throws IOException {
        Path file = Paths.get(filePath);

        System.out.println(new String(new char[MAX_UI_PROCESS_ELEMENTS]).replace("\0", "-") + "|100%");

        List<String> lines = Files.readAllLines(file);

        int allLines = lines.size();
        if (MAX_LINES_FOR_INDEX > 0) {
            allLines = Math.min(allLines, MAX_LINES_FOR_INDEX);
        }

        double processPart = ((double)allLines / MAX_UI_PROCESS_ELEMENTS);

        int processedLines = 0;
        int processPassedPart = 0;
        int processCurrentPart;
        int newDocuments = 0;

        int fullId = 0;
        int fullBook = 0;
        int fullChapter = 0;
        int fullVerse = 0;
        int fullVerseLength = 0;
        String fullText = "";

        Map<Integer,Integer> versesStats = new HashMap<Integer, Integer>();
        for (String line : lines) {
            processedLines++;

            processCurrentPart = (int)Math.ceil(processedLines / processPart);
            if (processCurrentPart > processPassedPart && processCurrentPart * processPart <= allLines) {
                System.out.print("*");
                processPassedPart = processCurrentPart;
            }

            String[] linePart = line.split("\t");

            int id = Integer.parseInt(linePart[0]);
            int book = Integer.parseInt(linePart[1]);
            int chapter = Integer.parseInt(linePart[2]);
            int verse = Integer.parseInt(linePart[3]);
            String text = linePart[4].trim();

            // build multi vers sentence
            if (fullVerseLength == 0) {
                fullId = id;
                fullBook = book;
                fullChapter = chapter;
                fullVerse = verse;
            } else {
                text = "\n\t" + text;
            }

            fullVerseLength++;
            fullText += text;

            // last char
            String lastChar = text.substring(text.length() - 1);
            boolean addToIndex = SENTENCE_ENDINGS.contains(lastChar) || fullBook != book || fullChapter != chapter;

            if (addToIndex) {
                // add only whole sentence to the index

                // make a new, empty document
                Document doc = new Document();

                doc.add(new StoredField("id", Integer.toString(fullId)));
                doc.add(new StoredField("book", Integer.toString(fullBook)));
                doc.add(new StoredField("chapter", Integer.toString(fullChapter)));
                doc.add(new StoredField("verse", Integer.toString(fullVerse)));
                doc.add(new TextField("content", fullText, Field.Store.YES));
                doc.add(new StoredField("verseLength", Integer.toString(fullVerseLength)));

                // add neighbours to context
                String context = "";
                for(int j = processedLines-CONTEXT_WINDOWS_VERSE_BASED; j < processedLines+CONTEXT_WINDOWS_VERSE_BASED; j++) {
                    // skip invalid lines and the main verse by itself
                    if (j >= 0 && j < lines.size()) {
                        String[] contextlinePart = lines.get(j).split("\t");

                        int contextBook = Integer.parseInt(contextlinePart[1]);
                        // int contextChapter = Integer.parseInt(contextlinePart[2]);

                        // add only verses if it in the same book
                        if (fullBook == contextBook) {
                            String contextText = contextlinePart[4].trim();
                            context += contextText + " ";
                        }
                    }
                }
                TextField contentTextField = new TextField("context", context.trim(), Field.Store.YES);
                // set weak boost for this context field
                contentTextField.setBoost(CONTEXT_BOOST);
                doc.add(contentTextField);

                /*
                if (fullVerseLength == 9) {
                    System.out.println(fullText + "\n");
                }
                */


                int oldValue = 1;
                if (versesStats.containsKey(fullVerseLength)) {
                    oldValue = versesStats.get(fullVerseLength);
                    oldValue++;
                }
                versesStats.put(fullVerseLength, oldValue);

                if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
                    // New index, so we just add the document (no old document can be there):
                    writer.addDocument(doc);
                } else {
                    // Existing index (an old copy of this document may have been indexed) so
                    // we use updateDocument instead to replace the old one matching the exact
                    // path, if present:
                    writer.updateDocument(new Term("id", Integer.toString(id)), doc);
                }
                newDocuments++;

                // reset multi sentence values
                fullId = 0;
                fullBook = 0;
                fullChapter = 0;
                fullVerse = 0;
                fullVerseLength = 0;
                fullText = "";

                if (MAX_LINES_FOR_INDEX > 0 && processedLines >= MAX_LINES_FOR_INDEX) {
                    break;
                }

            }


        }

        System.out.println("\n" + "STATS: ammount verses in one document");
        for (Map.Entry<Integer, Integer> entry : versesStats.entrySet()) {
            System.out.println(Integer.toString(entry.getKey()) + ": " + Integer.toString(entry.getValue()) + "x");
        }

        System.out.println("\n" + Integer.toString(processedLines) + " lines indexed");
        System.out.println(Integer.toString(newDocuments) + " Documents created");

    }
}
