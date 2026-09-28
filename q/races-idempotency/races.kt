// Сценарий расхождения к разбору «гонки при параллельных обновлениях,
// идемпотентность» (issue #17).
//
// Пять прогонов, каждый печатает то, что утверждает разбор:
//   A — потерянное обновление на read-modify-write
//   B — CAS (update {}): результат сходится, лямбда повторяется
//   C — Mutex: результат сходится, тело выполняется ровно N раз
//   D — недостижимая комбинация полей: ничья запись не стёрта, инвариант нарушен
//   E — повтор намерения: at-least-once, ключ операции, признак «в полёте»
//
// Запуск: bash run.sh (kotlinc + kotlinx-coroutines-core)

import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.util.concurrent.atomic.AtomicInteger

private const val N = 1000

private fun head(title: String) = println("\n== $title ==")

// --- A, B, C: счётчик --------------------------------------------------------

private data class Counter(val count: Int = 0)

private suspend fun lostUpdate(): Pair<Int, Int> {
    val state = MutableStateFlow(Counter())
    val bodyRuns = AtomicInteger()
    coroutineScope {
        repeat(N) {
            launch(Dispatchers.Default) {
                bodyRuns.incrementAndGet()
                val snapshot = state.value              // чтение
                state.value = snapshot.copy(count = snapshot.count + 1) // запись
            }
        }
    }
    return state.value.count to bodyRuns.get()
}

private suspend fun cas(): Pair<Int, Int> {
    val state = MutableStateFlow(Counter())
    val bodyRuns = AtomicInteger()
    coroutineScope {
        repeat(N) {
            launch(Dispatchers.Default) {
                state.update { current ->
                    bodyRuns.incrementAndGet()
                    current.copy(count = current.count + 1)
                }
            }
        }
    }
    return state.value.count to bodyRuns.get()
}

private suspend fun serialized(): Pair<Int, Int> {
    val state = MutableStateFlow(Counter())
    val mutex = Mutex()
    val bodyRuns = AtomicInteger()
    coroutineScope {
        repeat(N) {
            launch(Dispatchers.Default) {
                mutex.withLock {
                    bodyRuns.incrementAndGet()
                    val snapshot = state.value
                    state.value = snapshot.copy(count = snapshot.count + 1)
                }
            }
        }
    }
    return state.value.count to bodyRuns.get()
}

// --- D: недостижимая комбинация полей ----------------------------------------

private data class Session(val isLoggedIn: Boolean, val user: String?)

private fun unreachableCombination() {
    head("D. Недостижимая комбинация полей")
    println("инвариант: user != null  <=>  isLoggedIn == true")

    // Обновление профиля прочитало состояние и ушло в сеть.
    val state = MutableStateFlow(Session(isLoggedIn = true, user = "старый профиль"))
    val decidedOn = state.value

    // Пока запрос в полёте — пользователь вышел.
    state.update { it.copy(isLoggedIn = false, user = null) }
    println("после logout:                 ${state.value}")

    // Вариант 1: ответ пишется из своего снимка.
    val blind = MutableStateFlow(state.value)
    blind.value = decidedOn.copy(user = "Иван")
    println("запись из снимка:             ${blind.value}   <- logout стёрт (lost update)")

    // Вариант 2: та же запись через CAS — свежее чтение перед записью.
    val fresh = MutableStateFlow(state.value)
    fresh.update { it.copy(user = "Иван") }
    println("CAS без проверки:             ${fresh.value}  <- logout цел, инвариант нарушен")

    // Вариант 3: проверка внутри лямбды — там, где она защищена.
    val guarded = MutableStateFlow(state.value)
    guarded.update { if (it.isLoggedIn) it.copy(user = "Иван") else it }
    println("CAS с проверкой внутри:       ${guarded.value}  <- ответ отброшен, инвариант цел")
}

// --- E: повтор намерения -----------------------------------------------------

/** Сервер с доставкой at-least-once: ответ на первый вызов теряется по таймауту. */
private class OrderServer {
    private val byKey = mutableMapOf<String, String>()
    private var seq = 0
    val created = mutableListOf<String>()

    /** Без ключа: каждый вызов — новый заказ. */
    fun create(): String {
        val id = "order-${++seq}"
        created += id
        return id
    }

    /** С ключом: повтор возвращает уже созданный заказ. */
    fun create(opKey: String): String = byKey.getOrPut(opKey) {
        val id = "order-${++seq}"
        created += id
        id
    }
}

private data class Checkout(val inFlight: Boolean = false, val opKey: String? = null, val orderId: String? = null)

private fun retryWithoutKey() {
    val server = OrderServer()
    server.create()                 // ответ не дошёл — таймаут
    server.create()                 // ретрай по кнопке
    println("без ключа, 1 намерение + ретрай: заказов ${server.created.size} ${server.created}")
}

private fun retryWithKey() {
    val server = OrderServer()
    val state = MutableStateFlow(Checkout())

    // Первый тап: захват «в полёте» и ключ, созданный клиентом.
    val taken = state.getAndUpdate { if (it.inFlight) it else it.copy(inFlight = true, opKey = "op-7f3a") }
    check(!taken.inFlight)
    val key = state.value.opKey!!

    server.create(key)              // ответ не дошёл — таймаут
    val id = server.create(key)     // ретрай по кнопке, ключ взят из state
    state.update { it.copy(inFlight = false, orderId = id) }

    println("с ключом,  1 намерение + ретрай: заказов ${server.created.size} ${server.created}")
    println("состояние экрана:                ${state.value}")
}

private fun doubleTap() {
    val state = MutableStateFlow(Checkout())
    var started = 0
    repeat(2) {
        val before = state.getAndUpdate { if (it.inFlight) it else it.copy(inFlight = true, opKey = "op-7f3a") }
        if (!before.inFlight) started++
    }
    println("двойной тап, признак «в полёте»: запущено операций $started")
}

// --- main --------------------------------------------------------------------

fun main() = runBlocking {
    println("N = $N; процессоров: ${Runtime.getRuntime().availableProcessors()}")

    head("A. Потерянное обновление: state.value = state.value.copy(...)")
    repeat(3) {
        val (result, runs) = lostUpdate()
        println("итог $result из $N (тело выполнено $runs раз) — потеряно ${N - result}")
    }

    head("B. CAS: state.update { ... }")
    repeat(3) {
        val (result, runs) = cas()
        println("итог $result из $N (лямбда выполнена $runs раз) — повторов ${runs - N}")
    }

    head("C. Сериализация: mutex.withLock { ... }")
    repeat(3) {
        val (result, runs) = serialized()
        println("итог $result из $N (тело выполнено $runs раз) — повторов ${runs - N}")
    }

    unreachableCombination()

    head("E. Повтор намерения")
    retryWithoutKey()
    retryWithKey()
    doubleTap()
}
