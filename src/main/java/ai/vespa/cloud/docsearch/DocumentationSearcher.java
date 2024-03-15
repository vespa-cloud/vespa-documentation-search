// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud.docsearch;

import com.yahoo.component.annotation.Inject;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.query.FuzzyItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.prelude.query.Item;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;

import java.util.ArrayList;
import java.util.List;

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

    private static final CompoundName SUGGEST_ONLY = new CompoundName("suggestions-only");
    private static final CompoundName SOURCE = new CompoundName("index-source");

    private Linguistics linguistics;
    @Inject
    public DocumentationSearcher(Linguistics linguistics) {
        this.linguistics = linguistics;
    }

    @Override
    public Result search(Query query, Execution execution) {
        String userQuery = query.properties().getString("term");
        if (userQuery == null) return execution.search(query);

        List<String> tokens = tokenize(userQuery);
        Result suggestions = getSuggestions(userQuery, tokens, execution, query);
        if(query.properties().getBoolean(SUGGEST_ONLY,false))
            return suggestions;

        String index = query.properties().getString(SOURCE, "doc");
        query.getModel().setRestrict(index);
        WeakAndItem weakAndItem = new WeakAndItem();
        for (String term: suggestions.getHitCount() > 0 ? suggestedTerms(suggestions) : tokens)
            weakAndItem.addItem(new WordItem(term, true));
        query.getModel().getQueryTree().setRoot(weakAndItem);
        query.getRanking().setProfile("documentation");
        Result result = execution.search(query);
        result.getQuery().setHits(20);
        result.hits().addAll(suggestions.hits().asList());
        return result;
    }

    private List<String> tokenize(String userQuery) {
        List<String> result = new ArrayList<>(6);
        Iterable<Token> tokens = this.linguistics.getTokenizer().
                tokenize(userQuery, Language.fromLanguageTag("en"), StemMode.NONE,false);
        for(Token t: tokens) {
            if (t.isIndexable())
                result.add(t.getTokenString());
        }
        return result;
    }

    private Result getSuggestions(String userQuery, List<String> tokens, Execution execution, Query originalQuery) {
        Query query = originalQuery.clone();
        query.getPresentation().setSummary(SUGGESTION_SUMMARY);
        query.setHits(10);
        query.getModel().setRestrict("term");
        query.getRanking().setProfile(SUGGESTION_RANK_PROFILE);

        Item suggestionQuery = buildSuggestionQueryTree(userQuery, tokens);

        Item root = query.getModel().getQueryTree().getRoot();
        AndItem andItem = new AndItem();
        andItem.addItem(root);
        andItem.addItem(suggestionQuery);
        query.getModel().getQueryTree().setRoot(andItem);
        if(tokens.size() == 1)
            query.getRanking().getFeatures().put("query(matchWeight)", 0.2);
        Result suggestionResult = execution.search(query);
        execution.fill(suggestionResult, SUGGESTION_SUMMARY);
        return suggestionResult;
    }

    private Item buildSuggestionQueryTree(String userQuery, List<String> tokens) {
        PrefixItem prefix = new PrefixItem(userQuery, "default");
        OrItem relaxedMatching = new OrItem();
        for(String t: tokens) {
            if (Question.isStopWord(t))
                continue;
            int length = t.length();
            if(length <= 3) {
                WordItem word = new WordItem(t, "tokens", true);
                relaxedMatching.addItem(word);
            } else {
                int maxDistance = 1;
                FuzzyItem fuzzyItem = new FuzzyItem("tokens",
                        true, t, maxDistance, 2);
                relaxedMatching.addItem(fuzzyItem);
            }
        }
        WordItem boost = new WordItem("vespa", "tokens", true);
        RankItem vespa = new RankItem();
        if(relaxedMatching.getItemCount() == 0) {
            vespa.addItem(prefix);
            vespa.addItem(boost);
            return vespa;
        }
        OrItem orItem = new OrItem();
        orItem.addItem(prefix);
        orItem.addItem(relaxedMatching);
        vespa.addItem(orItem);
        vespa.addItem(boost);
        return vespa;
    }

    private List<String> suggestedTerms(Result suggestionResult) {
        Hit topHit = suggestionResult.hits().get(0);
        if (topHit.getField("term") == null)
            throw new IllegalStateException("Suggestion result unexpectedly missing 'term' field");
        return tokenize(topHit.getField("term").toString());
    }
}

