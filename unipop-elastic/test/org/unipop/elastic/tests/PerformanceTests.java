package org.unipop.elastic.tests;

import org.apache.commons.configuration.Configuration;
import org.apache.tinkerpop.gremlin.LoadGraphWith;
import org.apache.tinkerpop.gremlin.structure.Edge;
import org.apache.tinkerpop.gremlin.structure.Graph;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.junit.Before;
import org.junit.Test;
import org.unipop.common.test.UnipopGraphProvider;
import org.unipop.elastic.ElasticGraphProvider;
import org.unipop.elastic.common.TimingAccessor;

import java.util.Iterator;

public class PerformanceTests {

    TimingAccessor sw = new TimingAccessor();
    private Graph graph;

    @Before
    public void startUp() throws Exception {
        UnipopGraphProvider unipopGraphProvider = new ElasticGraphProvider();
        final Configuration configuration = unipopGraphProvider.newGraphConfiguration("testGraph", this.getClass(), "performanceTests", LoadGraphWith.GraphData.MODERN);
        this.graph = unipopGraphProvider.openTestGraph(configuration);
    }

    @Test
    public void profile() {
        startWatch("add vertices");
        int count = 10000;
        for(int i = 0; i < count; i++)
            graph.addVertex();
        stopWatch("add vertices");

        startWatch("vertex iterator");
        Iterator<Vertex> vertexIterator = graph.vertices();
        stopWatch("vertex iterator");

        startWatch("add edges");
        vertexIterator.forEachRemaining(v -> v.addEdge("bla", v));
        stopWatch("add edges");

        startWatch("edge iterator");
        Iterator<Edge> edgeIterator = graph.edges();
        stopWatch("edge iterator");

        sw.print();
        System.out.println("-----");
    }


    private void stopWatch(String s) {
        sw.timer(s).stop();
    }

    private void startWatch(String s) {
        sw.timer(s).start();
    }
}
