package com.quantumretail.constraint;

/**
 * The strategy that the ResourceConstrainingQueue will use to decide whether it should return an item. In effect, it
 * is answering the question "do we have resources for this item?" where "this item" is the next item on the list.
 *
 * This is not expected to block; the ResourceConstrainingQueue itself will handle the blocking behavior if this returns false.
 *
 * It may be called very frequently, so it should not be an expensive operation.
 *
 */
public interface ConstraintStrategy<T> {

    /**
     * Returns true if we should return this item -- implicitly, "do we have resources for this item?"
     * The implementation may choose to ignore the parameter entirely.
     *
     * @param nextItem
     * @return
     */
    public boolean shouldReturn(T nextItem);
}
