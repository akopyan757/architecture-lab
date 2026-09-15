// Тот же экран «Заказы», что в q/flags-to-sealed-cost/ — через три квартала
// после миграции на sealed. К исходной оси «что на экране» добавились три
// продуктовых требования, и каждое добавлялось так, как подсказывает форма:
// новым вариантом иерархии.
//
// Код модельный: он показывает форму, а не взят из прода.

package lab.orders.grown

data class Order(val id: String, val title: String)

// ── Что просил продукт, по одному требованию за раз ───────────────────────
// R1. Pull-to-refresh: обновляем, но список уже показан.
// R2. Оффлайн: данные из кэша, надо показать плашку «данные от такого-то».
// R3. Режим выделения: чекбоксы над списком, работает только поверх контента.
//
// Ни одно из трёх не исключает другие: можно обновлять оффлайновый список
// в режиме выделения. Именно поэтому они попали в форму так, как попали.

sealed interface OrdersState {

    // ── Ось «что на экране»: варианты действительно исключающие ───────────
    data object Loading : OrdersState
    data class Error(val message: String) : OrdersState
    data object Empty : OrdersState

    // ── Дальше — контент. Восемь вариантов на три оси ──────────────────────
    // Каждый из них — это одна и та же строчка «у нас есть список заказов»
    // плюс отметка, в каком из восьми сочетаний мы находимся.

    data class Content(
        val orders: List<Order>,
    ) : OrdersState

    data class ContentRefreshing(
        val orders: List<Order>,
    ) : OrdersState

    data class ContentOffline(
        val orders: List<Order>,
        val staleAt: Long,
    ) : OrdersState

    data class ContentOfflineRefreshing(
        val orders: List<Order>,
        val staleAt: Long,
    ) : OrdersState

    data class ContentSelecting(
        val orders: List<Order>,
        val selectedIds: Set<String>,
    ) : OrdersState

    data class ContentSelectingRefreshing(
        val orders: List<Order>,
        val selectedIds: Set<String>,
    ) : OrdersState

    data class ContentSelectingOffline(
        val orders: List<Order>,
        val selectedIds: Set<String>,
        val staleAt: Long,
    ) : OrdersState

    data class ContentSelectingOfflineRefreshing(
        val orders: List<Order>,
        val selectedIds: Set<String>,
        val staleAt: Long,
    ) : OrdersState
}

// ── Во что превращается простейший переход ────────────────────────────────
// «Пользователь дёрнул pull-to-refresh» — это не изменение одного поля, а
// переход между вариантами. Отображение приходится выписывать руками, и оно
// обязано сохранить всё, что несла прежняя ветка.
fun OrdersState.startRefresh(): OrdersState = when (this) {
    is OrdersState.Content ->
        OrdersState.ContentRefreshing(orders)
    is OrdersState.ContentOffline ->
        OrdersState.ContentOfflineRefreshing(orders, staleAt)
    is OrdersState.ContentSelecting ->
        OrdersState.ContentSelectingRefreshing(orders, selectedIds)
    is OrdersState.ContentSelectingOffline ->
        OrdersState.ContentSelectingOfflineRefreshing(orders, selectedIds, staleAt)

    // Уже обновляемся — переход в себя же.
    is OrdersState.ContentRefreshing,
    is OrdersState.ContentOfflineRefreshing,
    is OrdersState.ContentSelectingRefreshing,
    is OrdersState.ContentSelectingOfflineRefreshing -> this

    // Контента нет — обновлять нечего, это обычная загрузка.
    OrdersState.Loading -> this
    OrdersState.Empty, is OrdersState.Error -> OrdersState.Loading
}

// Компилятор здесь помогает ровно в одном: он не даст забыть ветку.
// Но он не скажет, что `ContentSelectingRefreshing(orders, selectedIds)`
// потерял `staleAt`, если её туда забыли дописать — типы совпадают, а
// поведение «плашка оффлайна мигает на время обновления» уезжает в баг.

// ── И то же самое для второй оси ──────────────────────────────────────────
// Включение режима выделения — ещё одна такая же функция на восемь ветвей.
// Выключение — третья. Приход оффлайна — четвёртая.
// Число таких функций равно числу осей, и каждая знает про все варианты.

// ── Обычная попытка это остановить ────────────────────────────────────────
// На этом месте в живом коде появляется вот такой вариант: оси возвращают
// в поля, но уже внутри ветки sealed.
sealed interface OrdersStateV2 {
    data object Loading : OrdersStateV2
    data class Error(val message: String) : OrdersStateV2
    data object Empty : OrdersStateV2

    data class Content(
        val orders: List<Order>,
        val isRefreshing: Boolean = false,   // ось R1 вернулась флагом
        val staleAt: Long? = null,           // ось R2 вернулась nullable-полем
        val selectedIds: Set<String>? = null, // ось R3 — тем же способом
    ) : OrdersStateV2
}
// Направление верное — оси вернулись множителями, — но сделано небрежно:
// `selectedIds = null` и `selectedIds = emptySet()` означают разное («режим
// выключен» и «включён, ничего не выбрано»), и различие живёт только в
// голове. Как это чинится — в hybrid.kt.
