// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud.docsearch;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.prelude.query.Item;
import com.yahoo.prelude.query.AndItem;
import com.yahoo.prelude.query.OrItem;
import com.yahoo.prelude.query.WordItem;
import com.yahoo.prelude.query.NullItem;
import com.yahoo.prelude.query.NearestNeighborItem;
import com.yahoo.prelude.query.WeakAndItem;
import com.yahoo.prelude.query.RankItem;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;

import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;
import java.util.List;

/**
 * The searcher building the retrieval query logic
 */
public class LLMSearcher extends Searcher {

    private Linguistics linguistics;
    private Embedder embedder;

    private TensorType tensorType = TensorType.fromSpec("tensor<float>(x[384])");

    @Inject
    public LLMSearcher(Linguistics linguistics, ComponentRegistry<Embedder> embedders) {
        this.linguistics = linguistics;
        this.embedder = embedders.getComponent("embedder");
    }

    @Override
    public Result search(Query query, Execution execution) {
        String queryString = query.getModel().getQueryString();
        if(queryString == null || queryString.isBlank())
            return new Result(query);
        queryString = queryString.trim();
        Embedder.Context context = new Embedder.Context("query");
        Tensor embedding = embedder.embed("query: " + queryString, context, tensorType);
        buildQuery(embedding,queryString,query);
        return execution.search(query);
    }

    private void buildQuery(Tensor embedding, String queryStr, Query query) {
        WeakAndItem weakAndItem = new WeakAndItem(100);
        for(String term : Question.tokenize(queryStr, linguistics)) {
            if(!Question.isStopWord(term))
                weakAndItem.addItem(new WordItem(term, true));
        }

        NearestNeighborItem paragraphQuery = new NearestNeighborItem("embedding", "q");
        paragraphQuery.setTargetNumHits(100);
        query.getRanking().getFeatures().put("query(q)", embedding);

        NearestNeighborItem questionQuery = new NearestNeighborItem("question_embedding", "q");
        questionQuery.setTargetNumHits(100);
        query.getRanking().getFeatures().put("query(q)", embedding);

        OrItem hybrid = new OrItem();
        hybrid.addItem(weakAndItem);
        hybrid.addItem(paragraphQuery);
        hybrid.addItem(questionQuery);

        WordItem exact = new WordItem(queryStr,"questions_exact",true);
        RankItem rankItem = new RankItem();

        Item filter = filter(query);
        if(!(filter instanceof NullItem)) {
            AndItem and = new AndItem();
            and.addItem(filter);
            and.addItem(hybrid);
            rankItem.addItem(and);
        } else {
            rankItem.addItem(hybrid);
        }
        rankItem.addItem(exact);
        query.getModel().getQueryTree().setRoot(rankItem);
    }

    public static Item filter(Query query) {
        String filter = query.properties().getString("filters");
        if (filter == null || filter.isEmpty())
            return new NullItem();
        filter = filter.replace("+","");
        String[] terms = filter.split(" ");

        query.trace("terms " + List.of(terms),3);
        if(terms.length == 0)
            return new NullItem();

        OrItem or = new OrItem();
        for(String t: terms) {
            String[] indexFilter = t.split(":");
            if(indexFilter.length != 2)
                continue;
            String field = indexFilter[0];
            String value = indexFilter[1];
            WordItem w = new WordItem(value,field, true);
            or.addItem(w);
        }
        query.trace("Query tree filter: " + or, 3);
        if (or.getItemCount() == 0)
            return new NullItem();
        else return or;
    }

}
