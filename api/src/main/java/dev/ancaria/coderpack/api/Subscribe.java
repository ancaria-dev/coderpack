package dev.ancaria.coderpack.api;

import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/** Marks a listener method. One event parameter, no return value. */
@Retention(RetentionPolicy.RUNTIME)
@Target(ElementType.METHOD)
public @interface Subscribe {

    /** When this listener runs. See {@link Priority}. */
    Priority priority() default Priority.NORMAL;

    /**
     * Skip this listener when the event has already been cancelled.
     *
     * <p>Off by default, because a listener that undoes its own side effects
     * has to hear about the cancel too. Turn it on when the listener only acts
     * on an event that is really going to happen -- which is most of them.
     */
    boolean ignoreCancelled() default false;
}
