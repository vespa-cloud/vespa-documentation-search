package ai.vespa.cloud.docsearch;

import com.yahoo.component.chain.Chain;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import org.junit.jupiter.api.Test;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * Tests the documentation searcher.
 *
 * @author bratseth
 */
public class DocumentationSearcherTest {

    @Test
    public void testNoMatches() {
        Result result = search("foo", null,
                               List.of(), List.of());
        assertEquals(0, result.getHitCount());
    }

    @Test
    public void testSuggestionsAndMatches() {
        Hit suggestion1 = new Hit("suggestion1", 0.8);
        suggestion1.setField("term", "foo bar");
        Hit suggestion2 = new Hit("suggestion2", 0.9);
        suggestion2.setField("term", "foo baz");
        Hit doc1 = new Hit("document1", 0.9);
        Hit doc2 = new Hit("document2", 0.7);
        Hit doc3 = new Hit("document2", 0.6);

        Result result = search("foo", "foo baz",
                               List.of(suggestion1, suggestion2), List.of(doc1, doc2, doc3));
        assertEquals(5, result.getHitCount());
    }

    @Test
    public void testMatches() {
        Hit doc1 = new Hit("document1", 0.9);
        Hit doc2 = new Hit("document2", 0.7);
        Hit doc3 = new Hit("document2", 0.6);

        Result result = search("foo", null,
                               List.of(), List.of(doc1, doc2, doc3));
        assertEquals(3, result.getHitCount());
    }

    private Result search(String userQuery, String expectedSuggestion, List<Hit> suggestions, List<Hit> documents) {
        var source = new DocumentSourceSearcher();
        source.addResult(suggestionsQuery(userQuery), suggestions);
        if (expectedSuggestion == null)
            source.addResult(documentsQuery(userQuery, 10), documents);
        else
            source.addResult(documentsQuery(expectedSuggestion, 20), documents);

        Chain<Searcher> chain = new Chain<>(new DocumentationSearcher(), source);
        return new Execution(chain, Execution.Context.createContextStub())
                .search(new Query("?term=" + userQuery));
    }

    /** Creates the suggestion query expected to be created by DocumentationSearcher for these arguments */
    private Query suggestionsQuery(String userQuery) {
        Query query = new Query();
        query.getModel().setRestrict("term");
        query.getModel().getQueryTree().setRoot(new PrefixItem(userQuery, "default"));
        query.getRanking().setProfile("term_rank");
        return query;
    }

    /** Creates the document query expected to be created by DocumentationSearcher for these arguments */
    private Query documentsQuery(String userQuery, int hits) {
        Query query = new Query();
        query.setHits(hits);
        query.getModel().setRestrict("doc");
        WeakAndItem weakAnd = new WeakAndItem();
        for (String token : userQuery.split(" "))
            weakAnd.addItem(new WordItem(token, true));
        query.getModel().getQueryTree().setRoot(weakAnd);
        query.getRanking().setProfile("documentation");
        return query;
    }

    /** A searcher which returns memorized results for particular queries */
    private static class DocumentSourceSearcher extends Searcher {

        private final Map<Query, Result> results = new HashMap<>();

        public DocumentSourceSearcher addResult(Query query, List<Hit> hits) {
            Result result = new Result(query);
            result.hits().addAll(hits);
            results.put(query, result);
            return this;
        }

        @Override
        public Result search(Query query, Execution execution) {
            var result = results.get(query);
            if (result == null) {
                throw new IllegalArgumentException("No memorized result for\n" + query.toDetailString() +
                                                   "\nMemorized queries:\n" +
                                                   results.keySet()
                                                          .stream()
                                                          .map(Query::toDetailString)
                                                          .collect(Collectors.joining("\n")));
            }
            return result;
        }

    }

}
