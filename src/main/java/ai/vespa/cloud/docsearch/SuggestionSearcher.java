// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud.docsearch;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;

import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.FuzzyItem;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.query.PrefixItem;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.NearestNeighborItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import java.util.List;

import static ai.vespa.cloud.docsearch.LLMSearcher.filter;
/**
 * Powers search suggestion api for search.vespa.ai
 */

public class SuggestionSearcher extends Searcher {

    private static final String SUGGESTION_SUMMARY = "suggestion";
    private static final String SUGGESTION_RANK_PROFILE = "term_rank";

    private final Embedder embedder;

    private final TensorType tensorType = TensorType.fromSpec("tensor<float>(x[384])");

    private final Linguistics linguistics;
    @Inject
    public SuggestionSearcher(Linguistics linguistics,  ComponentRegistry<Embedder> embedders) {
        this.linguistics = linguistics;
        this.embedder = embedders.getComponent("embedder");
    }

    @Override
    public Result search(Query query, Execution execution) {
        String queryString = query.getModel().getQueryString();
        if(queryString == null || queryString.isBlank())
            return new Result(query);
        queryString = queryString.trim();
        List<String> tokens = Question.tokenize(queryString, linguistics);
        query.getPresentation().setSummary(SUGGESTION_SUMMARY);
        query.getModel().setRestrict("term");
        query.getRanking().setProfile(SUGGESTION_RANK_PROFILE);
        Item suggestionQuery = buildSuggestionQueryTree(queryString, tokens, query);
        Item filterItem = filter(query);
        if(filterItem instanceof NullItem) {
            query.getModel().getQueryTree().setRoot(suggestionQuery);
        }else {
            AndItem andItem = new AndItem();
            andItem.addItem(filterItem);
            andItem.addItem(suggestionQuery);
            query.getModel().getQueryTree().setRoot(andItem);
        }
        return execution.search(query);
    }
    private Item buildSuggestionQueryTree(String userQuery, List<String> tokens, Query query) {
        PrefixItem prefix = new PrefixItem(userQuery, "default");
        OrItem tokenMatching = new OrItem();
        for(String t: tokens) {
            if (Question.isStopWord(t))
                continue;
            int length = t.length();
            if(length <= 3) {
                WordItem word = new WordItem(t, "tokens", true);
                tokenMatching.addItem(word);
            } else {
                int maxDistance = 1;
                FuzzyItem fuzzyItem = new FuzzyItem("tokens",
                        true, t, maxDistance, 2);
                tokenMatching.addItem(fuzzyItem);
            }
        }
        OrItem orItem = new OrItem();
        orItem.addItem(prefix);
        orItem.addItem(tokenMatching);
        if(tokenMatching.getItemCount() > 3 ) {
            Embedder.Context context = new Embedder.Context("query");
            Tensor embedding = embedder.embed("query: " + userQuery, context, tensorType);
            query.getRanking().getFeatures().put("query(q)",embedding);
            NearestNeighborItem nn = new NearestNeighborItem("embedding", "q");
            nn.setTargetHits(100);
            orItem.addItem(nn);
        }
        return orItem;
    }

}
