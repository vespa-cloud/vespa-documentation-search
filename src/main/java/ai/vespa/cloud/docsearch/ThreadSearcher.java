package ai.vespa.cloud.docsearch;

import com.yahoo.component.annotation.Inject;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.searchchain.Execution;
import com.yahoo.search.result.Hit;
import com.yahoo.prelude.query.*;

public class ThreadSearcher extends Searcher {

    private final ThreadedMessageSearcher threadedMessageSearcher;

    @Inject
    public ThreadSearcher(ThreadedMessageSearcher threadedMessageSearcher) {
        this.threadedMessageSearcher = threadedMessageSearcher;
    }

    @Override
    public Result search(Query query, Execution execution) {
        query.setHits(1);
        Result result = threadedMessageSearcher.search(query, execution);
        if (result.getTotalHitCount() <= 0) return result;

        execution.fill(result);
        Hit topHit = result.hits().get(0);
        String threadRef = (String) topHit.getField("thread_ref");

        Query threadQuery = new Query();
        threadQuery.getModel().getQueryTree().setRoot(new WordItem(threadRef, "thread_ref"));
        return execution.search(threadQuery);
    }
}
