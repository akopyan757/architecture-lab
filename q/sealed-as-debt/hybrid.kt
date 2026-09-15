// Тот же экран после свёртки: общий множитель вынесен за скобки.
// Одиннадцать вариантов из grown.kt превратились в четыре, множество
// состояний при этом не изменилось — те же 8 сочетаний на контенте.
//
// Код модельный: он показывает форму, а не взят из прода.

package lab.orders.hybrid

data class Order(val id: String, val title: String)

sealed interface OrdersState {

    // Слагаемые: взаимно исключающие ответы на «что сейчас на экране»
    data object Loading : OrdersState
    data class Error(val message: String) : OrdersState
    data object Empty : OrdersState

    // Произведение общих и независимых осей + вложенная сумма на той оси,
    // которая действительно исключающая.
    data class Content(
        val orders: List<Order>,
        val isRefreshing: Boolean = false,  // независимая ось → множитель
        val staleAt: Long? = null,          // независимая ось → множитель
        val selection: Selection = Selection.Off,
    ) : OrdersState
}

// «Выключен» и «включён, но ничего не выбрано» одновременно невозможны —
// значит сумма. Заодно исчезает двусмысленность nullable-поля: null против
// emptySet() больше не надо толковать.
sealed interface Selection {
    data object Off : Selection
    data class On(val selectedIds: Set<String>) : Selection
}

// ── Переход, который в grown.kt занимал одиннадцать ветвей ────────────────
fun OrdersState.startRefresh(): OrdersState = when (this) {
    is OrdersState.Content -> copy(isRefreshing = true)
    OrdersState.Empty, is OrdersState.Error -> OrdersState.Loading
    OrdersState.Loading -> this
}

// Включение режима выделения — тоже одна строка, и оно структурно не может
// произойти над Loading или Empty: у них нет поля selection.
fun OrdersState.startSelecting(): OrdersState = when (this) {
    is OrdersState.Content -> copy(selection = Selection.On(emptySet()))
    else -> this
}

// Приход оффлайна, уход оффлайна — так же по строке. В grown.kt каждая из
// этих функций знала про все восемь вариантов контента.

// ── Чтение в UI ───────────────────────────────────────────────────────────
@Composable
fun OrdersScreen(state: OrdersState) {
    when (state) {
        OrdersState.Loading -> Spinner()
        is OrdersState.Error -> ErrorView(state.message)
        OrdersState.Empty -> EmptyView()
        is OrdersState.Content -> {
            // ЦЕНА СВЁРТКИ: эти две строки компилятор не требует.
            // Забыл любую — соберётся, и просто не покажется.
            if (state.isRefreshing) TopProgress()
            if (state.staleAt != null) StaleBanner(state.staleAt)

            // А вот эту ветку — требует, потому что Selection — сумма.
            when (val s = state.selection) {
                Selection.Off -> OrdersList(state.orders)
                is Selection.On -> SelectableOrdersList(state.orders, s.selectedIds)
            }
        }
    }
}

// Добавление четвёртой независимой оси (поиск) здесь стоит одно поле в
// Content и одно место чтения в UI — вместо удвоения числа слагаемых
// с восьми до шестнадцати, как было бы в grown.kt.
