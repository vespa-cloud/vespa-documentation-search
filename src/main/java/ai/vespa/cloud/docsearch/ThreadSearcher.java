package ai.vespa.cloud.docsearch;

import com.yahoo.component.annotation.Inject;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.*;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.GroupList;
import com.yahoo.search.grouping.result.RootGroup;
import com.yahoo.search.result.Hit;
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
        Result result = threadedMessageSearcher.search(query, execution);
        execution.fill(result);

        RootGroup resultGroup = request.getResultGroup(result);

        if (resultGroup == null) return result;

        Result newResult = new Result(query);

        for (Hit h : resultGroup.getGroupList("thread_id")) {
            var thread = (Group) h;
            Hit hit = new Hit(thread.getGroupId().toString().substring("group:string:".length()));
            for (Hit h2 : thread.getGroupList("threaded_message_id")) {
                var message_id = (Group) h2;
                for (Hit h3 : message_id.getGroupList("text")) {
                    hit.setField(message_id.getDisplayId().substring("group:string:".length()), h3.getDisplayId().substring("group:string:".length()));
                }
            }
            if (hit.fields().values().size() > 1) {
                newResult.hits().add(hit);
            }
        }

        return newResult;
    }
}
