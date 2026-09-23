// MVVM в наивной форме: ссылки на View нет, состояние лежит независимыми полями.
//
// Код модельный: показывает форму состояния, а не взят из прода и не собирается
// в этом репозитории. Метки [1]..[4] — шаги, на которые ссылается race.md.

package lab.mvi.mvvm

class ItemsViewModel(
    private val repo: ItemsRepo,
    private val scope: CoroutineScope,
) {
    // Три независимых поля: каждое корректно по отдельности, инвариант между
    // ними ничем не выражен и ничем не охраняется.
    private val _isLoading = MutableStateFlow(false)
    private val _items = MutableStateFlow(emptyList<Item>())
    private val _error = MutableStateFlow<String?>(null)

    val isLoading: StateFlow<Boolean> = _isLoading
    val items: StateFlow<List<Item>> = _items
    val error: StateFlow<String?> = _error

    fun onRetryClicked() {
        scope.launch {
            _isLoading.value = true                        // [1] запись № 1
            _error.value = null
            runCatching { repo.load() }
                .onSuccess {
                    _items.value = it                      // [2] запись № 2
                    _isLoading.value = false
                }
                .onFailure {
                    _error.value = it.message              // [3] запись № 3
                    _isLoading.value = false
                }
        }
    }
}

// Та же VM, но состояние собрано в один объект. Это по-прежнему MVVM: буква
// не меняется от формы поля. Инвариант всё так же не охраняется — copy() может
// позвать любой метод класса, и ответ устаревшего запроса тоже.
class ItemsViewModelSingleState(
    private val repo: ItemsRepo,
    private val scope: CoroutineScope,
) {
    private val _state = MutableStateFlow(ItemsState())
    val state: StateFlow<ItemsState> = _state

    fun onRetryClicked() {
        scope.launch {
            _state.update { it.copy(isLoading = true, error = null) }
            runCatching { repo.load() }
                .onSuccess { loaded ->
                    _state.update { it.copy(isLoading = false, items = loaded) }
                }
                .onFailure { failure ->                    // [4] ответ пришёл вторым
                    _state.update { it.copy(isLoading = false, error = failure.message) }
                }
        }
    }
}

data class ItemsState(
    val isLoading: Boolean = false,
    val items: List<Item> = emptyList(),
    val error: String? = null,
)
