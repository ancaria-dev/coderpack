package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.Context
import dev.ancaria.coderpack.api.Game

import java.nio.file.Path

// The API is Java, so its readers are methods rather than getters: `id()`, not
// `getId()`. Kotlin only synthesizes a property for the second shape, so these
// say the first one out loud. Every one is inline and forwards to the method it
// is named after; the bytecode a mod ends up with is the call it would have
// written itself.
//
// `Context.events` is deliberately not among them. The registration block in
// Events.kt is already called `events`, and a property of the same name beside
// it would make `context.events { }` a question about overload resolution
// rather than a line somebody reads.

/** This mod's id, from its descriptor. */
public inline val Context.id: String get() = id()

/** Acting on the game rather than reacting to it. */
public inline val Context.game: Game get() = game()

/** The Sacred Gold install directory, the folder that holds mods/ and the game. */
public inline val Context.gameDir: Path get() = gameDir()
