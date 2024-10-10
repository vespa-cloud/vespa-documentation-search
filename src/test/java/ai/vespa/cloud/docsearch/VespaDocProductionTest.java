package ai.vespa.cloud.docsearch;

import ai.vespa.hosted.cd.ProductionTest;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

@ProductionTest
public class VespaDocProductionTest {

    @Test
    void verifyMetrics() throws IOException, InterruptedException {
        // Can use publicly available resources only, so use open query interface
        // Here, ensure > 50 documents about ranking
        HttpRequest req = HttpRequest.newBuilder()
                                     .GET()
                                     .uri(URI.create("https://api.search.vespa.ai/search/?query=ranking&ranking=documentation&locale=en-US&hits=1"))
                                     .build();
        HttpResponse<String> res = HttpClient.newBuilder().build()
                                             .send(req, HttpResponse.BodyHandlers.ofString());

        assertEquals(200, res.statusCode());

        String body = res.body();
        long hitCount = new ObjectMapper().readTree(body)
                                          .get("root").get("fields").get("totalCount").asLong();
        assertTrue(hitCount > 50, "Number of hits should be more than 50");
    }

}
