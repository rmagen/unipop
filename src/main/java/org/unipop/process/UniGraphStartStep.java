package org.unipop.process;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.step.sideEffect.GraphStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.process.traversal.util.MutableMetrics;
import org.apache.tinkerpop.gremlin.process.traversal.util.StandardTraversalMetrics;
import org.apache.tinkerpop.gremlin.process.traversal.util.TraversalMetrics;
import org.apache.tinkerpop.gremlin.structure.*;
import org.unipop.controller.*;
import org.unipop.controllerprovider.ControllerProvider;

import java.util.ArrayList;
import java.util.Iterator;
import java.util.Optional;
import java.util.function.Supplier;

public class UniGraphStartStep<E extends Element> extends GraphStep<E> {

    private final Predicates predicates;
    private final ControllerProvider controllerProvider;
    private MutableMetrics metrics;

    public UniGraphStartStep(GraphStep originalStep, Predicates predicates, ControllerProvider controllerProvider) {
        super(originalStep.getTraversal(), originalStep.getReturnClass(), originalStep.getIds());
        originalStep.getLabels().forEach(label -> this.addLabel(label.toString()));
        predicates.labels.forEach(label -> this.addLabel(label.toString()));
        this.predicates = predicates;
        this.controllerProvider = controllerProvider;
        Optional<StandardTraversalMetrics> metrics = this.getTraversal().asAdmin().getSideEffects().<StandardTraversalMetrics>get(TraversalMetrics.METRICS_KEY);
        if(metrics.isPresent()) this.metrics = (MutableMetrics) metrics.get().getMetrics(this.getId());
        this.setIteratorSupplier(() -> (Iterator<E>) (Vertex.class.isAssignableFrom(this.returnClass) ? this.vertices() : this.edges()));
    }

    private Iterator<? extends Vertex> vertices() {
        return controllerProvider.getVertexHandler(predicates).vertices(predicates, metrics);
    }

    private Iterator<? extends Edge> edges() {
         return controllerProvider.getEdgeHandler(predicates).edges(predicates, metrics);
    }
}