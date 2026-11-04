package app.deckly.data.db

import app.deckly.data.buildFlaggedCard
import app.deckly.data.buildFullDeck
import app.deckly.data.buildPlainCard
import app.deckly.domain.model.EpochValues
import app.deckly.domain.model.createDeck
import org.junit.Assert.assertEquals
import org.junit.Test

class MappingTest {

    @Test
    fun deckRoundTripsThroughEntity() {
        val deck = buildFullDeck()

        assertEquals(deck, mapDeck(mapDeckEntity(deck)))
    }

    @Test
    fun emptyAndSingleTailsRoundTrip() {
        val deck = buildFullDeck().copy(
            tail = EpochValues(emptyList(), listOf(5L), emptyList(), listOf(9L, 3L)),
        )

        assertEquals(deck, mapDeck(mapDeckEntity(deck)))
    }

    @Test
    fun freshDeckRoundTripsThroughEntity() {
        val deck = createDeck(3, "Новая")

        assertEquals(deck, mapDeck(mapDeckEntity(deck)))
    }

    @Test
    fun tailIsStoredAsCommaSeparatedIds() {
        val entity = mapDeckEntity(buildFullDeck())

        assertEquals("11,12,13", entity.tail1)
        assertEquals("", entity.tail2)
        assertEquals("7", entity.tail3)
    }

    @Test
    fun enumsAreStoredByName() {
        val entity = mapDeckEntity(buildFullDeck())

        assertEquals("EPOCH_3", entity.activeSection)
        assertEquals("BACK", entity.visibleSide)
    }

    @Test
    fun cardRoundTripsThroughEntity() {
        val card = buildFlaggedCard()

        val entity = mapCardEntity(card, deckId = 7, contentFile = "card-42-0123456789abcdef.dks")

        assertEquals(card, mapCard(entity))
        assertEquals(7L, entity.deckId)
        assertEquals("card-42-0123456789abcdef.dks", entity.contentFile)
    }

    @Test
    fun cardWithoutFlagsRoundTripsThroughEntity() {
        val card = buildPlainCard(id = 5, position = 4)

        assertEquals(card, mapCard(mapCardEntity(card, deckId = 1, contentFile = "x.dks")))
    }
}
