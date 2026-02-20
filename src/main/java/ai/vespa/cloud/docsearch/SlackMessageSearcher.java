package ai.vespa.cloud.docsearch;

import com.yahoo.component.annotation.Inject;
import com.yahoo.component.provider.ComponentRegistry;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.Embedder;
import com.yahoo.prelude.query.*;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

public class SlackMessageSearcher extends Searcher {

    private final Linguistics linguistics;
    private final Embedder embedder;

    private final TensorType tensorType = TensorType.fromSpec("tensor<float>(x[384])");

    @Inject
    public SlackMessageSearcher(Linguistics linguistics, ComponentRegistry<Embedder> embedders) {
        this.linguistics = linguistics;
        this.embedder = embedders.getComponent("embedder");
    }

    @Override
    public Result search(Query query, Execution execution) {
        var root = query.getModel().getQueryTree().getRoot();
        String queryString = query.getModel().getQueryString();
        if (queryString == null || queryString.isBlank()) {
            return new Result(query);
        }
        queryString = queryString.trim();

        Embedder.Context context = new Embedder.Context("query");
        Tensor embedding = embedder.embed("query: " + queryString, context, tensorType);

        buildQuery(embedding, queryString, query);
        Result r =  execution.search(query);
        query.getModel().getQueryTree().setRoot(root);
        return r;
    }

    private void buildQuery(Tensor embedding, String queryStr, Query query) {
        WeakAndItem weakAndItem = new WeakAndItem(Integer.valueOf(100));
        for (String term : Question.tokenize(queryStr, linguistics)) {
            if (!Question.isStopWord(term)) {
                weakAndItem.addItem(new WordItem(term, true));
            }
        }

        NearestNeighborItem nearestNeighborItem = new NearestNeighborItem("text_embedding", "q");
        nearestNeighborItem.setTargetHits(100);
        query.getRanking().getFeatures().put("query(q)", embedding);

        OrItem hybrid = new OrItem();
        hybrid.addItem(weakAndItem);
        hybrid.addItem(nearestNeighborItem);

        RankItem rankItem = new RankItem();
        rankItem.addItem(hybrid);

        WordItem exact = new WordItem(queryStr, "text", true);
        rankItem.addItem(exact);

        query.getModel().getQueryTree().setRoot(rankItem);
    }
}
