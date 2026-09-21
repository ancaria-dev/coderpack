package dev.ancaria.coderpack.ktx

import dev.ancaria.coderpack.api.entity.Item
import dev.ancaria.coderpack.api.event.Damage
import dev.ancaria.coderpack.api.event.Fold
import dev.ancaria.coderpack.api.event.Gold

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PropertiesTest {

    // The properties are one line each and the compiler checks their types, so
    // what is worth a test is the half that is not a rename: that a read
    // survives a field the agent did not send, and that `value` reports the
    // fold rather than the arrived number.
    //
    // What used to be pinned here was the opposite, and it is gone with the
    // `var`s: assigning a field once left the getter reading the game's own
    // number, so a second mod never saw the first. An event is read-only now,
    // a listener answers with a mutation, and the fold is what the next
    // listener reads.

    @Test
    fun `value reports the fold and initial keeps the arrived number`() {
        val gold = Gold(mapOf("delta" to "10", "current" to "50", "dir" to "gain"))

        Fold.apply(gold, Gold.Mutation.of(gold.value * 3 / 2))

        assertEquals(15L, gold.value)
        assertEquals(10L, gold.initial)
        assertEquals(mapOf("delta" to "15"), Fold.verdict(gold))
        assertEquals(50L, gold.current)
        assertEquals(false, gold.spending)
    }

    @Test
    fun `two listeners doubling the same number compose`() {
        val damage = Damage(mapOf("prev" to "100", "next" to "60", "max" to "120"))

        Fold.apply(damage, Damage.Mutation.of(damage.value + 20))
        Fold.apply(damage, Damage.Mutation.of(damage.value + 20))

        assertEquals(100L, damage.value)
        assertEquals(60L, damage.initial)
        assertEquals(mapOf("next" to "100"), Fold.verdict(damage))
    }

    @Test
    fun `a veto hides the value and a reset brings the game number back`() {
        val gold = Gold(mapOf("delta" to "10"))

        Fold.apply(gold, Gold.Mutation.of(99))
        Fold.apply(gold, Gold.Mutation.veto())
        assertEquals(true, gold.vetoed)
        assertEquals(mapOf(), Fold.verdict(gold))

        Fold.apply(gold, Gold.Mutation.reset())
        assertEquals(false, gold.vetoed)
        assertEquals(10L, gold.value)
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
