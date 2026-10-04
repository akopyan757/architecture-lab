// Стенд к разбору «форма как состояние: валидация, dirty/pristine» (issue #18).
//
// Четыре прогона, каждый печатает то, что утверждает разбор:
//   A — гейт показа ошибки: плоская модель против осей touched / submitted
//   B — dirty от факта правки против dirty от диффа; «не тронуто и пусто» vs «тронуто и очищено»
//   C — error хранить против выводить: расхождение и цена пересчёта
//   D — submit: флаг isValid против разобранного значения
//
// Запуск: bash run.sh (нужен только kotlinc, внешних зависимостей нет)

// --- правила валидации: одни и те же для всех моделей ------------------------

private var ruleCalls = 0

private fun required(v: String): String? {
    ruleCalls++
    return if (v.isBlank()) "обязательное поле" else null
}

private val EMAIL = Regex("^[^@\\s]+@[^@\\s.]+\\.[^@\\s]+$")

private fun email(v: String): String? {
    ruleCalls++
    return when {
        v.isBlank() -> "обязательное поле"
        !EMAIL.matches(v) -> "непохоже на адрес"
        else -> null
    }
}

// --- две модели одного поля --------------------------------------------------

/** Плоская: текст, текст ошибки и флаг валидности лежат рядом как три поля. */
private data class Flat(
    val text: String,
    val error: String? = null,
    val isValid: Boolean = false,
)

/**
 * С явными осями: в состоянии только то, что нельзя вывести из значения, —
 * само значение, исходный снимок и две оси взаимодействия.
 * error / dirty / показ ошибки считаются из них.
 */
private data class Field(
    val value: String,
    val initial: String,
    val touched: Boolean = false,   // поле получило и отдало фокус (blur)
    val edited: Boolean = false,    // был хотя бы один ввод
) {
    val dirtyByEdit: Boolean get() = edited
    val dirtyByDiff: Boolean get() = value != initial
}

private fun Field.type(v: String) = copy(value = v, edited = true)
private fun Field.blur() = copy(touched = true)

/** Гейт показа: ошибка есть всегда, видна — только после касания или попытки отправки. */
private fun Field.shownError(submitAttempted: Boolean, rule: (String) -> String?): String? =
    rule(value)?.takeIf { touched || submitAttempted }

// --- печать ------------------------------------------------------------------

private fun head(title: String) = println("\n== $title ==")
private fun row(vararg cells: String) =
    println(cells.mapIndexed { i, c -> if (i == 0) c.padEnd(26) else c.padEnd(30) }.joinToString("").trimEnd())

private fun err(e: String?) = e ?: "—"
private fun btn(enabled: Boolean) = if (enabled) "«Сохранить» активна" else "«Сохранить» серая"
private fun show(f: Flat) = "text=«${f.text}» error=${err(f.error)} isValid=${f.isValid}"

// --- A. гейт показа ошибки ---------------------------------------------------

private fun gate() {
    head("A. Обязательное поле: зашёл и вышел, ничего не введя")
    println("Шаги: экран открыт → фокус в поле → blur → тап «Сохранить». Поле обязательное, пустое.")

    // A1: плоская модель, валидация считается сразу при создании состояния
    var eager = Flat(text = "", error = required(""), isValid = false)
    println("\nA1 плоская, валидация при создании состояния:")
    row("экран открыт", err(eager.error), btn(eager.isValid))
    row("фокус в поле", err(eager.error), btn(eager.isValid))
    row("blur", err(eager.error), btn(eager.isValid))
    row("тап «Сохранить»", err(eager.error), btn(eager.isValid))
    println("  -> ошибка кричит на пустом экране, до любого действия пользователя")

    // A2: плоская модель, валидация только в обработчике ввода
    val lazyFlat = Flat(text = "", error = null, isValid = false) // onChange не случился ни разу
    println("\nA2 плоская, валидация только в onChange:")
    row("экран открыт", err(lazyFlat.error), btn(lazyFlat.isValid))
    row("фокус в поле", err(lazyFlat.error), btn(lazyFlat.isValid))
    row("blur", err(lazyFlat.error), btn(lazyFlat.isValid))
    row("тап «Сохранить»", err(lazyFlat.error), btn(lazyFlat.isValid))
    println("  -> ошибка не показана ни на одном шаге, кнопка серая без объяснения")

    // A3: оси
    var f = Field(value = "", initial = "")
    var submitAttempted = false
    println("\nA3 оси value / touched / submitted, error выводится:")
    row("экран открыт", err(f.shownError(submitAttempted, ::required)), btn(true))
    row("фокус в поле", err(f.shownError(submitAttempted, ::required)), btn(true))
    f = f.blur()
    row("blur", err(f.shownError(submitAttempted, ::required)), btn(true))
    submitAttempted = true
    row("тап «Сохранить»", err(f.shownError(submitAttempted, ::required)), "не отправлено")
    println("  -> ошибка появляется ровно на blur; submit отказывает и говорит почему")

    println("\nВторой заход на A3: пользователь не касался поля и сразу жмёт «Сохранить»")
    val untouched = Field(value = "", initial = "")
    row("без оси submitted", err(untouched.shownError(false, ::required)), "не отправлено")
    row("с осью submitted", err(untouched.shownError(true, ::required)), "не отправлено")
    println("  -> submitted не выводится из touched: без неё submit так же глух, как A2")
}

// --- B. dirty: правка против диффа -------------------------------------------

private fun dirty() {
    head("B. dirty от факта правки против dirty от диффа")

    println("B1 поле с исходным «Иван»: ввёл пробел и стёр — значение вернулось к исходному")
    val back = Field(value = "Иван", initial = "Иван").type("Иван ").type("Иван").blur()
    row("состояние", "value=«${back.value}» initial=«${back.initial}»", "touched=${back.touched} edited=${back.edited}")
    row("dirty по правке", "${back.dirtyByEdit}", btn(back.dirtyByEdit) + " -> PATCH без изменений")
    row("dirty по диффу", "${back.dirtyByDiff}", btn(back.dirtyByDiff) + " -> запроса нет")

    println("\nB2/B3 «не тронуто и пусто» против «тронуто и очищено» — одно и то же значение «»")
    val cleared = Field(value = "", initial = "Иван").type("").blur()   // очистил обязательное поле
    val fresh = Field(value = "", initial = "")                        // не касался пустого поля
    println("  оси:")
    row("тронуто и очищено", "diff=${cleared.dirtyByDiff} touched=${cleared.touched}", "показ: ${err(cleared.shownError(false, ::required))}")
    row("не тронуто и пусто", "diff=${fresh.dirtyByDiff} touched=${fresh.touched}", "показ: ${err(fresh.shownError(false, ::required))}")
    println("  плоская модель на тех же двух случаях:")
    row("тронуто и очищено", show(Flat(text = "", error = required(""))))
    row("не тронуто и пусто", show(Flat(text = "", error = required(""))))
    println("  -> состояния совпадают посимвольно: различить нечем, гейт выразить не на чем")
}

// --- C. error: хранить против выводить ---------------------------------------

private fun storedVsDerived() {
    head("C. error хранить против выводить")
    val edits = listOf("ab@", "ab@b.c", "ab@b.co")

    println("C1 хранимый error: подстановка значения мимо пересчёта")
    var stored = Flat(text = "", error = email(""), isValid = false)
    edits.forEachIndexed { i, v ->
        stored = if (i == 1) stored.copy(text = v)                      // восстановление черновика: только текст
        else email(v).let { stored.copy(text = v, error = it, isValid = it == null) }
        val honest = email(stored.text) == stored.error
        row("шаг ${i + 1}: «$v»", "error=${err(stored.error)}", if (honest) "" else "<- про прежнее значение")
    }
    println("  -> на шаге 2 адрес корректен, а под полем ошибка про прежнее значение,")
    println("     и кнопка «Сохранить» серая при валидной форме")

    println("\nC2 выводимый error, та же последовательность")
    var fld = Field(value = "", initial = "")
    edits.forEachIndexed { i, v ->
        fld = fld.type(v)
        row("шаг ${i + 1}: «$v»", "error=${err(email(fld.value))}", "расхождение невозможно")
    }

    println("\nЦена пересчёта на той же последовательности (3 правки):")
    ruleCalls = 0
    edits.forEach { v -> email(v) }
    println("  хранить: вызовов правила ${ruleCalls} (по одному на обновление)")
    ruleCalls = 0
    edits.forEach { v -> repeat(3) { email(v) } }   // баннер, активность кнопки, цвет рамки
    println("  выводить, три читателя на кадр: вызовов правила ${ruleCalls}")
    ruleCalls = 0
    var memoValue: String? = null
    var memoError: String? = null
    edits.forEach { v -> repeat(3) { if (memoValue != v) { memoError = email(v); memoValue = v } } }
    println("  выводить с мемоизацией по значению: вызовов правила ${ruleCalls} (последняя ошибка ${err(memoError)})")
}

// --- D. submit: флаг против разобранного значения ----------------------------

private sealed interface Parsed<out T> {
    data class Ok<T>(val value: T) : Parsed<T>
    data class Err(val message: String) : Parsed<Nothing>
}

private class Email private constructor(val raw: String) {
    override fun toString() = "Email($raw)"

    companion object {
        fun parse(v: String): Parsed<Email> =
            email(v)?.let { Parsed.Err(it) } ?: Parsed.Ok(Email(v))
    }
}

private fun send(e: Email) = "ушло на сервер: $e"

private fun submitGate() {
    head("D. submit: флаг isValid против разобранного значения")
    println("Один и тот же сбой: значение подменили мимо пересчёта (восстановление черновика).")

    println("\nD1 submit доверяет флагу:")
    val valid = email("ab@b.c").let { Flat(text = "ab@b.c", error = it, isValid = it == null) }
    row("после ручного ввода", show(valid))
    val stale = valid.copy(text = "ab@")                 // только текст, флаг остался прежним
    row("после подстановки", show(stale))
    row("submit при isValid", if (stale.isValid) "ушло на сервер: ${stale.text}" else "отказ")
    println("  -> флаг говорит «проверено», значение проверку не проходит; запрос ушёл")

    println("\nD2 submit требует разобранное значение:")
    listOf("ab@", "ab@b.c").forEach { v ->
        val out = when (val p = Email.parse(v)) {
            is Parsed.Ok -> send(p.value)
            is Parsed.Err -> "submit не вызван, форма показала: ${p.message}"
        }
        row("значение «$v»", out)
    }
    println("  -> ветки «проверено, но невалидно» нет: без Email запрос собрать не из чего")
}

fun main() {
    println("стенд к issue #18: форма как состояние")
    gate()
    dirty()
    storedVsDerived()
    submitGate()
}
