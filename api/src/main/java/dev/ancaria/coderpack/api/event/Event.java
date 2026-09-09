package dev.ancaria.coderpack.api.event;

import java.util.Collections;
import java.util.HashMap;
import java.util.Map;

import javax.annotation.Nonnull;
import javax.annotation.Nullable;

/**
 * Base of every event. The wire carries flat string fields. Subclasses name
 * the ones that matter and leave the rest reachable through {@link #fields()},
 * so a new field on the agent side does not require an SDK release to be
 * usable.
 */
public abstract class Event {

    private final Map<String, String> fields;

    // Filled on the first num() for a key. A getter is the natural thing to
    // call twice, once in a condition and once in the arithmetic, and every
    // call used to re-run parseLong over the same string.
    private Map<String, Long> numbers;

    protected Event(Map<String, String> fields) {
        this.fields = fields;
    }

    /** Raw fields as they arrived. */
    @Nonnull
    public final Map<String, String> fields() {
        return Collections.unmodifiableMap(fields);
    }

    /** The field as it arrived, or null when the agent did not send it. */
    @Nullable
    public final String text(String key) {
        return fields.get(key);
    }

    // num() and once() are the same read with and without the memo, and which
    // one an event wants is not a matter of taste. What decides it is whether
    // the memo is ever read a second time, and how often the event fires.
    //
    //   num()  memoizes. Right wherever a getter is plausibly read twice, or
    //          wherever the event is rare enough that one HashMap is noise.
    //          That is nearly everything here. Damage alone has three getters a
    //          handler reads once in a condition and again in the arithmetic,
    //          and every vetoable event has the game thread stopped around it,
    //          next to which a map costs nothing measurable.
    //   once() does not. Right only where the event is hot AND each getter is
    //          read once. There the memo turns two parseLong calls into a
    //          HashMap plus two boxed puts, 232 bytes a frame against 24, and
    //          buys nothing on the frame that can least afford it.
    //
    // Position is the only event that meets both, and says why at length. The
    // inconsistency between them is deliberate. Read that comment before
    // resolving it in either direction.

    /** The field as a number, parsed on the first call and remembered. */
    public final long num(String key) {
        if (numbers == null) {
            numbers = new HashMap<>(8);
        }
        Long known = numbers.get(key);
        if (known != null) {
            return known;
        }
        long value = parse(fields.get(key));
        numbers.put(key, value);
        return value;
    }

    /** The field as a number, parsed every time and nothing kept. */
    protected final long once(String key) {
        return parse(fields.get(key));
    }

    private static long parse(@Nullable String raw) {
        if (raw == null) {
            return 0;
        }
        try {
            return Long.parseLong(raw);
        } catch (NumberFormatException e) {
            return 0;
        }
    }
}
