package org.phantam.fozminesproofapi.action;

/**
 * Generic functional interface for executing an action on a bot or related data.
 * <p>
 * Implementations should handle their own error cases and return a meaningful result.
 * The action is expected to be side-effect free or manage its own transaction context.
 *
 * @param <T> the type of the target input (e.g., String for bot name, or a request object)
 * @param <R> the type of the result returned by the action
 */
@FunctionalInterface
public interface IBotAction<T, R> {

    /**
     * Executes the action with the given target and returns the result.
     *
     * @param target the target data required to perform the action
     * @return the result of the execution, typically a boolean or a status object
     * @throws RuntimeException if the action fails unexpectedly (implementations should wrap checked exceptions)
     */
    R execute(T target);
}