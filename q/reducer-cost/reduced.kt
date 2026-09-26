// Тот же экран через reducer. Код модельный, в репозитории не собирается.

data class ItemsState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

sealed interface Action {
    data object Started : Action                        // открыли экран
    data object Retried : Action                        // нажали «Повторить»
    data class Loaded(val items: List<Item>) : Action
    data class Failed(val message: String) : Action
}

// Первая половина: что стало с данными. Чистая функция, тестируется как есть.
fun reduce(state: ItemsState, action: Action): ItemsState = when (action) {
    Action.Started,
    Action.Retried   -> state.copy(isLoading = true, error = null)   // [1] один переход на оба действия
    is Action.Loaded -> state.copy(isLoading = false, items = action.items, error = null)
    is Action.Failed -> state.copy(isLoading = false, error = action.message)
}

class ReducedViewModel(private val repo: Repo) : ViewModel() {

    private val _state = MutableStateFlow(ItemsState())
    val state: StateFlow<ItemsState> = _state.asStateFlow()

    fun dispatch(action: Action) {
        _state.update { reduce(it, action) }   // единственное место записи

        // Вторая половина: какой эффект запустить. Разбирает тот же Action.
        when (action) {                                              // [2]
            Action.Started,
            Action.Retried   -> load()
            is Action.Loaded,
            is Action.Failed -> Unit           // эффекта нет — сказано явно, не через else
        }
    }

    private fun load() = viewModelScope.launch {
        runCatching { repo.load() }
            .onSuccess { dispatch(Action.Loaded(it)) }
            .onFailure { dispatch(Action.Failed(it.message.orEmpty())) }
    }
}

// Что изменилось по сравнению с direct.kt:
//
// 1. Место записи одно. Правило «пока грузим, старой ошибки нет» записано
//    в [1] и существует как одна строка кода, а не как совпадение четырёх.
// 2. Action разбирается дважды — в reduce и в [2]. Знание о том, что делает
//    Retried, размазано по двум местам: reduce знает про флаг, [2] знает про
//    запрос. Это цена чистоты, а не недоделка.
// 3. В [2] сознательно нет else. С `else -> Unit` добавление нового Action
//    молча не запустило бы эффект — вечный спиннер. Без else такой код просто
//    не соберётся. Это единственное место, где reducer даёт то, чего в
//    direct.kt не добиться никакой аккуратностью.
//
// Чего reducer здесь НЕ даёт: ошибку из пункта 3 тест на reduce не поймает.
// reduce в ней правильный, сломан обработчик эффектов — а он тестируется
// уже с корутинами и подменой репозитория, по обычным расценкам.
