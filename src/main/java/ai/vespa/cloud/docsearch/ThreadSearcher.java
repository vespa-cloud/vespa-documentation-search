package ai.vespa.cloud.docsearch;

import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.result.Hit;

public class ThreadSearcher extends Searcher {

    @Override
    public Result search(Query query, Execution execution) {
        query.setHits(1);
        Result mostSimilar = execution.search(query);
        if (mostSimilar.getTotalHitCount() <= 0) return mostSimilar;

        execution.fill(mostSimilar);

        Hit topHit = mostSimilar.hits().get(0);

        String threadRef = (String) topHit.getField("thread_ref");

        Query threadQuery = new Query();
        threadQuery.getModel().getQueryTree().setRoot(new com.yahoo.prelude.query.WordItem(threadRef, "thread_ref"));
        return execution.search(threadQuery);
    }
}
