package lab.orders.analytics

// E1 — переписан целиком, и это показательно: аналитике нужен был плоский
// набор полей, и его приходится собирать обратно вручную.
fun trackOrdersShown(state: OrdersState) {
    analytics.log(
        event = "orders_shown",
        params = mapOf(
            "state" to when (state) {
                OrdersState.Loading -> "loading"
                is OrdersState.Error -> "error"
                OrdersState.Empty -> "empty"
                is OrdersState.Content -> "content"
            },
            "count" to (state as? OrdersState.Content)?.orders?.size.orZero(),
        ),
    )
}

// E2 — переписан; строка стала короче, но зайти в файл всё равно пришлось
@Composable
fun StateDebugOverlay(state: OrdersState) {
    Text(state.toString())
}
