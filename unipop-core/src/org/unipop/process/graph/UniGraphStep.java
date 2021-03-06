package org.unipop.process.graph;

import org.apache.tinkerpop.gremlin.process.traversal.Order;
import org.apache.tinkerpop.gremlin.process.traversal.step.Profiling;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.GraphStep;
import org.apache.tinkerpop.gremlin.process.traversal.step.util.HasContainer;
import org.apache.tinkerpop.gremlin.process.traversal.util.MutableMetrics;
import org.apache.tinkerpop.gremlin.structure.Element;
import org.javatuples.Pair;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.unipop.process.order.Orderable;
import org.unipop.util.ConversionUtils;
import org.unipop.process.predicate.ReceivesPredicatesHolder;
import org.unipop.process.properties.PropertyFetcher;
import org.unipop.query.StepDescriptor;
import org.unipop.query.controller.ControllerManager;
import org.unipop.query.predicates.PredicatesHolder;
import org.unipop.query.predicates.PredicatesHolderFactory;
import org.unipop.query.search.SearchQuery;
import org.unipop.structure.UniGraph;

import java.util.*;
import java.util.stream.Stream;

public class UniGraphStep<S,E extends Element> extends GraphStep<S,E> implements ReceivesPredicatesHolder<S, E>, PropertyFetcher, Orderable, Profiling{
    private static final Logger logger = LoggerFactory.getLogger(UniGraphStep.class);
    private StepDescriptor stepDescriptor;
    private List<SearchQuery.SearchController>  controllers;
    private PredicatesHolder predicates = PredicatesHolderFactory.empty();
    private Set<String> propertyKeys;
    private int limit;
    private List<Pair<String, Order>> orders;

    public UniGraphStep(GraphStep<S, E> originalStep, ControllerManager controllerManager) {
        super(originalStep.getTraversal(), originalStep.getReturnClass(), originalStep.isStartStep(), originalStep.getIds());
        originalStep.getLabels().forEach(this::addLabel);
        this.predicates = UniGraph.createIdPredicate(originalStep.getIds(), originalStep.getReturnClass());
        this.stepDescriptor = new StepDescriptor(this);
        this.controllers = controllerManager.getControllers(SearchQuery.SearchController.class);
        this.setIteratorSupplier(this::query);
        limit = -1;
        this.propertyKeys = new HashSet<>();
    }

    private Iterator<E> query() {
        Stream.concat(
                this.predicates.getPredicates().stream(),
                this.predicates.getChildren().stream()
                        .map(PredicatesHolder::getPredicates)
                        .flatMap(Collection::stream)
        ).map(HasContainer::getKey).forEach(this::addPropertyKey);

        SearchQuery<E> searchQuery = new SearchQuery<>(returnClass, predicates, limit, propertyKeys, orders, stepDescriptor, traversal);
        logger.debug("Executing query: ", searchQuery);
        return controllers.stream().<Iterator<E>>map(controller -> controller.search(searchQuery)).flatMap(ConversionUtils::asStream).distinct().iterator();
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


    @Override
    public void addPropertyKey(String key) {
        if (propertyKeys != null)
            propertyKeys.add(key);
    }

    @Override
    public void fetchAllKeys() {
        this.propertyKeys = null;
    }

    @Override
    public Set<String> getKeys() {
        return propertyKeys;
    }

    @Override
    public void setMetrics(MutableMetrics metrics) {
        this.stepDescriptor = new StepDescriptor(this, metrics);
    }

    @Override
    public void setOrders(List<Pair<String, Order>> orders) {
        this.orders = orders;
    }
}
