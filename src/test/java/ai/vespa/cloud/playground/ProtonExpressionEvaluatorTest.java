// Copyright Vespa.ai. Licensed under the terms of the Apache 2.0 license. See LICENSE in the project root.

package ai.vespa.cloud.playground;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.yahoo.searchlib.rankingexpression.evaluation.DoubleValue;
import com.yahoo.searchlib.rankingexpression.evaluation.MapContext;
import com.yahoo.searchlib.rankingexpression.evaluation.TensorValue;
import com.yahoo.searchlib.rankingexpression.evaluation.Value;
import com.yahoo.searchlib.rankingexpression.parser.ParseException;
import com.yahoo.tensor.Tensor;
import org.junit.jupiter.api.Test;
import org.junit.Ignore;

import java.io.IOException;

import static org.junit.jupiter.api.Assertions.assertNotNull;

public class ProtonExpressionEvaluatorTest {

 // @Test
    public void requireThatTensorExpressionCanBeEvaluated() throws Exception {
        String json =
                """
                   [ { "name": "t1",
                       "cell": 1,
                       "verbose": false,
                       "expr": "tensor(x{}):{{x:0}:1,{x:1}:2}" },
                     { "name": "t2",
                       "cell": 2,
                       "verbose": false,
                       "expr": "tensor(x{}):{{x:0}:2,{x:1}:3}" },
                     { "name": "out",
                       "cell": 3,
                       "verbose": false,
                       "expr": "t1 * t2" } ]
                """;
        String v = ProtonExpressionEvaluator.evaluate(json);

        ObjectMapper m = new ObjectMapper();
        JsonNode root = m.readTree(v);
        assertNotNull(root);
        System.err.println("Result of simple multiplication: " + root.toPrettyString());
    }

 // @Test
    public void requireThatUnpackBitsCanBeEvaluated() throws Exception {
        String json =
                """
                   [
                       { "name": "attribute(colbert)",
                         "cell": 3,
                         "verbose": false,
                         "expr": "tensor<int8>(dt{}, x[2]):{ 0:[-83,107], foo:[119,170] }"
                       },
                       { "name": "unpack",
                         "cell": 42,
                         "verbose": true,
                         "expr": "unpack_bits(attribute(colbert))"
                       }
                   ]
                """;
        String v = ProtonExpressionEvaluator.evaluate(json);

        ObjectMapper m = new ObjectMapper();
        JsonNode root = m.readTree(v);
        assertNotNull(root);
        System.err.println("Result with unpack_bits: " + root.toPrettyString());
    }


}
