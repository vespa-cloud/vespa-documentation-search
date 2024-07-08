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
import com.yahoo.search.result.Hit;
import com.yahoo.tensor.Tensor;
import com.yahoo.tensor.TensorType;

public class ThreadSearcher extends Searcher {

    private final Linguistics linguistics;
    private final Embedder embedder;

    private final TensorType tensorType = TensorType.fromSpec("tensor<float>(x[384])");

    @Inject
    public ThreadSearcher(Linguistics linguistics, ComponentRegistry<Embedder> embedders) {
        this.linguistics = linguistics;
        this.embedder = embedders.getComponent("embedder");
    }

    @Override
    public Result search(Query query, Execution execution) {
        Result firstHit = getFirst(query, execution, 1);
        return firstHit;
        //return searchForThread(firstHit, query, execution);
    }

    private Result searchForThread(Result firstHit, Query query, Execution execution) {
        if (firstHit.getTotalHitCount() <= 0) return firstHit;

        // Uncomment and modify this part if you need to use a thread reference

        execution.fill(firstHit);
        Hit topHit = firstHit.hits().get(0);
        String threadRef = (String) topHit.getField("thread_ref");

        Query threadQuery = new Query();
        threadQuery.getModel().getQueryTree().setRoot(new com.yahoo.prelude.query.WordItem(threadRef, "thread_ref"));
        return execution.search(threadQuery);
    }

    private Result getFirst(Query query, Execution execution, int hits) {
        String queryString = query.getModel().getQueryString();
        if (queryString == null || queryString.isBlank()) {
            return new Result(query);
        }
        queryString = queryString.trim();

        Embedder.Context context = new Embedder.Context("query");
        Tensor embedding = embedder.embed("query: " + queryString, context, tensorType);

        buildQuery(embedding, queryString, query);
        query.setHits(hits);

        return execution.search(query);
    }

    private void buildQuery(Tensor embedding, String queryStr, Query query) {
        WeakAndItem weakAndItem = new WeakAndItem(100);
        for (String term : Question.tokenize(queryStr, linguistics)) {
            if (!Question.isStopWord(term)) {
                weakAndItem.addItem(new WordItem(term, true));
            }
        }

        NearestNeighborItem nearestNeighborItem = new NearestNeighborItem("text_embedding", "q");
        nearestNeighborItem.setTargetNumHits(100);
        query.getRanking().getFeatures().put("query(q)", embedding);

        OrItem hybrid = new OrItem();
        hybrid.addItem(weakAndItem);
        hybrid.addItem(nearestNeighborItem);

        RankItem rankItem = new RankItem();
        rankItem.addItem(hybrid);

        WordItem exact = new WordItem(queryStr, "text", true);
        rankItem.addItem(exact);

        AndItem finalQuery = new AndItem();
        finalQuery.addItem(rankItem);
        var threadId = query.properties().get("threadId");
        if (threadId != null) {
            NotItem notItem = new NotItem();
            notItem.addNegativeItem(new WordItem((String) threadId, "thread_ref", true));
            finalQuery.addItem(notItem);
        }

        query.getModel().getQueryTree().setRoot(finalQuery);
    }
}
