package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.Context
import dev.ancaria.coderpack.api.Priority
import dev.ancaria.coderpack.api.event.Gold
import dev.ancaria.coderpack.api.event.Hero

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertSame
import kotlin.test.assertTrue

class EventsTest {

    private fun hero(level: Int = 7) = Hero(mapOf("level" to level.toString()))

    @Test
    fun `on passes the reified type and the annotation defaults through`() {
        val bus = Bus()
        bus.on<Hero> { }

        assertEquals(Hero::class.java, bus.type)
        assertEquals(Priority.NORMAL, bus.priority)
        assertEquals(false, bus.ignoreVetoed)
    }

    @Test
    fun `on carries a priority and a skip when it is given one`() {
        val bus = Bus()
        bus.on<Gold>(Priority.MONITOR, ignoreVetoed = true) { }

        assertEquals(Gold::class.java, bus.type)
        assertEquals(Priority.MONITOR, bus.priority)
        assertEquals(true, bus.ignoreVetoed)
    }

    @Test
    fun `the block registers on the same bus the context hands out`() {
        val bus = Bus()
        val context = FakeContext(bus)
        context.events {
            on<Hero> { }
        }

        assertEquals(Hero::class.java, bus.type)
        assertEquals(1, bus.live.size)
    }

    @Test
    fun `once fires one event and takes itself off`() {
        val bus = Bus()
        val seen = mutableListOf<Int>()
        bus.once<Hero> { seen.add(it.level) }

        bus.fire(hero(3))
        bus.fire(hero(4))

        assertEquals(listOf(3), seen)
        assertTrue(bus.live.isEmpty(), "the listener is still on the bus")
    }

    // The reason once() is not four lines. A listener cannot hold a handle it
    // has not been given yet, and on a real bus the event can arrive first.
    @Test
    fun `once still unregisters when the event beats the handle`() {
        val bus = EagerBus(hero(9))
        var calls = 0
        bus.once<Hero> { calls++ }

        assertEquals(1, calls)
        assertEquals(0, bus.live, "nothing came off the bus")
    }

    @Test
    fun `two handles come off together`() {
        val bus = Bus()
        val both = bus.on<Hero> { } + bus.on<Gold> { }
        assertEquals(2, bus.live.size)

        both.unregister()

        assertTrue(bus.live.isEmpty())
    }

    @Test
    fun `a collection of handles comes off together`() {
        val bus = Bus()
        listOf(bus.on<Hero> { }, bus.on<Hero> { }, bus.on<Gold> { }).unregister()

        assertTrue(bus.live.isEmpty())
    }

    @Test
    fun `the mod base class keeps the context and runs load against it`() {
        val context = FakeContext()
        val mod = Probe()

        mod.onLoad(context)

        assertSame(context, mod.context)
        assertEquals("demo-mod", mod.seen)
        assertEquals(listOf("loaded"), context.logged)
    }

    /** A mod written the way the Kotlin template writes one. */
    private class Probe : SacredMod() {

        var seen: String? = null

        override fun Context.load() {
            seen = id()
            log("loaded")
        }
    }

    @Test
    fun `a raw field is readable through the operator and null when absent`() {
        val event = hero()

        assertEquals("7", event["level"])
        assertNull(event["nothing"])
        assertEquals(7L, event.long("level"))
        assertEquals(0L, event.long("nothing"))
    }
}
