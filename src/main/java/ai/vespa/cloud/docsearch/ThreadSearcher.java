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
import java.util.ArrayList;

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

        var groupList = resultGroup.getGroupList("thread_id");
        if (groupList == null) return result;

        Result newResult = new Result(query);
        for (Hit h : groupList) {
            var thread = (Group) h;
            Hit hit = new Hit(thread.getGroupId().toString().substring("group:string:".length()));
            var docIds = new ArrayList<String>();
            var conversation = new ArrayList<String>();
            for (Hit h2 : thread.getGroupList("threaded_message_id")) {
                var message_id = (Group) h2;
                for (Hit h3 : message_id.getGroupList("text")) {
                    conversation.add(h3.getDisplayId().substring("group:string:".length()));
                }
                docIds.add("id:slack-p:threaded_message::" + message_id.getDisplayId().substring("group:string:".length()));
            }
            hit.setField("conversation", conversation);
            hit.setField("docids", docIds);
            if (hit.fields().values().size() > 1) {
                newResult.hits().add(hit);
            }
        }

        return newResult;
    }
}
