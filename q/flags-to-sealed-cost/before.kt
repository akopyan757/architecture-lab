// Экран «Заказы» ДО миграции: состояние — плоский набор флагов.
// Метки A/B/C/D/E — группы точек изменения. Каждая пронумерованная метка
// это одна точка, которую придётся тронуть при переводе на sealed.
//
// Код модельный: он показывает форму, а не взят из прода.

package lab.orders.before

data class Order(val id: String, val title: String)

// ── A. Определение модели ─────────────────────────────────────────────────
// A1
data class OrdersState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val orders: List<Order> = emptyList(),
)

// ── B. Конструирование состояния ──────────────────────────────────────────
class OrdersViewModel(
    private val repo: OrdersRepository,
    private val scope: CoroutineScope,
) {
    // B1 — начальное значение
    private val _state = MutableStateFlow(OrdersState())
    val state: StateFlow<OrdersState> = _state.asStateFlow()

    fun load() {
        // D1 — гвард читает ОДНО поле, а не состояние целиком
        if (_state.value.isLoading) return

        // B2 — «начали грузить»: не забыть погасить error, иначе спиннер поверх ошибки
        _state.update { it.copy(isLoading = true, error = null) }

        scope.launch {
            runCatching { repo.orders() }
                // B3 — успех; пустой список от непустого здесь ничем не отличается
                .onSuccess { items -> _state.update { it.copy(isLoading = false, orders = items) } }
                // B4 — ошибка; старые orders остаются лежать в состоянии
                .onFailure { e -> _state.update { it.copy(isLoading = false, error = e.message) } }
        }
    }
}
