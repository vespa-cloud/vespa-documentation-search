package ai.vespa.cloud.docsearch;

import ai.vespa.hosted.cd.Endpoint;
import ai.vespa.hosted.cd.ProductionTest;
import ai.vespa.hosted.cd.TestRuntime;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.TestReporter;

import java.io.IOException;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Verifies the deployment that was just rolled out to a production zone.
 * A failure here stops the rollout, so the remaining zones keep the previous version.
 *
 * <p>Metrics are read from the deployment under test through the mTLS endpoint,
 * see <a href="https://docs.vespa.ai/en/reference/api/metrics-v2.html">/metrics/v2 reference</a>.
 * Values cover the last minute of metrics to check the new version serving traffic.
 */
@ProductionTest
public class VespaDocProductionTest {

    /** Fail if any node is busier than this. */
    private static final double MAX_CPU_UTIL_PERCENT = 85.0;

    private final ObjectMapper mapper = new ObjectMapper();
    private final Endpoint endpoint = TestRuntime.get().deploymentToTest().endpoint("default");

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

    @Test
    void noServerErrors() throws IOException {
        JsonNode metrics = metrics();
        assertEquals(0.0, sumAllNodes(metrics, "http.status.5xx.count"), 0.0,
                     "No requests should fail with a server error");
        assertEquals(0.0, sumAllNodes(metrics, "failed_queries.count"), 0.0,
                     "No queries should fail");
    }

    @Test
    void cpuUtilizationIsBelowThreshold(TestReporter report) throws IOException {
        JsonNode metrics = metrics();
        Optional<Sample> worst = worstNode(metrics, List.of("content.proton.resource_usage.cpu_util.other.max"), Unit.PERCENT);

        report.publishEntry("max content.proton.resource_usage.cpu_util.other", worst.get().toString());
        assertTrue(worst.get().value() < MAX_CPU_UTIL_PERCENT,
                   "CPU utilization should be below " + MAX_CPU_UTIL_PERCENT + "%, but " + worst.get());
    }

    private JsonNode metrics() throws IOException {
        HttpResponse<String> res = endpoint.send(endpoint.request("/metrics/v2/values",
                                                                 Map.of("consumer", "Vespa")));
        assertEquals(200, res.statusCode(), "Status code for metrics");
        return mapper.readTree(res.body());
    }

    private enum Unit {
        /** Value is already a percentage, 0-100. */
        PERCENT,
        /** Value is used as is. */
        RAW
    }

    private record Sample(String host, String role, double value, Unit unit) {
        @Override
        public String toString() {
            return value + (unit == Unit.RAW ? "" : "%") + " on " + role + " (" + host + ")";
        }
    }

    /**
     * The node with the highest sum of the given metrics. Each metric contributes its highest
     * value on that node, so several samples of the same metric - one per dimension combination -
     * are not added together. Empty if none of the metrics are reported by any node.
     */
    private Optional<Sample> worstNode(JsonNode metrics, List<String> names, Unit unit) {
        Sample worst = null;
        for (JsonNode node : metrics.path("nodes")) {
            double total = 0;
            boolean found = false;
            for (String name : names) {
                double highest = 0;
                boolean present = false;
                for (JsonNode values : valuesIn(node)) {
                    JsonNode value = values.path(name);
                    if (value.isNumber()) {
                        highest = Math.max(highest, value.asDouble());
                        present = true;
                    }
                }
                if (present) {
                    total += highest;
                    found = true;
                }
            }

            if (!found) 
                continue;

            Sample sample = new Sample(node.path("hostname").asText(),
                                       node.path("role").asText(),
                                       total,
                                       unit);
            if (worst == null || sample.value() > worst.value()) worst = sample;
        }
        return Optional.ofNullable(worst);
    }

    private double sumAllNodes(JsonNode metrics, String name) {
        double sum = 0;
        for (JsonNode node : metrics.path("nodes"))
            for (JsonNode values : valuesIn(node)) {
                JsonNode value = values.path(name);
                if (value.isNumber()) sum += value.asDouble();
            }
        return sum;
    }

    /** All metric value objects of a node: those of the node itself, and those of each of its services. */
    private List<JsonNode> valuesIn(JsonNode node) {
        List<JsonNode> values = new ArrayList<>();
        for (JsonNode metric : node.path("node").path("metrics")) values.add(metric.path("values"));
        for (JsonNode service : node.path("services"))
            for (JsonNode metric : service.path("metrics")) values.add(metric.path("values"));
        return values;
    }
}
