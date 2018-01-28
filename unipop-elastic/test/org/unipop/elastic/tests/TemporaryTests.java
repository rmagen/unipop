package org.unipop.elastic.tests;

import org.apache.tinkerpop.gremlin.AbstractGremlinTest;
import org.apache.tinkerpop.gremlin.GraphManager;
import org.apache.tinkerpop.gremlin.LoadGraphWith;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Test;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import test.ElasticGraphProvider;

import static org.apache.tinkerpop.gremlin.LoadGraphWith.GraphData.MODERN;
import static org.apache.tinkerpop.gremlin.process.traversal.dsl.graph.__.select;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertFalse;

public class TemporaryTests extends AbstractGremlinTest {

    private static final Logger logger = LoggerFactory.getLogger(AbstractGremlinTest.class);

    public TemporaryTests() throws Exception {
        GraphManager.setGraphProvider(new ElasticGraphProvider());
    }


    @Test
    @LoadGraphWith(MODERN)
    public void test() {
        final Traversal<Vertex, String> traversal = g.withSideEffect("a", "marko").addV().property("name", select("a")).values("name");
        printTraversalForm(traversal);
        assertEquals("marko", traversal.next());
        assertFalse(traversal.hasNext());
    }


    @Test
    public void nullProperty() {
        graph.addVertex("abc", null);
    }

    private void check(Traversal traversal) {
//        traversal.profile();
        System.out.println("pre-strategy:" + traversal);
        traversal.hasNext();
        System.out.println("post-strategy:" + traversal);

        int count = 0;
        while (traversal.hasNext()) {
            System.out.println(traversal.next());
            count++;
        }
        System.out.println(count);
    }
}
