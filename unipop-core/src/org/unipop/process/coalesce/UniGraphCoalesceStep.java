package org.unipop.process.coalesce;

import com.google.common.collect.Lists;
import org.apache.tinkerpop.gremlin.process.traversal.Traversal;
import org.apache.tinkerpop.gremlin.process.traversal.Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.step.TraversalParent;
import org.apache.tinkerpop.gremlin.process.traversal.step.map.FlatMapStep;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.B_O_S_SE_SL_Traverser;
import org.apache.tinkerpop.gremlin.process.traversal.traverser.TraverserRequirement;
import org.apache.tinkerpop.gremlin.process.traversal.util.DefaultTraversalSideEffects;
import org.apache.tinkerpop.gremlin.process.traversal.util.FastNoSuchElementException;
import org.apache.tinkerpop.gremlin.util.iterator.EmptyIterator;
import org.unipop.process.traverser.UniGraphTraverserStep;

import java.util.*;
import java.util.function.BinaryOperator;
import java.util.function.Supplier;

/**
 * Created by sbarzilay on 3/15/16.
 */
public class UniGraphCoalesceStep<S, E> extends FlatMapStep<S, E> implements TraversalParent {
    private final List<Traversal.Admin<S, E>> coalesceTraversals;
    private Iterator<Traverser.Admin<E>> results = EmptyIterator.instance();

    @Override
    public Set<TraverserRequirement> getSelfAndChildRequirements(TraverserRequirement... selfRequirements) {
        return new HashSet<TraverserRequirement>() {{
            add(TraverserRequirement.SIDE_EFFECTS);
        }};
    }

    public UniGraphCoalesceStep(Traversal.Admin traversal, List<Traversal.Admin<S, E>> coalesceTraversals) {
        super(traversal);
        this.coalesceTraversals = coalesceTraversals;
        this.coalesceTraversals.forEach(t -> t.addStep(new UniGraphTraverserStep<>(t.asAdmin())));
        this.coalesceTraversals.forEach(this::integrateChild);
    }

    @Override
    protected Traverser.Admin<E> processNextStart() {

        while (!results.hasNext() && starts.hasNext())
            results = query(starts);

        if (results.hasNext())
            return results.next();

        throw FastNoSuchElementException.instance();
    }

    private Iterator<Traverser.Admin<E>> query(Iterator<Traverser.Admin<S>> traversers) {
        List<Traverser.Admin<E>> coalesce = new ArrayList<>();
        List<Traverser.Admin<S>> traversersList = Lists.newArrayList(traversers);
        traversersList.forEach(t -> {
            t.setSideEffects(new DefaultTraversalSideEffects() {{
                register(t.toString(), () -> t, BinaryOperator.maxBy((o1, o2) -> 1));
                set(t.toString(), t);
            }});
        });
        coalesceTraversals.forEach(t -> {
            traversersList.forEach(t::addStart);
            while (t.hasNext()) {
                Traverser<E> item = (Traverser<E>) t.next();
                ((B_O_S_SE_SL_Traverser) item).getSideEffects().forEach((key, value) -> {
                    if (value != null && value instanceof Traverser)
                        traversersList.remove(value);
                });
                coalesce.add((item.asAdmin()));
            }
        });
        return coalesce.iterator();
    }

    @Override
    protected Iterator<E> flatMap(Traverser.Admin<S> traverser) {
        for (final Traversal.Admin<S, E> coalesceTraversal : this.coalesceTraversals) {
            if (coalesceTraversal.hasNext())
                return coalesceTraversal;
        }
        return EmptyIterator.instance();
    }

    @Override
    public Set<TraverserRequirement> getRequirements() {
        return this.getSelfAndChildRequirements();
    }
}
