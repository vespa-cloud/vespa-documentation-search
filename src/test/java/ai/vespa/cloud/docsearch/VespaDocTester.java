// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.
package ai.vespa.cloud.docsearch;

import ai.vespa.feed.client.DocumentId;
import ai.vespa.hosted.cd.Endpoint;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.TestReporter;

import java.io.IOException;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.Collection;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;

import static java.net.URLEncoder.encode;
import static java.net.http.HttpRequest.BodyPublishers.ofString;
import static java.nio.charset.StandardCharsets.UTF_8;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;

class VespaDocTester {

    private final ObjectMapper mapper = new ObjectMapper();
    private final Endpoint endpoint;
    private final TestReporter report;

    VespaDocTester(Endpoint endpoint, TestReporter report) {
        this.endpoint = endpoint;
        this.report = report;
    }

    int countInLinks(DocumentId docId) throws IOException {
        JsonNode inLinks = mapper.readTree(getTestDoc(docId)).get("fields").get("inlinks");
        return inLinks == null ? 0 : inLinks.size();
    }

    void removeAllTestDocs() throws IOException {
        String cluster = "documentation";
        report.publishEntry("Removing all documents from cluster '" + cluster + "'");

        HttpRequest.Builder request = endpoint.request("/document/v1/",
                                                       Map.of("cluster", cluster,
                                                                  "selection", "true"))
                                              .timeout(Duration.ofMinutes(1))
                                              .DELETE();
        HttpResponse<String> response = endpoint.send(request);

        assertEquals(200, response.statusCode(),
                     "Status code should be 200.");
        assertFalse(mapper.readTree(response.body()).has("continuation"),
                    "All documents should be purged within a minute.");

        report.publishEntry("All documents removed");
    }

    void removeTestDocs(Collection<DocumentId> ids) {
        report.publishEntry("Removing " + ids.size() + " documents");
        for (DocumentId id : ids) removeTestDoc(id);
        report.publishEntry("Done removing documents");
    }

    /**
     * Feed Test documents using Vespa Put
     * @see <a href="https://docs.vespa.ai/en/reference/document-v1-api-reference.html">document-v1-api-reference</a>
     */
    void feedTestDocs() throws IOException {
        JsonNode docs = mapper.readTree(VespaDocSystemTest.class.getResourceAsStream("/test-documents.json"));
        report.publishEntry("Start feeding test documents");
        int i = 0;
        for (JsonNode node : docs) {
            assertEquals(200, feedTestDoc(node).statusCode(), "Status code for feeding document #" + i);
            i++;
        }
        report.publishEntry("Done feeding " + i + " documents");
        assertEquals(5, i, "Should have fed 5 documents");
    }

    /**
     * Feed Test documents using Vespa Update and create-if-nonexistent
     * (i.e. same as a Put, but retains values in other fields if the document already exists)
     */
    void updateTestDocs() throws IOException {
        JsonNode updates = mapper.readTree(VespaDocSystemTest.class.getResourceAsStream("/test-documents-updates.json"));
        report.publishEntry("Start updating test documents");
        int i = 0;
        for (JsonNode node : updates) {
            assertEquals(200, updateTestDoc(node).statusCode(), "Status code for updating document #" + i);
            i++;
        }
        report.publishEntry("Done updating " + i + " documents");
        assertEquals(5, i, "Should have updated 5 documents");
    }

    Set<DocumentId> getTestDocIDs() throws Exception {
        Set<DocumentId> ids = new HashSet<>();
        String continuation = "";
        report.publishEntry("Start dumping document IDs");
        do {
            String visitResult = visit(continuation.isEmpty() ? "" : "?continuation=" + continuation);
            continuation = mapper.readTree(visitResult).path("continuation").asText();
            for (JsonNode jsonNode : mapper.readTree(visitResult).path("documents")) {
                ids.add(DocumentId.of(jsonNode.path("id").asText()));
            }
        } while ( ! continuation.isEmpty());
        report.publishEntry("Done dumping document IDs");
        return ids;
    }

    void waitUntilBackendUp(String query) throws IOException {
        int retries = 0;
        while (searchNoAssert(query, "1s").statusCode() != 200 && retries < 30) {
            retries++;
            sleep();
        }

        retries = 0;
        while (getNumSearchResults(search(query, "1s")) < 5 && retries < 30) {
            sleep();
            retries++;
        }
    }

    private static void sleep() {
        try { Thread.sleep(1000); } catch (InterruptedException e) { throw new RuntimeException(e); }
    }

    void verifyQueryResults(Collection<DocumentId> expectedIds, String query, String timeout) throws IOException {
        String result = search(query, timeout);
        assertEquals(expectedIds.size(), getNumSearchResults(result));
        Set<DocumentId> resultIds = new HashSet<>();
        for (JsonNode jsonNode : mapper.readTree(result).get("root").get("children")) {
            resultIds.add(DocumentId.of(jsonNode.get("id").asText()));
        }
        assertEquals(expectedIds, resultIds, "expected and actual ids should match, result: " + result);
    }

    HttpResponse<String> feedTestDoc(JsonNode doc) {
        String path = "/document/v1/open/doc/docid/" +
                      encode("open" + doc.get("fields").get("path").textValue(), UTF_8);
        HttpResponse<String> res = endpoint.send(endpoint.request(path)
                                                         .POST(ofString(doc.toString())));
        assertEquals(200, res.statusCode(), "Status code for post");
        return res;
    }

    HttpResponse<String> updateTestDoc(JsonNode update) {
        String path = "/document/v1/open/doc/docid/" +
                      encode("open" + update.get("fields").get("path").get("assign").textValue(), UTF_8);
        HttpResponse<String> res = endpoint.send(endpoint.request(path, Map.of("create", "true"))
                                                         .PUT(ofString(update.toString())));
        assertEquals(200, res.statusCode(), "Status code for update");
        return res;
    }

    void removeTestDoc(DocumentId id) {
        HttpRequest.Builder request = endpoint.request("/document/v1/open/doc/docid/" +
                                                       encode(id.userSpecific(), UTF_8)) // id:open:doc::open/documentation/annotations.html
                                              .DELETE();
        HttpResponse<String> res = endpoint.send(request);
        assertEquals(200, res.statusCode(), "Status code for delete");
    }

    String getTestDoc(DocumentId id) {
        HttpResponse<String> res = endpoint.send(endpoint.request("/document/v1/open/doc/docid/" +
                                                                  encode(id.userSpecific(), UTF_8)) // id:open:doc::open/documentation/annotations.html
                                                         .GET());
        assertEquals(200, res.statusCode(), "Status code for get");
        return res.body();
    }

    String search(String query, String timeout) {
        HttpResponse<String> res = searchNoAssert(query, timeout);
        assertEquals(200, res.statusCode(), "Status code for search");
        return res.body();
    }

    HttpResponse<String> searchNoAssert(String query, String timeout) {
        return endpoint.send(endpoint.request("/search/",
                                              Map.of("yql", query,
                                                     "timeout", timeout)));
    }

    String visit(String continuation) {
        HttpRequest.Builder request = endpoint.request("/document/v1/open/doc/docid/" + continuation)
                                              .GET();
        HttpResponse<String> res = endpoint.send(request);
        assertEquals(200, res.statusCode(), "Status code for visiting documents");
        return res.body();
    }

    int getNumSearchResults(String searchResult) throws IOException {
        return mapper.readTree(searchResult).get("root").get("fields").get("totalCount").asInt();
    }

}
