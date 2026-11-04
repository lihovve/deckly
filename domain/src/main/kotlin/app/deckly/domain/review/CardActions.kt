package app.deckly.domain.review

import app.deckly.domain.model.DeckState
import app.deckly.domain.model.Section
import app.deckly.domain.model.Side
import app.deckly.domain.review.Availability.Available
import app.deckly.domain.review.Availability.Unavailable
import app.deckly.domain.review.CardAction.BROWSE
import app.deckly.domain.review.CardAction.CREATE
import app.deckly.domain.review.CardAction.DELETE
import app.deckly.domain.review.CardAction.DID_NOT_KNOW
import app.deckly.domain.review.CardAction.EDIT
import app.deckly.domain.review.CardAction.FLIP
import app.deckly.domain.review.CardAction.KNEW
import app.deckly.domain.review.CardAction.POSTPONE
import app.deckly.domain.review.CardAction.RETURN_TO_NEW

enum class CardAction { DELETE, RETURN_TO_NEW, EDIT, CREATE, KNEW, DID_NOT_KNOW, POSTPONE, BROWSE, FLIP }

enum class UnavailableReason { FRONT_VISIBLE, ANSWER_REVEALED, ALREADY_POSTPONED, LAST_IN_PASS }

sealed interface Availability {
    data object Available : Availability
    data class Unavailable(val reason: UnavailableReason) : Availability
}

/**
 * Действия, которые открытая секция предлагает по таблице «Действия с карточкой» ТЗ, и доступность
 * каждого. Действий, которых секция не предлагает, в карте нет.
 */
fun computeCardActions(state: DeckState): Map<CardAction, Availability> {
    val deck = state.deck
    val card = findCurrentCard(state)
    if (isActivePassOpen(deck)) {
        if (card == null) return emptyMap()
        val ratingAvailability = if (deck.visibleSide == Side.FRONT) Unavailable(UnavailableReason.FRONT_VISIBLE) else Available
        val postponeAvailability = findPostponeBlock(state, card)?.let(::Unavailable) ?: Available
        return mapOf(
            DELETE to Available,
            RETURN_TO_NEW to Available,
            KNEW to ratingAvailability,
            DID_NOT_KNOW to ratingAvailability,
            POSTPONE to postponeAvailability,
            FLIP to Available,
        )
    }
    return when (deck.activeSection) {
        Section.NEW ->
            if (card == null) listOf(CREATE).associateWith { Available }
            else listOf(DELETE, EDIT, CREATE, BROWSE, FLIP).associateWith { Available }

        Section.LEARNED ->
            if (card == null) emptyMap() else listOf(DELETE, BROWSE, FLIP).associateWith { Available }

        else ->
            if (card == null) emptyMap() else listOf(DELETE, RETURN_TO_NEW, BROWSE, FLIP).associateWith { Available }
    }
}
