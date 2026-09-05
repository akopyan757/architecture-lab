// Тот же экран ПОСЛЕ миграции. Метки те же — видно, во что превратилась
// каждая точка. Метка «решение» = место, где правка не механическая.

package lab.orders.after

data class Order(val id: String, val title: String)

// ── A. Определение модели ─────────────────────────────────────────────────
// A1 — механика
sealed interface OrdersState {
    data object Loading : OrdersState
    data class Error(val message: String) : OrdersState
    data object Empty : OrdersState
    data class Content(val orders: List<Order>) : OrdersState
}

// ── B. Конструирование состояния ──────────────────────────────────────────
class OrdersViewModel(
    private val repo: OrdersRepository,
    private val scope: CoroutineScope,
) {
    // B1 — РЕШЕНИЕ: чем экран открывается.
    // Раньше дефолт был «не грузим, пусто, без ошибки» — состояние, которого
    // в новой модели просто нет. Выбрать надо явно: Loading или Empty.
    private val _state = MutableStateFlow<OrdersState>(OrdersState.Loading)
    val state: StateFlow<OrdersState> = _state.asStateFlow()

    fun load() {
        // D1 — механика: поле заменилось проверкой типа
        if (_state.value is OrdersState.Loading) return

        // B2 — стало ПРОЩЕ: гасить error больше не нужно, его негде забыть
        _state.value = OrdersState.Loading

        scope.launch {
            runCatching { repo.orders() }
                // B3 — РЕШЕНИЕ: одна строка расщепилась на две ветки.
                // Пустой ответ раньше молча становился «список из нуля»,
                // теперь это отдельное состояние, и его надо было заметить.
                .onSuccess { items ->
                    _state.value =
                        if (items.isEmpty()) OrdersState.Empty
                        else OrdersState.Content(items)
                }
                // B4 — РЕШЕНИЕ и ПОТЕРЯ: старый список при ошибке больше
                // некуда положить. Либо мы его теряем (как здесь), либо
                // Error обязан носить с собой last known content — и тогда
                // это поле, общее для двух веток, со своей ценой.
                .onFailure { e ->
                    _state.value = OrdersState.Error(e.message ?: "Ошибка загрузки")
                }
        }
    }
}
