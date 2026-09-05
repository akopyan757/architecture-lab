// Тот же экран «Заказы», но на проекте, где есть ОБЩИЙ КОНТРАКТ состояния.
// Здесь миграция одного экрана невозможна в принципе — и это видно на коде.

package lab.orders.contract

// ── Общий тип на весь апп: им параметризованы все 12 экранов ──────────────
data class UiState<T>(
    val isLoading: Boolean = false,
    val error: String? = null,
    val data: T? = null,
)

// ── Общий рендер: один спиннер, одна обработка ошибок на весь апп ─────────
@Composable
fun <T> StateHost(
    state: UiState<T>,
    onRetry: () -> Unit,
    content: @Composable (T) -> Unit,
) {
    when {
        state.isLoading -> Spinner()
        state.error != null -> ErrorView(state.error, onRetry)
        state.data != null -> content(state.data)
        else -> EmptyView()
    }
}

// ── Экран «Заказы» — три строки, вся механика в хосте ─────────────────────
@Composable
fun OrdersScreen(state: UiState<List<Order>>, onRetry: () -> Unit) {
    StateHost(state, onRetry) { orders -> OrdersList(orders) }
}

// Чтобы перевести ОДНИ «Заказы» на sealed, нужно одно из трёх:
//
//  1. Поменять UiState<T> и StateHost — то есть мигрировать все 12 экранов
//     одним коммитом. 12 × перечень из п.1.1.
//
//  2. Вывести «Заказы» из-под StateHost и дать им свой when со своими
//     Spinner/ErrorView. Дёшево сейчас (+2 точки), но обработка ошибок
//     раздваивается: правку в StateHost теперь надо помнить продублировать.
//
//  3. Завести второй хост рядом со старым — SealedStateHost — и переводить
//     экраны по одному. Это мост на уровне контракта, см. bridge.kt.
