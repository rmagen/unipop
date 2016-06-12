package org.unipop.process.vertex;

import com.google.common.collect.Iterators;
import com.google.common.collect.UnmodifiableIterator;
import org.unipop.query.StepDescriptor;
import org.unipop.process.predicate.ReceivesPredicatesHolder;
import org.apache.tinkerpop.gremlin.process.traversal.*;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.VertexStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.*;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;
import org.apache.tinkerpop.gremlin.process.traversal.util.*;
import org.apache.tinkerpop.gremlin.structure.*;
import org.apache.tinkerpop.gremlin.structure.util.StringFactory;
import org.apache.tinkerpop.gremlin.util.iterator.EmptyIterator;
import org.unipop.common.util.ConversionUtils;
import org.unipop.query.controller.ControllerManager;
import org.unipop.query.predicates.PredicatesHolder;
import org.unipop.query.predicates.PredicatesHolderFactory;
import org.unipop.query.search.SearchVertexQuery;
import org.unipop.structure.UniVertex;

import java.util.*;
import java.util.stream.Stream;

public class UniGraphVertexStep<E extends Element> extends AbstractStep<Vertex, E> implements ReceivesPredicatesHolder<Vertex, E> {
    private final boolean returnsVertex;
    private final Direction direction;
    private Class<E> returnClass;
    private String[] edgeLabels = new String[0];
    private int limit;
    private PredicatesHolder predicates = PredicatesHolderFactory.empty();
    private final StepDescriptor stepDescriptor;
    private List<SearchVertexQuery.SearchVertexController> controllers;
    private final int bulk;
    private Iterator<Traverser.Admin<E>> results = EmptyIterator.instance();

    public UniGraphVertexStep(VertexStep<E> vertexStep, ControllerManager controllerManager) {
        super(vertexStep.getTraversal());
        vertexStep.getLabels().forEach(this::addLabel);
        this.direction = vertexStep.getDirection();
        this.returnClass = vertexStep.getReturnClass();
        this.returnsVertex = vertexStep.returnsVertex();
        if(vertexStep.getEdgeLabels().length > 0) {
            this.edgeLabels = vertexStep.getEdgeLabels();
            HasContainer labelsPredicate = new HasContainer(T.label.getAccessor(), P.within(vertexStep.getEdgeLabels()));
            this.predicates = PredicatesHolderFactory.predicate(labelsPredicate);
        }
        else this.predicates = PredicatesHolderFactory.empty();
        this.controllers = controllerManager.getControllers(SearchVertexQuery.SearchVertexController.class);
        this.stepDescriptor = new StepDescriptor(this);
        this.bulk = getTraversal().getGraph().get().configuration().getInt("bulk", 100);
        limit = -1;
    }

    @Override
    protected Traverser.Admin<E> processNextStart() {
        if(!results.hasNext() && starts.hasNext())
            results = query();

        if(results.hasNext())
            return results.next();

        throw FastNoSuchElementException.instance();
    }

    private Iterator<Traverser.Admin<E>> query() {
        UnmodifiableIterator<List<Traverser.Admin<Vertex>>> partitionedTraversers = Iterators.partition(starts, bulk);
        return ConversionUtils.asStream(partitionedTraversers)
                .<Iterator<Traverser.Admin<E>>>map(this::queryBulk)
                .<Traverser.Admin<E>>flatMap(ConversionUtils::asStream).iterator();
    }

    private Iterator<Traverser.Admin<E>> queryBulk(List<Traverser.Admin<Vertex>> traversers) {
        Map<Object, List<Traverser<Vertex>>> idToTraverser = new HashMap<>(bulk);
        List<Vertex> vertices = new ArrayList<>(bulk);
        traversers.forEach(traverser -> {
            Vertex vertex = traverser.get();
            List<Traverser<Vertex>> traverserList = idToTraverser.get(vertex.id());
            if(traverserList == null) {
                traverserList = new ArrayList<>(1);
                idToTraverser.put(vertex.id(), traverserList);
            }
            traverserList.add(traverser);
            vertices.add(vertex);
        });
        SearchVertexQuery vertexQuery = new SearchVertexQuery(Edge.class, vertices, direction, predicates, limit, stepDescriptor);
        return controllers.stream().<Iterator<Edge>>map(controller -> controller.search(vertexQuery))
                .<Edge>flatMap(ConversionUtils::asStream)
                .<Traverser.Admin<E>>flatMap(edge -> toTraversers(edge, idToTraverser)).iterator();
    }

    private Stream<Traverser.Admin<E>> toTraversers(Edge edge, Map<Object, List<Traverser<Vertex>>> traversers) {
        return ConversionUtils.asStream(edge.vertices(direction))
            .<Traverser.Admin<E>>flatMap(originalVertex -> {
                List<Traverser<Vertex>> vertexTraversers = traversers.get(originalVertex.id());
                if(vertexTraversers == null) return null;
                return vertexTraversers.stream().map(vertexTraverser -> {
                    E result = getReturnElement(edge, originalVertex);
                    return vertexTraverser.asAdmin().split(result, this);
                });
            }).filter(result -> result != null);
    }

    private E getReturnElement(Edge edge, Vertex originalVertex) {
        if(!this.returnsVertex) return (E) edge;
        return (E) UniVertex.vertexToVertex(originalVertex, edge, this.direction);
    }



    @Override
    public void reset() {
        super.reset();
        this.results = EmptyIterator.instance();
    }

    @Override
    public String toString() {
        return StringFactory.stepString(this, this.direction, Arrays.asList(this.edgeLabels), this.returnClass.getSimpleName().toLowerCase());
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        return Collections.singleton(TraverserRequirement.OBJECT);
    }


    public Direction getDirection() {
        return direction;
    }

    @Override
    public void addPredicate(PredicatesHolder predicatesHolder) {
        this.predicates = PredicatesHolderFactory.and(this.predicates, predicatesHolder);
    }

    @Override
    public PredicatesHolder getPredicates() {
        return predicates;
    }

    @Override
    public void setLimit(int limit) {
        this.limit = limit;
    }
}
