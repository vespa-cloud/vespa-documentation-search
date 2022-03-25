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
 * and the documents matching the most relevant suggestion.
 * This is activated by passing the property term, containing a (partially typed)
 * user query string.
 */
public class DocumentationSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        String userQuery = query.properties().getString("term");
        if (userQuery == null) return execution.search(query);

        Result suggestions = getSuggestions(userQuery, execution);
        Query docQuery = new Query();
        docQuery.getModel().setRestrict("doc");
        WeakAndItem weakAndItem = new WeakAndItem();
        for (String term: suggestions.getHitCount() > 0 ? suggestedTerms(suggestions) : userQuery.split(" "))
            weakAndItem.addItem(new WordItem(term, true));
        docQuery.getModel().getQueryTree().setRoot(weakAndItem);
        docQuery.getRanking().setProfile("documentation");
        Result result = execution.search(docQuery);
        result.hits().addAll(suggestions.hits().asList());
        return result;
    }

    private Result getSuggestions(String userQuery, Execution execution) {
        Query query = new Query();
        query.getModel().setRestrict("term");
        query.getModel().getQueryTree().setRoot(new PrefixItem(userQuery, "default"));
        query.getRanking().setProfile("term_rank");
        query.setHits(10);
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
