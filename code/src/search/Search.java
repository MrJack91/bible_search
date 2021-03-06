package search;
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
 *
 *
 * - Build artifact
 * - run in code folder: java -jar out/artifacts/bible_search_jar/bible_search.jar
 *
 */


import org.apache.lucene.analysis.Analyzer;
import org.apache.lucene.analysis.TokenStream;
import org.apache.lucene.analysis.de.GermanAnalyzer;
import org.apache.lucene.document.Document;
import org.apache.lucene.index.DirectoryReader;
import org.apache.lucene.index.IndexReader;
import org.apache.lucene.index.Term;
import org.apache.lucene.queryparser.classic.MultiFieldQueryParser;
import org.apache.lucene.queryparser.classic.QueryParser;
import org.apache.lucene.search.IndexSearcher;
import org.apache.lucene.search.Query;
import org.apache.lucene.search.ScoreDoc;
import org.apache.lucene.search.TopDocs;
import org.apache.lucene.search.highlight.*;
import org.apache.lucene.search.spell.*;
import org.apache.lucene.store.Directory;
import org.apache.lucene.store.FSDirectory;
import utility.AnsiColor;
import utility.Books;

import javax.management.loading.MLet;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.Date;

/**
 * Simple command-line based search demo.
 */
public class Search {

    protected static Books books;

    protected static Analyzer analyzer;
    protected static IndexReader reader;

    /** searchable field */
    protected static String field = "content";

    private Search() {}

    /**
     * Simple command-line based search demo.
     */
    public static void main(String[] args) throws Exception {
        String usage =
                "Usage:\tjava org.apache.lucene.demo.SearchFiles [-index dir] [-field f] [-repeat n] [-queries file] [-query string] [-raw] [-paging hitsPerPage]\n\nSee http://lucene.apache.org/core/4_1_0/demo/ for details.";
        if (args.length > 0 && ("-h".equals(args[0]) || "-help".equals(args[0]))) {
            System.out.println(usage);
            System.exit(0);
        }

        String index = "index";

        String queries = null;
        int repeat = 0;
        boolean raw = false;
        String queryString = null;
        int hitsPerPage = 10;

        for (int i = 0; i < args.length; i++) {
            if ("-index".equals(args[i])) {
                index = args[i + 1];
                i++;
            } else if ("-field".equals(args[i])) {
                field = args[i + 1];
                i++;
            } else if ("-queries".equals(args[i])) {
                queries = args[i + 1];
                i++;
            } else if ("-query".equals(args[i])) {
                queryString = args[i + 1];
                i++;
            } else if ("-repeat".equals(args[i])) {
                repeat = Integer.parseInt(args[i + 1]);
                i++;
            } else if ("-raw".equals(args[i])) {
                raw = true;
            } else if ("-paging".equals(args[i])) {
                hitsPerPage = Integer.parseInt(args[i + 1]);
                if (hitsPerPage <= 0) {
                    System.err.println("There must be at least 1 hit per page.");
                    System.exit(1);
                }
                i++;
            }
        }

        // load books
        books = new Books();

        Directory dir = FSDirectory.open(Paths.get(index));
        reader = DirectoryReader.open(dir);
        IndexSearcher searcher = new IndexSearcher(reader);
        analyzer = new GermanAnalyzer();

        BufferedReader in = null;
        if (queries != null) {
            in = Files.newBufferedReader(Paths.get(queries), StandardCharsets.UTF_8);
        } else {
            in = new BufferedReader(new InputStreamReader(System.in, StandardCharsets.UTF_8));
        }
        // QueryParser parser = new QueryParser(field, analyzer);
        MultiFieldQueryParser parser = new MultiFieldQueryParser(new String[]{field, "context"}, analyzer);
        parser.setDefaultOperator(QueryParser.Operator.OR);

        while (true) {
            if (queries == null && queryString == null) {
                // prompt the user
                System.out.println(AnsiColor.ANSI_PURPLE + new String(new char[100]).replace("\0", "*"));
                System.out.println(AnsiColor.ANSI_PURPLE + "Enter query: " + AnsiColor.ANSI_RESET);
            }

            String line = queryString != null ? queryString : in.readLine();

            if (line == null || line.length() == -1) {
                break;
            }

            line = line.trim();
            if (line.length() == 0) {
                break;
            }

            Query query = parser.parse(line);
            System.out.println(AnsiColor.ANSI_BLUE + "Searching for: " + AnsiColor.ANSI_YELLOW + line + AnsiColor.ANSI_RESET);
            System.out.println(AnsiColor.ANSI_BLUE + "Real query: " + AnsiColor.ANSI_YELLOW + query.toString(field) + AnsiColor.ANSI_RESET);

            if (repeat > 0) {
                // repeat & time as benchmark
                Date start = new Date();
                for (int i = 0; i < repeat; i++) {
                    searcher.search(query, 100);
                }
                Date end = new Date();
                System.out.println("Time: " + (end.getTime() - start.getTime()) + "ms");
            }

            doPagingSearch(in, searcher, query, hitsPerPage, raw, queries == null && queryString == null);

            if (queryString != null) {
                break;
            }
        }
        reader.close();
    }

    /**
     * This demonstrates a typical paging search scenario, where the search engine presents
     * pages of size n to the user. The user can then go to the next page if interested in
     * the next hits.
     * <p>
     * When the query is executed for the first time, then only enough results are collected
     * to fill 5 result pages. If the user wants to page beyond this limit, then the query
     * is executed another time and all hits are collected.
     */
    public static void doPagingSearch(BufferedReader in, IndexSearcher searcher, Query query, int hitsPerPage, boolean raw, boolean interactive) throws IOException {

        // Collect enough docs to show 5 pages
        TopDocs results = searcher.search(query, 5 * hitsPerPage);
        ScoreDoc[] hits = results.scoreDocs;

        int numTotalHits = results.totalHits;

        int start = 0;
        int end = Math.min(numTotalHits, hitsPerPage);

        // there are no entries, show spellchecker if available
        DirectSpellChecker directSpellChecker = new DirectSpellChecker();

        String[] searchWords = query.toString(field).split(" ");
        ArrayList<String> didYouMeanWords = new ArrayList<>();;
        boolean foundAlternative = false;
        for (String searchWord : searchWords) {
            if (searchWord.length() > 8 && searchWord.substring(0, 8).equals("context:")) {
                continue;
            }
            searchWord = searchWord.replace("(", "");

            SuggestWord[] suggestions = directSpellChecker.suggestSimilar(new Term(field, searchWord), 1, reader, SuggestMode.SUGGEST_MORE_POPULAR);
            String didYouMean = searchWord;
            if (suggestions.length > 0) {
                didYouMean = suggestions[0].string;
                foundAlternative = true;
            }
            didYouMeanWords.add(didYouMean);
        }
        if (foundAlternative) {
            System.out.println(AnsiColor.ANSI_BLUE + "Did you mean: " + AnsiColor.ANSI_RESET + AnsiColor.ANSI_CYAN + String.join(" ", didYouMeanWords) + AnsiColor.ANSI_RESET);
        } else {
            if (!interactive || end == 0) {
                System.out.println(AnsiColor.ANSI_BLUE  + "Sorry, there are also no similar words known" + AnsiColor.ANSI_RESET);
            }
        }


        System.out.println(AnsiColor.ANSI_YELLOW + numTotalHits + AnsiColor.ANSI_BLUE + " total matching documents" + AnsiColor.ANSI_RESET);

        // highlighting - src: http://makble.com/how-to-do-lucene-search-highlight-example
        SimpleHTMLFormatter htmlFormatter = new SimpleHTMLFormatter(AnsiColor.ANSI_YELLOW, AnsiColor.ANSI_RESET);

        QueryScorer queryScorer = new QueryScorer(query);
        Highlighter highlighter = new Highlighter(htmlFormatter, queryScorer);

        while (true) {
            if (end > hits.length) {
                System.out.println("Only results 1 - " + hits.length + " of " + numTotalHits + " total matching documents collected.");
                System.out.println("Collect more (y/n) ?");
                String line = in.readLine();
                if (line.length() == 0 || line.charAt(0) == 'n') {
                    break;
                }
                // load all
                hits = searcher.search(query, numTotalHits).scoreDocs;
            }

            end = Math.min(hits.length, start + hitsPerPage);

            for (int i = start; i < end; i++) {
                if (raw) {
                    // output raw format
                    System.out.println("doc=" + hits[i].doc + " score=" + hits[i].score);
                    continue;
                }

                Document doc = searcher.doc(hits[i].doc);

                String content = doc.get(field);

                // highlight keywords
                TokenStream tokenStream = TokenSources.getTokenStream(field, reader.getTermVectors(i), content, new GermanAnalyzer(), -1);
                TextFragment[] frag = new TextFragment[0];
                try {
                    frag = highlighter.getBestTextFragments(tokenStream, content, false, 50);
                } catch (InvalidTokenOffsetsException e) {
                    e.printStackTrace();
                }

                // make sure, that the sentence order is always correctly
                Arrays.sort(frag, Comparator.comparing(TextFragment::getFragNum));

                String contentHighlighted = "";
                for (int j = 0; j < frag.length; j++) {
                    // if ((frag[j] != null) && (frag[j].getScore() > 0)) {
                    if (frag[j] != null) {
                        contentHighlighted += frag[j].toString();
                    }
                }

                String versText = doc.get("verse");
                int verseLength = Integer.parseInt(doc.get("verseLength"));
                if (verseLength > 1) {
                    versText += "-" + Integer.toString(Integer.parseInt(doc.get("verse")) + verseLength-1);
                }
                String resultLine = AnsiColor.ANSI_BLUE + (i + 1) + ".\t" + AnsiColor.ANSI_RESET
                        + contentHighlighted
                        + " - " + books.getBookNameAbr().get(Integer.parseInt(doc.get("book")))
                        + " " + doc.get("chapter") + "," + versText
                        // + "\n\t\tC: " + doc.get("context")
                ;
                System.out.println(resultLine);
            }

            if (!interactive || end == 0) {
                break;
            }

            if (numTotalHits >= end) {
                boolean quit = false;
                while (true) {
                    System.out.print(AnsiColor.ANSI_PURPLE + "Press ");
                    if (start - hitsPerPage >= 0) {
                        System.out.print("(p)revious page, ");
                    }
                    if (start + hitsPerPage < numTotalHits) {
                        System.out.print("(n)ext page, ");
                    }
                    System.out.println("(q)uit or enter number to jump to a page:" + AnsiColor.ANSI_RESET);

                    String line = in.readLine().trim();
                    if (line.length() == 0 || line.charAt(0) == 'q') {
                        quit = true;
                        break;
                    }
                    if (line.charAt(0) == 'p') {
                        start = Math.max(0, start - hitsPerPage);
                        break;
                    } else if (line.charAt(0) == 'n') {
                        if (start + hitsPerPage < numTotalHits) {
                            start += hitsPerPage;
                        }
                        break;
                    } else {
                        int page = 0;
                        try {
                            page = Integer.parseInt(line);
                        } catch (NumberFormatException e) {

                        }

                        if (page > 0 && (page - 1) * hitsPerPage < numTotalHits) {
                            start = (page - 1) * hitsPerPage;
                            break;
                        } else {
                            System.out.println(AnsiColor.ANSI_RED + "No such page" + AnsiColor.ANSI_RESET);
                        }
                    }
                }
                if (quit) break;
                end = Math.min(numTotalHits, start + hitsPerPage);
            }
        }
    }
}
