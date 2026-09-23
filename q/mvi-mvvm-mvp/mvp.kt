// MVP: презентер держит ссылку на View и командует ей.
//
// Код модельный: показывает форму связи, а не взят из прода и не собирается в
// этом репозитории. Метки [1]..[3] — шаги, на которые ссылается race.md.

package lab.mvi.mvp

// ── Граница между презентером и экраном — интерфейс команд ────────────────
interface ItemsView {
    fun showLoading()
    fun showItems(items: List<Item>)
    fun showError(message: String)
}

class ItemsPresenter(
    private val view: ItemsView,
    private val repo: ItemsRepo,
    private val scope: CoroutineScope,
) {
    // Ввод — вызов метода презентера; ответ — команда обратно во View.
    fun onRetryClicked() {
        scope.launch {
            view.showLoading()                                  // [1] push вниз
            runCatching { repo.load() }
                .onSuccess { view.showItems(it) }               // [2]
                .onFailure { view.showError(it.message.orEmpty()) }  // [3]
        }
    }
}

// Актуального состояния в этом файле нет ни одного поля: что сейчас на экране,
// знают только виджеты. Отсюда два следствия, которые видно прямо здесь:
//
// 1. Пересоздали View (поворот, возврат из бэкстека) — презентеру нечего
//    проиграть заново: он хранил не значение, а факт отправленной команды.
// 2. Декларативному UI такой презентер нечем помочь: composable-функция
//    вызывается заново при каждой рекомпозиции и рисует экран из аргументов,
//    а не из того, что ей однажды сказали.
