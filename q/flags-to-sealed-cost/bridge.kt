// Промежуточный период: обе модели живут рядом, команда не останавливается.
// Мост строится ТОЛЬКО в одну сторону — и здесь видно, почему.

package lab.orders.bridge

import lab.orders.after.OrdersState

data class LegacyOrdersState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val orders: List<Order> = emptyList(),
)

// ── Новое → старое: перевод ТОТАЛЬНЫЙ ─────────────────────────────────────
// Каждый вариант sealed однозначно ложится в набор полей. Один файл,
// четыре строки, ноль решений. Столько стоит промежуточный период.
fun OrdersState.toLegacy(): LegacyOrdersState = when (this) {
    OrdersState.Loading -> LegacyOrdersState(isLoading = true)
    is OrdersState.Error -> LegacyOrdersState(error = message)
    OrdersState.Empty -> LegacyOrdersState(orders = emptyList())
    is OrdersState.Content -> LegacyOrdersState(orders = orders)
}

// ── Старое → новое: перевод НЕ ТОТАЛЬНЫЙ ──────────────────────────────────
// Старых комбинаций больше, чем новых вариантов, и часть из них
// не переводится никак — на них приходится ПРИДУМЫВАТЬ ответ.
fun LegacyOrdersState.toNew(): OrdersState = when {
    // Комбинации, которых «не бывает», но тип их разрешает:
    isLoading && error != null ->
        TODO("грузим и одновременно ошибка — что показать?")
    error != null && orders.isNotEmpty() ->
        TODO("ошибка поверх старых данных — Error теряет список, Content прячет ошибку")

    // Осмысленное:
    isLoading -> OrdersState.Loading
    error != null -> OrdersState.Error(error)
    orders.isEmpty() -> OrdersState.Empty
    else -> OrdersState.Content(orders)
}

// Два TODO выше — не лень, а диагноз: пока нужен обратный мост, старая
// модель продолжает ПРОИЗВОДИТЬ невалидные комбинации, и миграция
// не движется. Правило: строим только toLegacy(). Появился toNew() —
// значит, границу выбрали не там.
