// Copyright Yahoo. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud.docsearch;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;
import com.yahoo.prelude.query.*;
import com.yahoo.processing.request.CompoundName;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.result.Hit;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

import java.util.ArrayList;
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
        NearestNeighborItem nn = new NearestNeighborItem("embedding", "q");
        nn.setTargetNumHits(100);
        query.getRanking().getFeatures().put("query(q)", embedding);

        OrItem hybrid = new OrItem();
        hybrid.addItem(weakAndItem);
        hybrid.addItem(nn);

        WordItem exact = new WordItem(queryStr,"questions_exact",true);
        RankItem rankItem = new RankItem();
        rankItem.addItem(hybrid);
        rankItem.addItem(exact);
        query.getModel().getQueryTree().setRoot(rankItem);
    }

}
