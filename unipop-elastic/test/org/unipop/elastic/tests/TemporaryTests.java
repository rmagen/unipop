package org.unipop.elastic.tests;

import com.google.common.collect.Iterators;
import com.google.common.collect.Lists;
import org.apache.tinkerpop.gremlin.AbstractGremlinTest;
import org.apache.tinkerpop.gremlin.GraphManager;
import org.apache.tinkerpop.gremlin.LoadGraphWith;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.MapHelper;
import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.util.Attachable;
import org.apache.tinkerpop.gremlin.structure.util.detached.DetachedEdge;
import org.apache.tinkerpop.gremlin.structure.util.detached.DetachedFactory;
import org.apache.tinkerpop.gremlin.util.iterator.IteratorUtils;
import org.junit.Test;
import org.unipop.elastic.ElasticGraphProvider;

import java.util.*;
import java.util.stream.Collectors;

import static org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.MODERN;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.GraphTraversalSource.computer;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.valueMap;
import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.CoreMatchers.not;
import static org.junit.Assert.*;

public class TemporaryTests extends AbstractGremlinTest {

    public TemporaryTests() throws Exception {
        GraphManager.setGraphProvider(new ElasticGraphProvider());
    }

    @Test
    @LoadGraphWith(MODERN)
    public void test() {
        GraphTraversalSource g = graph.traversal();
        Traversal t = g.V("1").out().hasId("2");

        check(t);
    }

    @Test
    public void concurrencyTest() {
        for (int i = 0; i < 25; i++) {
            graph.addVertex("myId", i);
        }
        System.out.println(Lists.newArrayList(graph.vertices()));

        graph.vertices().forEachRemaining(v -> graph.vertices().forEachRemaining(u -> v.addEdge("knows", u, "myEdgeId", 12)));
        graph.vertices().forEachRemaining(v -> graph.edges().forEachRemaining(e -> {
            if (e.inVertex().equals(v)) {
                System.out.println("Y");
            } else {
                System.out.println("N");
            }
        }));

        System.out.println(Lists.newArrayList(graph.edges()));

        tryCommit(graph, assertVertexEdgeCounts(25, 625));

        final List<Vertex> vertices = new ArrayList<>();
        IteratorUtils.fill(graph.vertices(), vertices);
        for (Vertex v : vertices) {
            v.remove();
            tryCommit(graph);
        }

        tryCommit(graph, assertVertexEdgeCounts(0, 0));
    }

    @Test
    public void nullProperty() {
        graph.addVertex("abc", null);
    }

    private void check(Traversal traversal) {
        System.out.println("pre-strategy:" + traversal);
        traversal.hasNext();
        System.out.println("post-strategy:" + traversal);

        //traversal.profile().cap(TraversalMetrics.METRICS_KEY);
        int count = 0;
        while (traversal.hasNext()) {
            System.out.println(traversal.next());
            count++;
        }
        System.out.println(count);
    }
}
