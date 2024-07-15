package ai.vespa.cloud.docsearch;

import com.yahoo.component.annotation.Inject;
import com.yahoo.processing.request.ErrorMessage;
import com.yahoo.search.Query;
import com.yahoo.search.Result;
import com.yahoo.search.Searcher;
import com.yahoo.search.grouping.GroupingRequest;
import com.yahoo.search.grouping.request.*;
import com.yahoo.search.grouping.result.Group;
import com.yahoo.search.grouping.result.GroupList;
import com.yahoo.search.grouping.result.HitList;
import com.yahoo.search.grouping.result.RootGroup;
import com.yahoo.search.grouping.request.RelevanceValue;
import com.yahoo.search.result.Hit;
import com.yahoo.search.result.HitGroup;
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
        request.setRootOperation(new AllOperation().setGroupBy(new AttributeValue("thread_id"))
                .addChild(new EachOperation().addChild(new EachOperation()
                        .addOutput(new SummaryValue()))));
        Result result = threadedMessageSearcher.search(query, execution);
        Group root = request.getResultGroup(result);
        GroupList thread_id = root.getGroupList("thread_id");

        Result newResult = new Result(query);
        for (Hit hit : thread_id) {
            HitGroup hitGroup = new HitGroup(getThreadId(hit), hit.getRelevance());
            for (Hit hit2 : (HitGroup) hit) {
                for (Hit hit3 : (HitList) hit2) {
                    hitGroup.add(hit3);
                }
            }
            newResult.hits().add(hitGroup);
        }
        return newResult;
    }

    private String getThreadId(Hit hit) {
        for (Hit hit2 : (HitGroup) hit) {
            for (Hit hit3 : (HitList) hit2) {
                return (String) hit3.getField("thread_ref");
            }
        }
        return null;
    }
}
