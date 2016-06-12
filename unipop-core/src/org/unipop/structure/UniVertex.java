package org.unipop.structure;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.util.ElementHelper;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.unipop.common.util.ConversionUtils;
import org.unipop.query.mutation.AddEdgeQuery;
import org.unipop.query.mutation.PropertyQuery;
import org.unipop.query.predicates.PredicatesHolder;
import org.unipop.query.predicates.PredicatesHolderFactory;
import org.unipop.query.search.SearchVertexQuery;

import java.util.Arrays;
import java.util.Iterator;
import java.util.Map;

public class UniVertex extends UniElement implements Vertex {

    public UniVertex(Map<String, Object> keyValues, UniGraph graph) {
        super(keyValues, graph);
    }

    @Override
    protected String getDefaultLabel() {
        return Vertex.DEFAULT_LABEL;
    }

    @Override
    protected Property createProperty(String key, Object value) {
        return new UniVertexProperty<>(this, key, value);
    }

    @Override
    public <V> VertexProperty<V> property(VertexProperty.Cardinality cardinality, String key, V value, final Object... keyValues) {
        ElementHelper.legalPropertyKeyValueArray(keyValues);
        if (keyValues != null && keyValues.length > 0) throw VertexProperty.Exceptions.metaPropertiesNotSupported();
        return this.property(key, value);
    }

    @Override
    public Iterator<Edge> edges(Direction direction, String... edgeLabels) {
        PredicatesHolder predicatesHolder = (edgeLabels.length == 0) ? PredicatesHolderFactory.empty() :
                PredicatesHolderFactory.predicate(new HasContainer(T.label.getAccessor(), P.within(edgeLabels)));

        SearchVertexQuery searchVertexQuery = new SearchVertexQuery(Edge.class, Arrays.asList(this), direction, predicatesHolder, -1, null);
        return graph.getControllerManager().getControllers(SearchVertexQuery.SearchVertexController.class).stream()
                .<Iterator<Edge>>map(controller -> controller.search(searchVertexQuery))
                .flatMap(ConversionUtils::asStream)
                .iterator();
    }

    @Override
    public Iterator<Vertex> vertices(Direction direction, String... edgeLabels) {
        Iterator<Edge> edges = this.edges(direction, edgeLabels);
        return ConversionUtils.asStream(edges).map(edge -> vertexToVertex(this, edge, direction)).iterator();
    }

    public static Vertex vertexToVertex(Vertex source, Edge edge, Direction direction) {
        switch (direction) {
            case OUT:
                return edge.inVertex();
            case IN:
                return edge.outVertex();
            case BOTH:
                Vertex outV = edge.outVertex();
                Vertex inV = edge.inVertex();
                if(outV.id().equals(inV.id()))
                    return outV; //points to self
                if(source.id().equals(inV.id()))
                    return outV;
                if(source.id().equals(outV.id()))
                    return inV;
            default:
                throw new IllegalArgumentException(direction.toString());
        }
    }

    @Override
    public <V> VertexProperty<V> property(String key, V value) {

        UniVertexProperty vertexProperty = (UniVertexProperty) addPropertyLocal(key, value);
        PropertyQuery<UniVertex> propertyQuery = new PropertyQuery<UniVertex>(this, vertexProperty, PropertyQuery.Action.Add, null);
        graph.getControllerManager().getControllers(PropertyQuery.PropertyController.class).forEach(controller ->
                controller.property(propertyQuery));
        return vertexProperty;
    }

    @Override
    public <V> VertexProperty<V> property(final String key) {
        if (this.properties.containsKey(key)) {
            return (VertexProperty<V>) this.properties.get(key);
        } else return VertexProperty.<V>empty();
    }

    @Override
    public Edge addEdge(final String label, final Vertex vertex, final Object... keyValues) {
        if (null == vertex) throw Graph.Exceptions.argumentCanNotBeNull("vertex");
        ElementHelper.legalPropertyKeyValueArray(keyValues);
        ElementHelper.validateLabel(label);
        Map<String, Object> stringObjectMap = ConversionUtils.asMap(keyValues);
        stringObjectMap.put(T.label.toString(), label);
        AddEdgeQuery addEdgeQuery = new AddEdgeQuery(this, vertex, stringObjectMap, null);
        return graph.getControllerManager().getControllers(AddEdgeQuery.AddEdgeController.class).stream()
                .map(controller -> controller.addEdge(addEdgeQuery))
                .findFirst().get();
    }

    @Override
    public void remove() {
        edges(Direction.BOTH).forEachRemaining(Element::remove);
        super.remove();
    }

    @Override
    public String toString() {
        return StringFactory.vertexString(this);
    }

    @Override
    public <V> Iterator<VertexProperty<V>> properties(final String... propertyKeys) {
        return propertyIterator(propertyKeys);
    }

}