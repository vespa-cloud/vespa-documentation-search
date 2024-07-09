package ai.vespa.cloud.docsearch;

import com.yahoo.component.annotation.Inject;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.*;
import com.yahoo.search.searchchain.Execution;

public class ThreadSearcher extends Searcher {

    private final ThreadedMessageSearcher threadedMessageSearcher;

    @Inject
    public ThreadSearcher(ThreadedMessageSearcher threadedMessageSearcher) {
        this.threadedMessageSearcher = threadedMessageSearcher;
    }

    @Override
    public Result search(Query query, Execution execution) {
        GroupingRequest request = GroupingRequest.newInstance(query);
        request.setRootOperation(
                new AllOperation().setGroupBy(new AttributeValue("thread_id"))
                        .addChild(new EachOperation()
                                .addChild(
                                        new AllOperation()
                                                .setGroupBy(new AttributeValue("threaded_message_id"))
                                                .addOrderBy(new MaxAggregator(new AttributeValue("threaded_message_id")))
                                                .addChild(new EachOperation()
                                                        .addChild(new AllOperation()
                                                                .setGroupBy(new AttributeValue("text")).addChild(new EachOperation().addOutput(new CountAggregator())))))));
        return threadedMessageSearcher.search(query, execution);
    }
}
