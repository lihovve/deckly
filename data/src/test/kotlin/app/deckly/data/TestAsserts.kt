package app.deckly.data

/** Проверяет, что [block] бросает [T], и возвращает исключение; другое исключение или его отсутствие — провал. */
suspend inline fun <reified T : Throwable> assertSuspendThrows(block: suspend () -> Unit): T {
    try {
        block()
    } catch (thrown: Throwable) {
        if (thrown is T) return thrown
        throw AssertionError("Ожидалось ${T::class.simpleName}, брошено $thrown", thrown)
    }
    throw AssertionError("Ожидалось ${T::class.simpleName}, исключения не было")
}
