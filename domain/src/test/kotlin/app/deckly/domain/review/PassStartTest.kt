package app.deckly.domain.review

import app.deckly.domain.buildCard
import app.deckly.domain.buildDeck
import app.deckly.domain.buildDeckState
import app.deckly.domain.findCard
import app.deckly.domain.model.EpochValues
import app.deckly.domain.model.Section
import app.deckly.domain.model.listSectionCards
import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Assertions.assertFalse
import org.junit.jupiter.api.Assertions.assertTrue
import org.junit.jupiter.api.Test
import kotlin.random.Random

class PassStartTest {

    @Test
    fun firstEpochCardLeavesOnlyAfterNight() {
        val state = buildDeckState(
            buildDeck(),
            buildCard(1, Section.EPOCH_1, successes = 4, sectionDay = 5, lastSuccessDay = 5),
            buildCard(2, Section.EPOCH_1, successes = 4, sectionDay = 5, lastSuccessDay = 6),
        )

        val after = startPass(state, 1, today = 9, random = Random(1))

        val stayed = after.findCard(1)
        assertEquals(Section.EPOCH_1, stayed.section)
        assertEquals(4, stayed.successes)
        val released = after.findCard(2)
        assertEquals(Section.EPOCH_2, released.section)
        assertEquals(0, released.successes)
        assertEquals(9, released.sectionDay)
        assertTrue(released.excludedFromPass)
    }

    @Test
    fun cardsReleasedByThreshold() {
        val secondEpoch = startPass(
            buildDeckState(buildDeck(), buildCard(1, Section.EPOCH_2, successes = 4)),
            2, today = 9, random = Random(1),
        )
        assertEquals(Section.EPOCH_3, secondEpoch.findCard(1).section)

        val thirdEpoch = startPass(
            buildDeckState(
                buildDeck(),
                buildCard(1, Section.EPOCH_3, successes = 4),
                buildCard(2, Section.EPOCH_3, successes = 3),
            ),
            3, today = 9, random = Random(1),
        )
        assertEquals(Section.EPOCH_4, thirdEpoch.findCard(1).section)
        assertEquals(Section.EPOCH_3, thirdEpoch.findCard(2).section)

        val fourthEpoch = startPass(
            buildDeckState(
                buildDeck(),
                buildCard(1, Section.EPOCH_4, successes = 3),
                buildCard(2, Section.EPOCH_4, successes = 2),
                buildCard(10, Section.LEARNED, position = 0),
                buildCard(11, Section.LEARNED, position = 4),
            ),
            4, today = 9, random = Random(1),
        )
        val learned = fourthEpoch.findCard(1)
        assertEquals(Section.LEARNED, learned.section)
        assertEquals(0, learned.successes)
        assertTrue(fourthEpoch.listSectionCards(Section.LEARNED).dropLast(1).all { it.position < learned.position })
        assertEquals(1L, fourthEpoch.listSectionCards(Section.LEARNED).last().id)
        assertEquals(Section.EPOCH_4, fourthEpoch.findCard(2).section)
    }

    @Test
    fun nightRuleHoldsWhenDateMovedBack() {
        val state = buildDeckState(
            buildDeck(),
            buildCard(1, Section.EPOCH_1, successes = 4, sectionDay = 10, lastSuccessDay = 8),
        )

        val after = startPass(state, 1, today = 8, random = Random(1))

        assertEquals(Section.EPOCH_1, after.findCard(1).section)
    }

    @Test
    fun firstEpochRefillsUpToForty() {
        val epochCards = (1L..35L).map { buildCard(it, Section.EPOCH_1, position = (it - 1).toInt()) }
        // Позиции в пуле идут против порядка id: первые по position — id 109, 108, 107, 106, 105.
        val poolCards = (0L..9L).map { buildCard(100 + it, Section.NEW, position = (9 - it).toInt()) }
        val state = buildDeckState(buildDeck(), epochCards + poolCards)

        val after = startPass(state, 1, today = 9, random = Random(1))

        assertEquals(40, listPassCards(after, 1).size)
        assertEquals((0 until 40).toList(), after.listSectionCards(Section.EPOCH_1).map { it.position })
        assertEquals(setOf(105L, 106L, 107L, 108L, 109L), after.listSectionCards(Section.EPOCH_1).map { it.id }.toSet() - epochCards.map { it.id }.toSet())
        assertEquals(setOf(100L, 101L, 102L, 103L, 104L), after.listSectionCards(Section.NEW).map { it.id }.toSet())
        val refilled = after.findCard(109)
        assertEquals(0, refilled.successes)
        assertEquals(9, refilled.sectionDay)
        assertFalse(refilled.excludedFromPass)
    }

    @Test
    fun firstEpochRefillTakesWholePoolWhenItIsSmall() {
        val poolCards = (1L..10L).map { buildCard(it, Section.NEW, position = it.toInt()) }

        val after = startPass(buildDeckState(buildDeck(), poolCards), 1, today = 9, random = Random(1))

        assertEquals(10, after.listSectionCards(Section.EPOCH_1).size)
        assertEquals(0, after.listSectionCards(Section.NEW).size)
    }

    @Test
    fun firstEpochOverFortyIsNotRefilled() {
        val epochCards = (1L..41L).map { buildCard(it, Section.EPOCH_1, position = (it - 1).toInt()) }
        val poolCards = (100L..104L).map { buildCard(it, Section.NEW, position = (it - 100).toInt()) }

        val after = startPass(buildDeckState(buildDeck(), epochCards + poolCards), 1, today = 9, random = Random(1))

        assertEquals(41, after.listSectionCards(Section.EPOCH_1).size)
        assertEquals(5, after.listSectionCards(Section.NEW).size)
    }

    @Test
    fun learnedCheckOnOddFourthPass() {
        val today = 100
        val ready = (1L..12L).map {
            buildCard(it, Section.LEARNED, position = (it - 1).toInt(), sectionDay = today - 40)
        }
        val fresh = buildCard(13, Section.LEARNED, position = 12, sectionDay = today - 29)
        val ordinary = buildCard(50, Section.EPOCH_4, successes = 1)

        fun startFourthEpoch(pass4: Int) = startPass(
            buildDeckState(buildDeck(passCount = EpochValues(0, 0, 0, pass4)), ready + fresh + ordinary),
            4, today, Random(1),
        )

        val oddPass = startFourthEpoch(pass4 = 1)
        assertEquals((1L..10L).toSet() + 50L, oddPass.listSectionCards(Section.EPOCH_4).map { it.id }.toSet())
        assertEquals(setOf(11L, 12L, 13L), oddPass.listSectionCards(Section.LEARNED).map { it.id }.toSet())
        for (id in 1L..10L) {
            val checked = oddPass.findCard(id)
            assertTrue(checked.checking, "карточка $id на проверке")
            assertEquals(0, checked.successes)
            assertEquals(today, checked.sectionDay)
            assertFalse(checked.excludedFromPass)
        }
        assertFalse(oddPass.findCard(50).checking)

        val evenPass = startFourthEpoch(pass4 = 2)
        assertEquals(listOf(50L), evenPass.listSectionCards(Section.EPOCH_4).map { it.id })
        assertEquals(13, evenPass.listSectionCards(Section.LEARNED).size)
    }

    @Test
    fun passFlagsResetOnStart() {
        fun buildFlagged(id: Long, section: Section) = buildCard(
            id, section, successes = 1,
            postponed = true, answerRevealed = true, failed = true, excludedFromPass = true,
        )
        val otherEpoch = buildFlagged(3, Section.EPOCH_3)
        val state = buildDeckState(
            buildDeck(),
            buildFlagged(1, Section.EPOCH_2),
            buildFlagged(2, Section.EPOCH_2),
            otherEpoch,
        )

        val after = startPass(state, 2, today = 9, random = Random(1))

        for (id in listOf(1L, 2L)) {
            val card = after.findCard(id)
            assertFalse(card.postponed, "postponed у карточки $id")
            assertFalse(card.answerRevealed, "answerRevealed у карточки $id")
            assertFalse(card.failed, "failed у карточки $id")
            assertFalse(card.excludedFromPass, "excludedFromPass у карточки $id")
        }
        assertEquals(otherEpoch, after.findCard(3))
    }

    @Test
    fun shuffleKeepsLastShownOutOfFront() {
        for (size in 2..12) {
            val ids = (1L..size).toList()
            val keptOutCount = minOf(5, (size + 3) / 4)
            for (seed in 0 until 200) {
                val previousPass = ids.shuffled(Random(seed + 7919))
                val tail = previousPass.takeLast(5)

                val result = shuffleWithOffset(ids, tail, Random(seed))

                assertEquals(ids, result.sorted(), "перестановка входа: size=$size seed=$seed")
                val front = result.take(keptOutCount)
                assertTrue(
                    tail.takeLast(keptOutCount).none { it in front },
                    "последние показанные не впереди: size=$size seed=$seed tail=$tail result=$result",
                )
            }
        }
    }

    @Test
    fun twoCardsLastShownNeverFirst() {
        for (seed in 0 until 200) {
            val result = shuffleWithOffset(listOf(1L, 2L), previousTail = listOf(1L, 2L), random = Random(seed))

            assertEquals(1L, result.first(), "seed=$seed")
        }
    }

    @Test
    fun shuffleIgnoresCardsGoneFromEpoch() {
        val ids = (1L..8L).toList()
        // Последние m = 2 записи tail — 92 и 5; карточки 92 в эпохе уже нет, значит, впереди не должно быть только 5.
        val tail = listOf(90L, 91L, 3L, 92L, 5L)
        for (seed in 0 until 200) {
            val result = shuffleWithOffset(ids, tail, Random(seed))

            assertEquals(ids, result.sorted(), "seed=$seed")
            assertFalse(5L in result.take(2), "seed=$seed result=$result")
        }

        val allGone = shuffleWithOffset(ids, listOf(90L, 91L, 92L, 93L, 94L), Random(1))
        assertEquals(ids, allGone.sorted())

        assertEquals(listOf(7L), shuffleWithOffset(listOf(7L), listOf(7L), Random(1)))
        assertEquals(emptyList<Long>(), shuffleWithOffset(emptyList(), listOf(7L), Random(1)))
    }

    @Test
    fun positionsAreContiguousAfterStart() {
        val tail = listOf(2L, 4L)
        val deck = buildDeck(tail = EpochValues(emptyList(), tail, emptyList(), emptyList()))
        val positions = listOf(5, 9, 12, 20, 21, 30)
        val state = buildDeckState(deck, positions.mapIndexed { index, position ->
            buildCard(index + 1L, Section.EPOCH_2, position = position)
        })

        val after = startPass(state, 2, today = 9, random = Random(3))

        val expectedOrder = shuffleWithOffset(state.listSectionCards(Section.EPOCH_2).map { it.id }, tail, Random(3))
        val cardsAfter = after.listSectionCards(Section.EPOCH_2)
        assertEquals(expectedOrder, cardsAfter.map { it.id })
        assertEquals((0 until 6).toList(), cardsAfter.map { it.position })
    }

    @Test
    fun startOfEmptyEpochChangesNothing() {
        val state = buildDeckState(
            buildDeck(),
            buildCard(1, Section.EPOCH_1, position = 3, successes = 2),
            buildCard(2, Section.LEARNED, position = 0, sectionDay = 0),
        )

        assertEquals(state, startPass(state, 3, today = 9, random = Random(1)))
    }

    @Test
    fun startPassLeavesDeckFieldsToEngine() {
        val deck = buildDeck(
            passStarted = true,
            passCount = EpochValues(7, 1, 2, 0),
            passCursor = 3,
            browseCursor = 2,
            tail = EpochValues(listOf(1L, 2L), emptyList(), emptyList(), emptyList()),
        )
        val state = buildDeckState(
            deck,
            buildCard(1, Section.EPOCH_1, successes = 4, sectionDay = 1, lastSuccessDay = 3),
            buildCard(2, Section.NEW),
        )

        assertEquals(deck, startPass(state, 1, today = 9, random = Random(1)).deck)
    }

    @Test
    fun passCardsSkipExcludedAndFollowPosition() {
        val state = buildDeckState(
            buildDeck(),
            buildCard(1, Section.EPOCH_2, position = 2),
            buildCard(2, Section.EPOCH_2, position = 0, excludedFromPass = true),
            buildCard(3, Section.EPOCH_2, position = 1),
            buildCard(4, Section.EPOCH_3, position = 0),
        )

        assertEquals(listOf(3L, 1L), listPassCards(state, 2).map { it.id })
    }
}
