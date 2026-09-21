package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.Events
import dev.ancaria.coderpack.api.Handle
import dev.ancaria.coderpack.api.Priority
import dev.ancaria.coderpack.api.event.Decides
import dev.ancaria.coderpack.api.event.Event
import dev.ancaria.coderpack.api.event.EventMutation

import java.util.function.Consumer
import java.util.function.Function

/**
 * What a listener body runs against: `this` is the scope, `it` is the event.
 *
 * Java needs two methods, `on` and `decide`, because one overloaded name cannot
 * carry both lambda shapes. Kotlin gets one, because the shape is the same on
 * both sides here: the body always returns `Unit` and says what it decided by
 * calling [mutate], which only exists when there is something to decide.
 */
public class On<E : Event> @PublishedApi internal constructor(

    /** The event, also reachable as `it`. */
    public val event: E,
) {

    @PublishedApi
    internal var answer: EventMutation? = null
}

/**
 * Says what this listener decides.
 *
 * ```
 * on<Experience> { mutate { Experience.Mutation.change(it.value * 2) } }
 * ```
 *
 * In scope only when the event has something to decide: [Decides] names that
 * event's mutation type, and an event without it has no such function to call.
 * So `on<Death> { mutate { ... } }` is a compile error rather than a rule.
 *
 * Calling it twice in one body keeps the last answer. The loader folds one
 * answer per listener.
 */
public fun <E, M : EventMutation> On<E>.mutate(build: () -> M)
        where E : Event, E : Decides<M> {
    answer = build()
}

/**
 * Registers a listener for [E], the type argument taking the place of the
 * `Class` the Java call needs:
 *
 * ```
 * events.on<Death> { log(it.blow) }
 * events.on<Damage>(Priority.LAST) { mutate { Damage.Mutation.change(it.maxHp) } }
 * ```
 *
 * Same bus, same order, same fold.
 */
public inline fun <reified E : Event> Events.on(
    priority: Priority = Priority.NORMAL,
    ignoreVetoed: Boolean = false,
    crossinline body: On<E>.(E) -> Unit,
): Handle = scoped(E::class.java, priority, ignoreVetoed) { event ->
    On(event).apply { body(event) }.answer
}

/** [on], straight off the context, for a mod that only registers a listener or two. */
public inline fun <reified E : Event> dev.ancaria.coderpack.api.Context.on(
    priority: Priority = Priority.NORMAL,
    ignoreVetoed: Boolean = false,
    crossinline body: On<E>.(E) -> Unit,
): Handle = events().on(priority, ignoreVetoed, body)

/**
 * The half of [on] that is a cast rather than sugar, kept out of the inline
 * body so there is one copy of it and it can be read.
 *
 * Java splits registration by what a listener may do, and it is right to: a
 * `Consumer` cannot answer and a `Function` must. Kotlin hides the split
 * because the body is `Unit` either way, which leaves one thing to do at run
 * time: send an event that can be decided to `decide` and everything else to
 * `on`.
 *
 * The cast is what that costs. `decide` is bounded on `E : Event & Decides<M>`,
 * an intersection this function's `E` is not known to satisfy, and Kotlin has
 * no syntax for casting to one. `Nothing` is a subtype of every type, so it
 * satisfies the bound by construction and erasure makes the call correct at run
 * time. Nothing unsound reaches a mod through it: [mutate] is the only way to
 * set an answer, and it is in scope only for the events this branch selects.
 */
@Suppress("UNCHECKED_CAST")
@PublishedApi
internal fun <E : Event> Events.scoped(
    type: Class<E>,
    priority: Priority,
    ignoreVetoed: Boolean,
    body: (E) -> EventMutation?,
): Handle =
    if (Decides::class.java.isAssignableFrom(type)) {
        decideAs<EventMutation, Nothing>(
            type as Class<Nothing>,
            priority,
            ignoreVetoed,
            body as (Nothing) -> EventMutation?,
        )
    } else {
        on(type, priority, ignoreVetoed, Consumer { body(it) })
    }

/** The typed call the cast above lands on. */
private fun <M : EventMutation, D> Events.decideAs(
    type: Class<D>,
    priority: Priority,
    ignoreVetoed: Boolean,
    body: (D) -> EventMutation?,
): Handle where D : Event, D : Decides<M> =
    @Suppress("UNCHECKED_CAST")
    decide(type, priority, ignoreVetoed, Function { event -> body(event) as M })
