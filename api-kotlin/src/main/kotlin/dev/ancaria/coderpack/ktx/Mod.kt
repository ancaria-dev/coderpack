package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.Context
import dev.ancaria.coderpack.api.SacredMod as ApiMod

/**
 * A mod, with the context already put away.
 *
 * The interface hands `onLoad` a [Context] and a mod needs it again from every
 * listener afterwards, so the first three lines of a Kotlin mod are always the
 * same `lateinit var` and the same assignment. This writes them once:
 *
 * ```
 * class GoldRush : SacredMod() {
 *
 *     override fun Context.load() {
 *         events {
 *             on<Gold> { it.delta = it.delta * 3 / 2 }
 *         }
 *         log("Loaded.")
 *     }
 * }
 * ```
 *
 * The context is the receiver of [load], which is what makes `events { }` and
 * `log(...)` read as bare calls, and it stays available as [context] from
 * anywhere else in the class, a listener body or a thread the mod started.
 *
 * Extending this is optional and costs nothing to skip. Implementing
 * [dev.ancaria.coderpack.api.SacredMod] directly works exactly as it does from
 * Java, and every extension in this package works either way.
 *
 * The name is deliberately the one the interface already has. A Kotlin mod
 * imports `dev.ancaria.coderpack.ktx.SacredMod` instead of
 * `dev.ancaria.coderpack.api.SacredMod` and writes the same word; nothing else
 * about the mod changes.
 */
public abstract class SacredMod : ApiMod {

    /**
     * What the loader handed this mod, from the moment [load] starts.
     *
     * Reading it before that throws, which is the same thing as saying a mod
     * has no context until it is loaded. There is no path into a mod's code
     * before `onLoad`, so nothing can observe the gap.
     */
    public lateinit var context: Context
        private set

    final override fun onLoad(context: Context) {
        this.context = context
        context.load()
    }

    /**
     * Register listeners here. The world may not exist yet, so wait for a
     * [dev.ancaria.coderpack.api.event.World] or
     * [dev.ancaria.coderpack.api.event.Hero] event before touching the player.
     *
     * Named `load` rather than `onLoad` because it cannot be called that. A
     * receiver becomes the first parameter on the JVM, so `Context.onLoad()`
     * and the `onLoad(Context)` above are one signature, and the compiler
     * rejects the pair before this class exists.
     */
    protected abstract fun Context.load()
}
