package org.unipop.elastic.suite;

import org.apache.tinkerpop.gremlin.GraphProviderClass;
import org.junit.runner.RunWith;
import org.unipop.common.test.UnipopStructureSuite;
import org.unipop.elastic.ElasticGraphProvider;
import org.unipop.structure.UniGraph;

@RunWith(UnipopStructureSuite.class)
@GraphProviderClass(provider = ElasticGraphProvider.class, graph = UniGraph.class)
public class ElasticStructureSuite {
}
