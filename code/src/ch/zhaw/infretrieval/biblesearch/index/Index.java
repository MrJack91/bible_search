package ch.zhaw.infretrieval.biblesearch.index;
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
import org.apache.lucene.analysis.standard.StandardAnalyzer;
import org.apache.lucene.document.*;
import org.apache.lucene.index.IndexWriter;
import org.apache.lucene.index.IndexWriterConfig;
import org.apache.lucene.index.IndexWriterConfig.OpenMode;
import org.apache.lucene.index.Term;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;

import java.io.IOException;
import java.nio.file.*;
import java.util.Date;
import java.util.List;

/**
 * Index all text files under a directory.
 * <p>
 * This is a command-line application demonstrating simple Lucene indexing.
 * Run it with no command-line arguments for usage information.
 */
public class Index {

    final static String BIBLE_PATH = "/Users/michael/projects/bible_search/code/data/b_luther_utf8.txt";

    /** amount of lines to index, 0 means all */
    final static int MAX_LINES_FOR_INDEX = 0;

    /** max elements to show for processing state */
    final static int MAX_UI_PROCESS_ELEMENTS = 100;

    private Index() {
    }

    /**
     * Index all text files under a directory.
     */
    public static void main(String[] args) {
        String indexFile;
        boolean create = true;

        for (int i = 0; i < args.length; i++) {
            if ("-update".equals(args[i])) {
                create = false;
            }
        }

        indexFile = BIBLE_PATH;

        Date start = new Date();
        try {
            System.out.println("Indexing...");

            Directory dir = FSDirectory.open(Paths.get("index"));
            Analyzer analyzer = new StandardAnalyzer();
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
            // writer.forceMerge(1);

            writer.close();

            Date end = new Date();
            String endMsg = end.getTime() - start.getTime() + " total milliseconds";
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
            String text = linePart[4];

            // System.out.println(text);

            // make a new, empty document
            Document doc = new Document();

            doc.add(new StoredField("id", Integer.toString(id)));
            doc.add(new StoredField("book", Integer.toString(book)));
            doc.add(new StoredField("chapter", Integer.toString(chapter)));
            doc.add(new StoredField("verse", Integer.toString(verse)));
            doc.add(new TextField("text", text, Field.Store.YES));

            if (writer.getConfig().getOpenMode() == OpenMode.CREATE) {
                // New index, so we just add the document (no old document can be there):
                writer.addDocument(doc);
            } else {
                // Existing index (an old copy of this document may have been indexed) so
                // we use updateDocument instead to replace the old one matching the exact
                // path, if present:
                writer.updateDocument(new Term("id", Integer.toString(id)), doc);
            }

            if (MAX_LINES_FOR_INDEX > 0 && processedLines >= MAX_LINES_FOR_INDEX) {
                break;
            }
        }

        System.out.println("\n" + Integer.toString(processedLines) + " lines indexed");
    }
}