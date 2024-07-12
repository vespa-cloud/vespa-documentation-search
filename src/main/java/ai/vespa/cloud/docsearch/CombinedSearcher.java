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

public class CombinedSearcher extends Searcher {

    private final ThreadSearcher threadSearcher;

    @Inject
    public CombinedSearcher(ThreadSearcher threadSearcher) {
        this.threadSearcher = threadSearcher;
    }

    @Override
    public Result search(Query query, Execution execution) {
        Result result = threadSearcher.search(query, execution);
        return result;
    }
}
