package app.deckly.domain.model

import org.junit.jupiter.api.Assertions.assertEquals
import org.junit.jupiter.api.Test

class CardUnitsTest {
    @Test
    fun sheetIsOneThousandUnitsWideAndKeepsMockupProportion() {
        assertEquals(1000f, CardUnits.WIDTH)
        // Лист макета 928 × 963: высота в единицах карточки по той же пропорции.
        assertEquals(1000f * 963f / 928f, CardUnits.HEIGHT, 0.001f)
    }

    @Test
    fun designUnitIsOneNinehundredTwentyEighthOfSheetWidthTimesThousand() {
        assertEquals(1000f / 928f, CardUnits.PER_DESIGN_UNIT, 0.00001f)
        // 928 единиц макета — вся ширина листа.
        assertEquals(CardUnits.WIDTH, 928f * CardUnits.PER_DESIGN_UNIT, 0.01f)
    }
}
