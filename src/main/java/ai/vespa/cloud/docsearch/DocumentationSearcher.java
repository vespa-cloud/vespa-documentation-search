// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud.docsearch;

import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.query.FuzzyItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * Searches for suggestions, and returns a result containing both the suggestions
 * and the documents matching the most relevant suggestion. Return up to 10 suggestions and up to 10 document results.
 *
 * This searcher is activated by passing the property term, containing a (partially typed)
 * user query string.
 */
public class DocumentationSearcher extends Searcher {

    private static final String SUGGESTION_SUMMARY = "suggestion";
    private static final String SUGGESTION_RANK_PROFILE = "term_rank";

    @Override
    public Result search(Query query, Execution execution) {
        String userQuery = query.properties().getString("term");
        if (userQuery == null) return execution.search(query);

        Result suggestions = getSuggestions(userQuery, execution, query);
        query.getModel().setRestrict("doc");
        WeakAndItem weakAndItem = new WeakAndItem();
        for (String term: suggestions.getHitCount() > 0 ? suggestedTerms(suggestions) : userQuery.split(" "))
            weakAndItem.addItem(new WordItem(term, true));
        query.getModel().getQueryTree().setRoot(weakAndItem);
        query.getRanking().setProfile("documentation");
        Result result = execution.search(query);
        result.getQuery().setHits(20);
        result.hits().addAll(suggestions.hits().asList());
        return result;
    }

    private Result getSuggestions(String userQuery, Execution execution, Query originalQuery) {
        Query query = new Query();
        query.getPresentation().setSummary(SUGGESTION_SUMMARY);
        originalQuery.attachContext(query);
        query.setHits(10);
        query.getModel().setRestrict("term");
        query.getRanking().setProfile(SUGGESTION_RANK_PROFILE);

        Item suggestionQuery = buildSuggestionQueryTree(userQuery);
        query.getModel().getQueryTree().setRoot(suggestionQuery);

        Result suggestionResult = execution.search(query);
        execution.fill(suggestionResult, SUGGESTION_SUMMARY);
        return suggestionResult;
    }

    private Item buildSuggestionQueryTree(String userQuery) {
        PrefixItem prefix = new PrefixItem(userQuery, "default");
        int maxDistance = 1;
        int length = userQuery.length();
        // Allow higher distance for longer queries
        if(length > 6)
            maxDistance = 2;
        else if(length > 12)
            maxDistance = 3;
        FuzzyItem fuzzyItem = new FuzzyItem("terms",
                true, userQuery, maxDistance,2);
        OrItem orItem = new OrItem();
        orItem.addItem(prefix);
        orItem.addItem(fuzzyItem);
        return orItem;
    }

    private String[] suggestedTerms(Result suggestionResult) {
        Hit topHit = suggestionResult.hits().get(0);
        if (topHit.getField("term") == null)
            throw new IllegalStateException("Suggestion result unexpectedly missing 'term' field");
        return topHit.getField("term").toString().split(" ");
    }
}
