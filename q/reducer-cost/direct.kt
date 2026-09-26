// Карточка товара без reducer'а: состояние меняется прямо в обработчиках.
// Код модельный, в репозитории не собирается.

data class ItemsState(
    val items: List<Item> = emptyList(),
    val isLoading: Boolean = false,
    val error: String? = null,
)

class DirectViewModel(private val repo: Repo) : ViewModel() {

    private val _state = MutableStateFlow(ItemsState())
    val state: StateFlow<ItemsState> = _state.asStateFlow()

    fun onOpen() {
        _state.update { it.copy(isLoading = true, error = null) }   // [1]
        load()
    }

    fun onRetry() {
        _state.update { it.copy(isLoading = true) }                  // [2] error = null забыт
        load()
    }

    private fun load() = viewModelScope.launch {
        runCatching { repo.load() }
            .onSuccess { items ->
                _state.update { it.copy(isLoading = false, items = items) }   // [3]
            }
            .onFailure { e ->
                _state.update { it.copy(isLoading = false, error = e.message.orEmpty()) }  // [4]
            }
    }
}

// Мест записи в состояние: четыре ([1]–[4]), и с каждым новым действием их больше.
//
// Правило «пока грузим, старой ошибки на экране нет» нигде не записано —
// оно существует только как совпадение строк в [1] и [3]. В [2] совпадение
// нарушено, и компилятор об этом молчать будет всегда: он не умеет проверять,
// что во всех copy выставлено одно и то же поле.
//
// Расхождение с reduced.kt показано в replay.md, шаг 3.
