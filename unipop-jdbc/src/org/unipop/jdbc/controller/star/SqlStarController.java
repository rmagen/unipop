package org.unipop.jdbc.controller.star;

import org.apache.commons.collections.iterators.EmptyIterator;
import org.apache.commons.lang.NotImplementedException;
import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.structure.Direction;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.apache.tinkerpop.gremlin.structure.T;
import org.apache.tinkerpop.gremlin.structure.Vertex;
import org.jooq.Record;
import org.jooq.RecordMapper;
import org.jooq.SelectJoinStep;
import org.unipop.controller.InnerEdgeController;
import org.unipop.jdbc.controller.vertex.SqlVertexController;
import org.unipop.jdbc.utils.JooqHelper;
import org.unipop.query.UniQuery;
import org.unipop.structure.UniEdge;
import org.unipop.structure.UniGraph;
import org.unipop.structure.UniVertex;

import java.sql.Connection;
import java.util.*;
import java.util.stream.Collectors;
import java.util.stream.Stream;
import java.util.stream.StreamSupport;

import static org.jooq.impl.DSL.field;


/**
 * Created by sbarzilay on 2/17/16.
 */
public class SqlStarController extends SqlVertexController implements EdgeQueryController {

    Set<InnerEdgeController> innerEdgeControllers;
    Set<String> propertiesNames;

    public SqlStarController(String tableName, UniGraph graph, Connection conn, Set<String> propertiesNames, InnerEdgeController... innerEdgeControllers) {
        super(tableName, graph, conn);
        this.innerEdgeControllers = new HashSet<>();
        Collections.addAll(this.innerEdgeControllers, innerEdgeControllers);
        vertexMapper = new StarVertexMapper();
        this.propertiesNames = propertiesNames;

    }

    public SqlStarController() {
    }

    @Override
    public void init(Map<String, Object> conf, UniGraph graph) throws Exception {
        super.init(conf, graph);
        vertexMapper = new StarVertexMapper();
        for (Map<String, Object> edge : ((List<Map<String, Object>>) conf.get("edges"))) {
            InnerEdgeController innerEdge = ((InnerEdgeController) Class.forName(edge.get("class").toString()).newInstance());
            edge.put("context", dslContext);
            innerEdge.init(edge);
            innerEdgeControllers.add(innerEdge);
        }
    }

    public String getTableName() {
        return tableName;
    }

    protected UniStarVertex createVertex(Object id, String label, Map<String, Object> properties) {
        UniStarVertex starVertex = new UniStarVertex(id, label, null,graph.getControllerManager(), graph, innerEdgeControllers);
        starVertex.addTransientProperty(new TransientProperty(starVertex, "resource", getResource()));
        properties.forEach(starVertex::addPropertyLocal);
        return starVertex;
    }

    @Override
    public UniVertex vertex(Direction direction, Object vertexId, String vertexLabel) {
        UniDelayedStarVertex uniVertex = new UniDelayedStarVertex(vertexId, vertexLabel, graph.getControllerManager(), graph, innerEdgeControllers);
        uniVertex.addTransientProperty(new TransientProperty(uniVertex, "resource", getResource()));
        return uniVertex;
    }

    @Override
    public Iterator<UniVertex> vertices(UniQuery uniQuery) {
        SelectJoinStep<Record> select = createSelect(uniQuery);
        try {
            Map<Object, List<UniVertex>> group = select.fetch(vertexMapper).stream().collect(Collectors.groupingBy(baseVertex -> baseVertex.id()));
            List<UniVertex> groupedVertices = new ArrayList<>();
            group.values().forEach(baseVertices -> {
                UniStarVertex star = (UniStarVertex) baseVertices.get(0);
                baseVertices.forEach(baseVertex -> ((UniStarVertex) baseVertex).getInnerEdges(new UniQuery()).forEach(baseEdge -> star.addInnerEdge(((UniInnerEdge) baseEdge))));
                groupedVertices.add(star);
            });
            return groupedVertices.iterator();
        }
        catch (Exception e){
            return EmptyIterator.INSTANCE;
        }
    }

    private HasContainer getHasId(UniQuery uniQuery){
        for (HasContainer hasContainer : uniQuery.hasContainers) {
            if (hasContainer.getKey().equals(T.id.toString()))
                return hasContainer;
        }
        return null;
    }

    @Override
    public Iterator<UniEdge> edges(UniQuery uniQuery) {
        SelectJoinStep<Record> select = dslContext.select().from(tableName);
        HasContainer id = getHasId(uniQuery);
        if (id != null){
            select.where(field("EDGEID").in(((Iterable) id.getValue())));
            uniQuery.hasContainers.remove(id);
        }
        uniQuery.hasContainers.forEach(has -> select.where(JooqHelper.createCondition(has)));
        Map<Object, List<UniVertex>> group = select.fetch(vertexMapper).stream().collect(Collectors.groupingBy(baseVertex -> baseVertex.id()));
        List<UniVertex> groupedVertices = new ArrayList<>();
        group.values().forEach(baseVertices -> {
            UniStarVertex star = (UniStarVertex) baseVertices.get(0);
            baseVertices.forEach(baseVertex -> ((UniStarVertex) baseVertex).getInnerEdges(new UniQuery()).forEach(baseEdge -> star.addInnerEdge(((UniInnerEdge) baseEdge))));
            groupedVertices.add(star);
        });
        if (id != null)
            uniQuery.hasContainers.add(id);
        return groupedVertices.stream().flatMap(vertex -> ((UniStarVertex) vertex).getInnerEdges(uniQuery).stream()).collect(Collectors.toSet()).iterator();
    }

    @Override
    public Iterator<UniEdge> edges(Vertex[] vertices, Direction direction, String[] edgeLabels, UniQuery uniQuery) {

        Set<UniEdge> innerEdges = StreamSupport.stream(Arrays.asList(vertices).stream().filter(vertex1 -> vertex1 instanceof UniStarVertex)
                .map(vertex2 -> ((UniStarVertex) vertex2).getInnerEdges(direction, Arrays.asList(edgeLabels), uniQuery)).spliterator(), false).flatMap(Collection::stream).collect(Collectors.toSet());

        SelectJoinStep<Record> select = dslContext.select().from(tableName);
        Set<Object> ids = Stream.of(vertices).map(Element::id).collect(Collectors.toSet());
        ArrayList<HasContainer> hasContainers = new ArrayList<>();
        uniQuery.hasContainers.forEach(hasContainers::add);
        Set<String> controllersLabels = innerEdgeControllers.stream().map(InnerEdgeController::getEdgeLabel).collect(Collectors.toSet());
        for (String edgeLabel : edgeLabels) {
            if (controllersLabels.contains(edgeLabel))
                hasContainers.add(new HasContainer(edgeLabel, P.within(ids)));
        }
        if (edgeLabels.length == 0){
            for (String edgeLabel : controllersLabels)
                hasContainers.add(new HasContainer(edgeLabel, P.within(ids)));
        }
        HasContainer id = getHasId(uniQuery);
        if (id != null){
            if (id.getValue() instanceof Iterable)
                select.where(field("EDGEID").in(((Iterable) id.getValue())));
            else if (id.getValue() instanceof String)
                select.where(field("EDGEID").in(id.getValue()));
            hasContainers.remove(id);
        }
        hasContainers.forEach(has -> select.where(JooqHelper.createCondition(has)));

        UniQuery p = new UniQuery();
        p.hasContainers = hasContainers;
        Map<Object, List<UniVertex>> group = select.fetch(vertexMapper).stream().collect(Collectors.groupingBy(baseVertex -> baseVertex.id()));
        List<UniVertex> groupedVertices = new ArrayList<>();
        group.values().forEach(baseVertices -> {
            UniStarVertex star = (UniStarVertex) baseVertices.get(0);
            baseVertices.forEach(baseVertex -> ((UniStarVertex) baseVertex).getInnerEdges(new UniQuery()).forEach(baseEdge -> star.addInnerEdge(((UniInnerEdge) baseEdge))));
            groupedVertices.add(star);
        });

        Set<UniEdge> outerEdges = groupedVertices.stream()
                .flatMap(vertex -> ((UniStarVertex) vertex).getInnerEdges(direction.opposite(), Arrays.asList(edgeLabels), uniQuery).stream()).collect(Collectors.toSet());
        return Stream.of(innerEdges, outerEdges).flatMap(baseEdges -> baseEdges.stream()).collect(Collectors.toSet()).iterator();

    }

    @Override
    protected void addProperty(List<UniVertex> vertices, String key, Object value) {
        super.addProperty(vertices, key, value);
        if (value instanceof List) {
            InnerEdgeController innerEdgeController1 = innerEdgeControllers.stream().filter(innerEdgeController -> innerEdgeController.getEdgeLabel().equals(key)).findFirst().get();
            List<Map<String, Object>> edges = (List<Map<String, Object>>) value;
            vertices.forEach(vertex -> edges.forEach(edge -> innerEdgeController1.parseEdge(((UniStarVertex) vertex), edge)));
        }
    }

    @Override
    public void update(UniVertex vertex, boolean force) {
        throw new NotImplementedException();
    }

    @Override
    public long edgeCount(UniQuery uniQuery) {
        throw new NotImplementedException();
    }

    @Override
    public long edgeCount(Vertex[] vertices, Direction direction, String[] edgeLabels, UniQuery uniQuery) {
        throw new NotImplementedException();
    }

    @Override
    public Map<String, Object> edgeGroupBy(UniQuery uniQuery, Traversal keyTraversal, Traversal valuesTraversal, Traversal reducerTraversal) {
        throw new NotImplementedException();
    }

    @Override
    public Map<String, Object> edgeGroupBy(Vertex[] vertices, Direction direction, String[] edgeLabels, UniQuery uniQuery, Traversal keyTraversal, Traversal valuesTraversal, Traversal reducerTraversal) {
        throw new NotImplementedException();
    }

    private InnerEdgeController getControllerByLabel(String label) {
        Optional<InnerEdgeController> edgeControllerOptional = innerEdgeControllers.stream().filter(innerEdgeController -> innerEdgeController.getEdgeLabel().equals(label)).findFirst();
        if (edgeControllerOptional.isPresent())
            return edgeControllerOptional.get();
        throw new RuntimeException("no edge mapping for label: " + label);
    }

    @Override
    public UniEdge addEdge(Object edgeId, String label, UniVertex outV, UniVertex inV, Map<String, Object> properties) {
        return getControllerByLabel(label).addEdge(edgeId, label, outV, inV, properties);
    }

    private class StarVertexMapper implements RecordMapper<Record, UniVertex> {

        @Override
        public UniVertex map(Record record) {
            //Change keys to lower-case. TODO: make configurable mapping
            Map<String, Object> stringObjectMap = new HashMap<>();
            record.intoMap().forEach((key, value) -> stringObjectMap.put(key.toLowerCase(), value));
            UniStarVertex star = createVertex(stringObjectMap.get("id"), tableName.toLowerCase(), stringObjectMap);
            innerEdgeControllers.forEach(innerEdgeController -> {
                    if (stringObjectMap.get(innerEdgeController.getEdgeLabel()) != null)
                        innerEdgeController.parseEdge(star, stringObjectMap);
                });
//            SelectJoinStep<Record> select = dslContext.select().from(tableName);
//            select.where(JooqHelper.createCondition(new HasContainer(T.id.toString(), P.eq(stringObjectMap.getValue("id")))));
//            select.fetch().forEach(vertex -> {
//                Map<String, Object> vertexStringObjectMap = new HashMap<>();
//                record.intoMap().forEach((key, value) -> vertexStringObjectMap.put(key.toLowerCase(), value));
//                innerEdgeControllers.forEach(innerEdgeController -> {
//                    if (vertexStringObjectMap.getValue(innerEdgeController.getEdgeLabel()) != null)
//                        innerEdgeController.parseEdge(star, vertexStringObjectMap);
//                });
//            });
            return star;
        }
    }
}
