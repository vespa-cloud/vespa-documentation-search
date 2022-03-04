package ai.vespa.cloud.docsearch;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.hosted.cd.Endpoint;
import ai.vespa.hosted.cd.StagingTest;
import ai.vespa.hosted.cd.TestRuntime;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;

@StagingTest
public class VespaDocStagingTest {

    final Endpoint endpoint = TestRuntime.get().deploymentToTest().endpoint("default");

    @Test
    @DisplayName("Verify documents, and further feeding, after upgrade")
    public void verifySearchThenUpdateAndRemove(TestReporter report) throws Exception {
        VespaDocTester tester = new VespaDocTester(endpoint, report);

        Set<DocumentId> ids = tester.getTestDocIDs();
        for (DocumentId id : ids) report.publishEntry(id.toString());
        assertEquals(5, ids.size(), "test-documents.json should have 5 documents");

        String allQuery = "select * from sources * where sddocname contains \"doc\";";
        tester.verifyQueryResults(ids, allQuery, "5s"); // Use a high timeout for first query

        String accessQuery = "select * from sources * where content contains \"access\";";
        Set<DocumentId> expectedAccessHits = Set.of(DocumentId.of("id:open:doc::open/documentation/access-logging.html"),
                                                    DocumentId.of("id:open:doc::open/documentation/content/api-state-rest-api.html"),
                                                    DocumentId.of("id:open:doc::open/documentation/operations/admin-procedures.html"));
        tester.verifyQueryResults(expectedAccessHits, accessQuery, "500ms");

        assertEquals(1, tester.countInLinks(DocumentId.of("id:open:doc::open/documentation/access-logging.html")));
        assertEquals(2, tester.countInLinks(DocumentId.of("id:open:doc::open/documentation/operations/admin-procedures.html")));

        tester.updateTestDocs();

        ids = tester.getTestDocIDs();
        for (DocumentId id : ids) report.publishEntry(id.toString());
        assertEquals(5, ids.size(), "test-documents-updates.json should have the same 5 documents");

        tester.removeTestDocs(ids);
        ids = tester.getTestDocIDs();
        assertEquals(0, ids.size(), "no documents should remain");
    }

}
