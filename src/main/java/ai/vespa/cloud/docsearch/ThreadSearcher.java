package ai.vespa.cloud.docsearch;

import com.yahoo.component.annotation.Inject;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.*;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.GroupList;
import com.yahoo.search.grouping.result.HitList;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
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
        request.setRootOperation(new AllOperation().setGroupBy(new AttributeValue("thread_id"))
                .addChild(new EachOperation().addChild(new EachOperation()
                        .addOutput(new SummaryValue()))));
        Result result = threadedMessageSearcher.search(query, execution);
        execution.fill(result);
        Group root = request.getResultGroup(result);
        GroupList threadIdGroupList = root.getGroupList("thread_id");

        Result newResult = new Result(query);
        for (Hit group : threadIdGroupList) {
            HitGroup hitGroup = new HitGroup(getThreadId((Group)group), group.getRelevance());
            for (Hit hitList : (HitGroup) group) {
                for (Hit hit : (HitList) hitList) {
                    hitGroup.add(hit);
                }
            }
            newResult.hits().add(hitGroup);
        }
        return newResult;
    }

    private String getThreadId(Group group) {
        for (Hit hitList : (HitGroup) group) {
            for (Hit hit : (HitList) hitList) {
                return (String) hit.getField("thread_id");
            }
        }
        return null;
    }
}
