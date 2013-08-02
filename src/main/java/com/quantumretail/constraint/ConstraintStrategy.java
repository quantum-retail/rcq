package com.quantumretail.constraint;

/**
 * TODO: document me.
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
