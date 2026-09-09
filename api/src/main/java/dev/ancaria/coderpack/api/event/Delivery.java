package dev.ancaria.coderpack.api.event;

import java.lang.annotation.Documented;
import java.lang.annotation.ElementType;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;

/**
 * What an event's arrival is like, for the part its Java type cannot say.
 * Whether a listener may change an event is decided by {@link Veto} and by the
 * compiler. These two marks describe the hook behind the event instead, how
 * completely it fires and how often, which is a fact about the agent and about
 * this loader version rather than about the contract.
 *
 * <p>An unmarked event is the ordinary case. It fires every time the game does
 * the thing, and rarely enough that a listener may take its time.
 */
public final class Delivery {

    private Delivery() {
    }

    /**
     * Not every path that should raise this event is hooked yet, so a listener
     * hears about some of them and not others. {@link #value()} says which part
     * is missing.
     *
     * <p>What does arrive is as accurate as any other event. What is missing is
     * completeness, so the mistake to avoid is reading absence as absence. "No
     * {@link Stored} arrived, therefore nothing entered the bag" is exactly the
     * inference this mark withdraws. A mod that must not miss one should
     * confirm the state another way, or accept a gap.
     *
     * <p>The mark comes off once coverage lands. Its absence means the paths
     * are covered as far as the hooks know, not that a hook can never miss.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Unstable {

        /** What is not covered, in one sentence a mod author can act on. */
        String value();
    }

    /**
     * This event fires far more often than the rest, on most frames while the
     * player is walking, where the others arrive a handful of times a minute.
     * Every listener on it runs on the loader's one dispatch thread, so
     * whatever a body allocates or waits for here it pays for continuously, on
     * the frame that can least afford it.
     *
     * <p>Arithmetic on the fields is fine. Logging, file and network work, and
     * per-event allocation are not. Nothing enforces this. The mark is here so
     * the cost is visible before a mod ships with a log line in the loop.
     */
    @Documented
    @Retention(RetentionPolicy.RUNTIME)
    @Target(ElementType.TYPE)
    public @interface Hot {
    }
}
