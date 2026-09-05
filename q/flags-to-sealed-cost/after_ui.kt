package lab.orders.after

// ── C. Чтение в UI ────────────────────────────────────────────────────────
@Composable
fun OrdersScreen(state: OrdersState, onRetry: () -> Unit) {
    // D2 — РЕШЕНИЕ: эффект больше не может подписаться на «поле error».
    // Здесь он подписан на состояние целиком, и это меняет поведение:
    // две одинаковые ошибки подряд теперь дают один показ, а не два.
    // Честная альтернатива — вынести показ в отдельный поток событий,
    // но это уже второй рефакторинг внутри первого.
    LaunchedEffect(state) {
        if (state is OrdersState.Error) showSnackbar(state.message)
    }

    // C1, C2, C3 — СХЛОПНУЛИСЬ в один when: три ранних return стали ветками
    when (state) {
        OrdersState.Loading -> Spinner()
        is OrdersState.Error -> ErrorView(state.message, onRetry)
        OrdersState.Empty -> EmptyView()
        is OrdersState.Content -> {
            OrdersHeader(count = state.orders.size)  // C4 — механика
            OrdersList(state.orders)                 // C5 — не тронут
        }
    }
}

// C6 — механика, но правится в двух местах: объявление + каждый вызов.
// Два параметра ушли: внутри ветки Content они бессмысленны.
@Composable
fun OrdersHeader(count: Int) { /* ... */ }
