package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.entity.Item
import dev.ancaria.coderpack.api.event.Damage
import dev.ancaria.coderpack.api.event.Gold

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PropertiesTest {

    // The properties are one line each and the compiler checks their types, so
    // what is worth a test is the half that is not a rename: an assignment has
    // to reach the rewrite map the loader answers the host from, a read has to
    // survive a field the agent did not send, and the two have to stay the two
    // halves the API says they are rather than the round trip a `var` looks
    // like.

    @Test
    fun `assigning a vetoable field writes the rewrite the loader reads`() {
        val gold = Gold(mapOf("delta" to "10", "current" to "50", "dir" to "gain"))

        gold.delta = gold.delta * 3 / 2

        assertEquals(mapOf("delta" to "15"), gold.rewrites())
        assertEquals(50L, gold.current)
        assertEquals(false, gold.spending)
    }

    // The one thing about these properties that has to be learned rather than
    // guessed, so it is pinned here. Assigning does not change what the field
    // reads: it asks for a different value, and the game's own number stays
    // visible to this listener and to every later one. That is what `delta(x)`
    // then `delta()` does in Java, and a Kotlin mod that disagreed with a Java
    // mod about one event would be worse than a `var` that surprises once.
    @Test
    fun `assigning does not change what the field reads`() {
        val gold = Gold(mapOf("delta" to "10"))

        gold.delta = 99

        assertEquals(10L, gold.delta)
        assertEquals(mapOf("delta" to "99"), gold.rewrites())
    }

    @Test
    fun `compound assignment reads once and asks for the sum`() {
        val damage = Damage(mapOf("prev" to "100", "next" to "60", "max" to "120"))

        damage.next += 20

        assertEquals(mapOf("next" to "80"), damage.rewrites())
        assertEquals(60L, damage.next)
    }

    @Test
    fun `a missing field reads as zero rather than throwing`() {
        val gold = Gold(mapOf("delta" to "10"))

        assertEquals(0L, gold.current)
        assertNull(gold["nothing"])
    }

    @Test
    fun `a modifier is its value or null, never a zero that means absent`() {
        val item = Item(mapOf("name" to "TYPE_OBJECT_RING_FIRE01", "mods" to "601:3,802:0"))

        assertEquals(3, item[601])
        assertEquals(0, item[802])
        assertNull(item[999])
        assertEquals(mapOf(601 to 3, 802 to 0), item.modifiers)
    }

    @Test
    fun `an unknown type name is null rather than the id zero`() {
        val game = FakeGame()

        assertEquals(5171, game.typeIdOrNull("TYPE_OBJECT_RING"))
        assertNull(game.typeIdOrNull("TYPE_OBJECT_NOTHING"))
    }
}
