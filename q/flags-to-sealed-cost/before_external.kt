// Внешние потребители полей. В оценке «да там на полчаса» их не видно:
// это чужие файлы, в которые ты заходишь только на этапе правки.

package lab.orders.analytics

// E1 — аналитика лезет в поля состояния напрямую
fun trackOrdersShown(state: OrdersState) {
    analytics.log(
        event = "orders_shown",
        params = mapOf(
            "loading" to state.isLoading,
            "has_error" to (state.error != null),
            "count" to state.orders.size,
        ),
    )
}

// E2 — дебажный оверлей, включаемый в debug-сборке
@Composable
fun StateDebugOverlay(state: OrdersState) {
    Text("loading=${state.isLoading} error=${state.error} n=${state.orders.size}")
}
