package org.unipop.process.start;

import org.apache.tinkerpop.gremlin.process.traversal.P;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.unipop.process.predicate.ReceivesPredicatesHolder;
import org.unipop.query.StepDescriptor;
import org.unipop.query.controller.ControllerManager;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.GraphStep;
import org.apache.tinkerpop.gremlin.structure.*;
import org.unipop.common.util.ConversionUtils;
import org.unipop.query.predicates.PredicatesHolder;
import org.unipop.query.predicates.PredicatesHolderFactory;
import org.unipop.query.search.SearchQuery;
import org.unipop.structure.UniGraph;

import java.util.*;

public class UniGraphStartStep<S,E extends Element> extends GraphStep<S,E> implements ReceivesPredicatesHolder<S, E> {

    private final StepDescriptor stepDescriptor;
    List<SearchQuery.SearchController>  controllers;
    private PredicatesHolder predicates = PredicatesHolderFactory.empty();
    private int limit;

    public UniGraphStartStep(GraphStep<S, E> originalStep, ControllerManager controllerManager) {
        super(originalStep.getTraversal(), originalStep.getReturnClass(), originalStep.isStartStep(), originalStep.getIds());
        originalStep.getLabels().forEach(this::addLabel);
        this.predicates = UniGraph.createIdPredicate(originalStep.getIds(), originalStep.getReturnClass());
        this.stepDescriptor = new StepDescriptor(this);
        this.controllers = controllerManager.getControllers(SearchQuery.SearchController.class);
        this.setIteratorSupplier(this::query);
        limit = -1;
    }

    private Iterator<E> query() {
        SearchQuery<E> searchQuery = new SearchQuery<>(returnClass, predicates, limit, stepDescriptor);
        return controllers.stream().<Iterator<E>>map(controller -> controller.search(searchQuery)).flatMap(ConversionUtils::asStream).iterator();
    }

    @Override
    public void addPredicate(PredicatesHolder predicatesHolder) {
        predicatesHolder.getPredicates().forEach(has -> GraphStep.processHasContainerIds(this, has));
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
