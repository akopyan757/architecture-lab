// MVI: все переходы состояния — ветки одной чистой функции.
//
// Код модельный: показывает форму перехода, а не взят из прода и не собирается
// в этом репозитории. Метки [1]..[4] — шаги, на которые ссылается race.md.

package lab.mvi.mvi

// Ввод пользователя.
sealed interface Intent {
    object Retry : Intent
}

// Всё, что вообще может изменить состояние, включая ответы наружного мира.
sealed interface Msg {
    object Started : Msg
    data class Loaded(val requestId: Long, val items: List<Item>) : Msg
    data class Failed(val requestId: Long, val message: String) : Msg
}

data class ItemsState(
    val isLoading: Boolean = false,
    val items: List<Item> = emptyList(),
    val error: String? = null,
    // Признак актуальности: ответ старше текущего запроса игнорируется.
    val requestId: Long = 0,
)

// Чистая функция: тестируется без корутин, моков и диспетчеров.
fun reduce(state: ItemsState, msg: Msg): ItemsState = when (msg) {
    Msg.Started -> state.copy(                               // [1]
        isLoading = true,
        error = null,
        requestId = state.requestId + 1,
    )
    is Msg.Loaded ->
        if (msg.requestId != state.requestId) state           // [3] устаревший ответ
        else state.copy(isLoading = false, items = msg.items, error = null)
    is Msg.Failed ->
        if (msg.requestId != state.requestId) state           // [4] устаревший ответ
        else state.copy(isLoading = false, error = msg.message, items = emptyList())
}

class ItemsViewModel(
    private val repo: ItemsRepo,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ItemsState())
    val state: StateFlow<ItemsState> = _state

    fun onIntent(intent: Intent) = when (intent) {
        Intent.Retry -> retry()
    }

    // Запуск работы живёт снаружи reduce: reduce обязан остаться чистым, и это
    // не стилистика, а условие пунктов 1 и 3 — переход, зависящий от времени
    // ответа, нельзя ни перечислить, ни проверить.
    private fun retry() {
        val id = apply(Msg.Started).requestId                 // [2] номер запроса
        scope.launch {
            runCatching { repo.load() }
                .onSuccess { apply(Msg.Loaded(id, it)) }
                .onFailure { apply(Msg.Failed(id, it.message.orEmpty())) }
        }
    }

    private fun apply(msg: Msg): ItemsState =
        _state.updateAndGet { reduce(it, msg) }
}
