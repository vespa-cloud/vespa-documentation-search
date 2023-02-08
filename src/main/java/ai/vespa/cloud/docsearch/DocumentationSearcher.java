// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud.docsearch;

import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

/**
 * Searches for suggestions, and returns a result containing both the suggestions
 * and the documents matching the most relevant suggestion. Return up to 5 suggestions and up to 10 document results.
 * This is activated by passing the property term, containing a (partially typed)
 * user query string.
 */
public class DocumentationSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        String userQuery = query.properties().getString("term");
        if (userQuery == null) return execution.search(query);

        Result suggestions = getSuggestions(userQuery, execution);
        query.getModel().setRestrict("doc");
        WeakAndItem weakAndItem = new WeakAndItem();
        for (String term: suggestions.getHitCount() > 0 ? suggestedTerms(suggestions) : userQuery.split(" "))
            weakAndItem.addItem(new WordItem(term, true));
        query.getModel().getQueryTree().setRoot(weakAndItem);
        query.getRanking().setProfile("documentation");
        Result result = execution.search(query);
        result.getQuery().setHits(15);
        result.hits().addAll(suggestions.hits().asList());
        return result;
    }

    private Result getSuggestions(String userQuery, Execution execution) {
        Query query = new Query();
        query.setHits(5);
        query.getModel().setRestrict("term");
        query.getModel().getQueryTree().setRoot(new PrefixItem(userQuery, "default"));
        query.getRanking().setProfile("term_rank");
        Result suggestionResult = execution.search(query);
        execution.fill(suggestionResult);
        return suggestionResult;
    }

    private String[] suggestedTerms(Result suggestionResult) {
        Hit topHit = suggestionResult.hits().get(0);
        if (topHit.fields().get("term") == null)
            throw new IllegalStateException("Suggestion result unexpectedly missing 'term' field");
        return topHit.getField("term").toString().split(" ");
    }

}
