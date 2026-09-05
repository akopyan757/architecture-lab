// Тот же экран, UI-часть. Отдельный файл — потому что в реальном проекте
// точки чтения лежат не там же, где точки записи, и это влияет на счёт.

package lab.orders.before

// ── C. Чтение в UI ────────────────────────────────────────────────────────
@Composable
fun OrdersScreen(state: OrdersState, onRetry: () -> Unit) {
    // D2 — эффект подписан на ОДНО поле
    LaunchedEffect(state.error) {
        if (state.error != null) showSnackbar(state.error)
    }

    // C1
    if (state.isLoading) { Spinner(); return }
    // C2
    if (state.error != null) { ErrorView(state.error, onRetry); return }
    // C3
    if (state.orders.isEmpty()) { EmptyView(); return }

    // C4
    OrdersHeader(count = state.orders.size, isLoading = state.isLoading, error = state.error)
    // C5
    OrdersList(state.orders)
}

// C6 — сигнатура построена на полях состояния: правится здесь И в каждом вызове
@Composable
fun OrdersHeader(count: Int, isLoading: Boolean, error: String?) { /* ... */ }
