package dev.ancaria.coderpack.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * Marks a listener method. One event parameter, and a return type that says
 * what the method may do.
 *
 * <p>{@code void} is an observer: it has nothing to return, so it cannot decide
 * anything. Otherwise the return type must be exactly that event's nested
 * {@code Mutation}, which only a
 * {@link dev.ancaria.coderpack.api.event.Decides} event has. The mod linter
 * checks both at build time and the loader checks them again at registration.
 *
 * <p>A {@link Priority#MONITOR} method must be {@code void}. Priority is
 * ordering; the return type is permission; a monitor that asks for both is a
 * lint error.
 */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {

    /** When this listener runs. See {@link Priority}. */
    Priority priority() default Priority.NORMAL;

    /**
     * Skip this listener when an earlier one has vetoed the event.
     *
     * <p>Off by default, because a listener that undoes its own side effects
     * has to hear about the veto too. Turn it on when the listener only acts
     * on an event that is really going to happen, which is most of them.
     */
    boolean ignoreVetoed() default false;
}
