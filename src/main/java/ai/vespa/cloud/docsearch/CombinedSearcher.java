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
    private final LLMSearcher llmSearcher;

    @Inject
    public CombinedSearcher(ThreadSearcher threadSearcher, LLMSearcher llmSearcher) {
        this.threadSearcher = threadSearcher;
        this.llmSearcher = llmSearcher;
    }

    @Override
    public Result search(Query query, Execution execution) {
        Result paragraphResult = llmSearcher.search(query, execution);
        Result result = threadSearcher.search(query, execution);
        for (Hit hit : result.hits()) {
            hit.setField("hello", paragraphResult.hits().size());
        }

        for (Hit hit : paragraphResult.hits()) {
            result.hits().add(hit);
        }
        return result;
    }
}
