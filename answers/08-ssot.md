# SSOT: как понять, что он нарушен, и чем это чинится

issue: [#8](https://github.com/akopyan757/architecture-lab/issues/8) · раздел: [1. Состояние и поток данных](../architecture-map.md)

## Что это

SSOT — Single Source of Truth, единый источник истины. Для одного значения на
экране должно быть ровно одно место, которое хранит его актуальное состояние.
Всё остальное — либо ссылка на этот источник, либо вычисление от него.

## Как понять, что нарушен

- Одно и то же по смыслу значение лежит в двух полях state, и хотя бы один
  путь кода обновляет только одно из них.
- Баг в духе «если сначала сделать А, потом Б — на экране неправильное
  значение», хотя по отдельности А и Б работают.
- Чтобы починить баг, добавляют ещё один вызов синхронизации/копирования
  значения, а не меняют единственный источник.
- В `copy()` state-класса регулярно забывают проставить производное поле —
  потому что оно отдельное поле, а не вычисление.

Признак: если на вопрос «а что если эти два значения разойдутся — какое из
них правда?» нет тривиального ответа — SSOT уже нарушен.

## Чем чинится

Свести к одному владельцу значения, остальное — вычислять от него, а не
хранить отдельным полем со своей мутацией.

## Пример: корзина

### До — totalPrice хранится отдельным полем

```kotlin
data class CartState(
    val items: List<CartItem>,
    val totalPrice: Int
)

fun addItem(state: CartState, item: CartItem): CartState {
    val items = state.items + item
    // totalPrice — вторая копия того же знания, что и items;
    // забыли пересчитать здесь — и на экране устаревшая сумма
    return state.copy(items = items)
}

fun removeItem(state: CartState, itemId: String): CartState {
    val items = state.items.filterNot { it.id == itemId }
    val total = items.sumOf { it.price * it.quantity }
    return state.copy(items = items, totalPrice = total) // а здесь не забыли
}
```

`totalPrice` полностью выводим из `items`, но хранится и мутируется отдельно.
Каждый путь обновления `items` обязан не забыть пересчитать `totalPrice` —
`addItem` это правило нарушает.

### После — totalPrice вычисляется от единственного источника

```kotlin
data class CartState(val items: List<CartItem>) {
    val totalPrice: Int get() = items.sumOf { it.price * it.quantity }
}

fun addItem(state: CartState, item: CartItem): CartState =
    state.copy(items = state.items + item)

fun removeItem(state: CartState, itemId: String): CartState =
    state.copy(items = state.items.filterNot { it.id == itemId })
```

Источник истины один — `items`. `totalPrice` невозможно рассинхронизировать,
потому что у него нет собственного состояния для мутации.
