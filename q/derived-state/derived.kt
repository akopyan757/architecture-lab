// Стенд к разбору «производные состояния: вычислять vs хранить» (issue #19).
// Три версии одного состояния корзины и один сценарий, на котором
// хранимое производное поле расходится с источником.
//
// A — totalPrice хранится полем, каждый писатель пересчитывает сам.
// B — totalPrice хранится полем, все переходы через один reduce().
// C — totalPrice вычисляется на чтении, второго слота нет.
//
// Сценарий один и тот же: добавить молоко, сервер обновил цены,
// добавить хлеб, восстановить корзину из сохранённого снимка.

data class Item(val id: String, val price: Int, val qty: Int)

fun List<Item>.sum() = sumOf { it.price * it.qty }

// ---------- A: хранимое поле, писатели разрозненные ----------

data class StateA(val items: List<Item>, val totalPrice: Int)

fun addA(s: StateA, i: Item) =
    s.copy(items = s.items + i, totalPrice = s.totalPrice + i.price * i.qty)

fun removeA(s: StateA, id: String): StateA {
    val items = s.items.filterNot { it.id == id }
    return s.copy(items = items, totalPrice = items.sum())
}

// новый писатель; про totalPrice не знает
fun pricesA(s: StateA, prices: Map<String, Int>) =
    s.copy(items = s.items.map { it.copy(price = prices[it.id] ?: it.price) })

// ---------- B: хранимое поле, единый редьюсер ----------

sealed interface Action
data class Add(val item: Item) : Action
data class Remove(val id: String) : Action
data class Prices(val prices: Map<String, Int>) : Action
data class Restore(val saved: List<Item>) : Action

data class StateB(val items: List<Item>, val totalPrice: Int)

private fun StateB.recount() = copy(totalPrice = items.sum())

fun reduceB(s: StateB, a: Action): StateB = when (a) {
    is Add     -> s.copy(items = s.items + a.item).recount()
    is Remove  -> s.copy(items = s.items.filterNot { it.id == a.id }).recount()
    is Prices  -> s.copy(items = s.items.map { it.copy(price = a.prices[it.id] ?: it.price) }).recount()
    is Restore -> s.copy(items = a.saved)        // четвёртая ветка; recount() забыт
}

// ---------- C: вычисление на чтении ----------

data class StateC(val items: List<Item>) {
    val totalPrice: Int get() = items.sum()
}

fun reduceC(s: StateC, a: Action): StateC = when (a) {
    is Add     -> s.copy(items = s.items + a.item)
    is Remove  -> s.copy(items = s.items.filterNot { it.id == a.id })
    is Prices  -> s.copy(items = s.items.map { it.copy(price = a.prices[it.id] ?: it.price) })
    is Restore -> s.copy(items = a.saved)
}

// ---------- сценарий ----------

fun row(step: String, items: List<Item>, stored: Int) {
    val truth = items.sum()
    val mark = if (stored == truth) "" else "   <- расхождение"
    println("%-28s items=%-6d stored=%-6d%s".format(step, truth, stored, mark))
}

fun main() {
    val milk = Item("milk", 100, 1)
    val bread = Item("bread", 50, 1)
    val newPrices = mapOf("milk" to 120)
    val saved = listOf(Item("milk", 120, 2))

    println("A: хранимое поле, писатели разрозненные")
    var a = StateA(emptyList(), 0)
    a = addA(a, milk);            row("1 add milk", a.items, a.totalPrice)
    a = pricesA(a, newPrices);    row("2 server prices milk=120", a.items, a.totalPrice)
    a = addA(a, bread);           row("3 add bread", a.items, a.totalPrice)
    a = removeA(a, "bread");      row("4 remove bread", a.items, a.totalPrice)

    println()
    println("B: хранимое поле, единый reduce()")
    var b = StateB(emptyList(), 0)
    b = reduceB(b, Add(milk));          row("1 add milk", b.items, b.totalPrice)
    b = reduceB(b, Prices(newPrices));  row("2 server prices milk=120", b.items, b.totalPrice)
    b = reduceB(b, Add(bread));         row("3 add bread", b.items, b.totalPrice)
    b = reduceB(b, Restore(saved));     row("4 restore milk x2", b.items, b.totalPrice)
    // и мимо reduce вообще:
    b = b.copy(items = b.items + bread); row("5 copy() мимо reduce", b.items, b.totalPrice)

    println()
    println("C: вычисление на чтении")
    var c = StateC(emptyList())
    c = reduceC(c, Add(milk));          row("1 add milk", c.items, c.totalPrice)
    c = reduceC(c, Prices(newPrices));  row("2 server prices milk=120", c.items, c.totalPrice)
    c = reduceC(c, Add(bread));         row("3 add bread", c.items, c.totalPrice)
    c = reduceC(c, Restore(saved));     row("4 restore milk x2", c.items, c.totalPrice)
    c = c.copy(items = c.items + bread); row("5 copy() мимо reduce", c.items, c.totalPrice)
}
