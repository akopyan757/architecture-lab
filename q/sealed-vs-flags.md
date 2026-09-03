# моделирование состояния: sealed vs флаги, невозможные состояния

Issue: [#10](https://github.com/akopyan757/architecture-lab/issues/10) · раздел: [1. Состояние и поток данных](../architecture-map.md)

**Доказано:** код в этом файле — решение (sealed) и альтернатива (флаги) стоят рядом, до/после

## 1. Что такое невозможное состояние

Плоская модель — набор независимых полей — разрешает любую их комбинацию,
даже ту, которая семантически не имеет смысла. Классика: экран одновременно
и грузится, и уже показывает ошибку.

```kotlin
data class ScreenState(
    val isLoading: Boolean = false,
    val error: String? = null,
    val data: List<Item>? = null
)
```

Три поля дают 2 × 2 × 2 (с поправкой на nullable) комбинаций, из которых
осмысленных — четыре: «загрузка», «ошибка», «есть данные», «пусто». Остальные
— `isLoading = true, error = "..."` или `error = "...", data = [...]` — тип
разрешает их построить, хотя ни одна не должна происходить. Компилятор не
поможет: он не знает, что эти поля друг друга исключают, это знание живёт
только в голове того, кто пишет `.copy(...)`.

## 2. До — флаги допускают невалидную комбинацию

```kotlin
fun reduce(state: ScreenState, event: Event): ScreenState = when (event) {
    is Event.Load -> state.copy(isLoading = true, error = null)
    is Event.Loaded -> state.copy(isLoading = false, data = event.items)
    is Event.Failed -> state.copy(isLoading = false, error = event.message)
    // забыли сбросить error при повторном Load — легко пропустить в ревью,
    // компилятор здесь совершенно не помощник
}
```

Если убрать `error = null` из ветки `Load`, экран на секунду покажет и
спиннер, и старую ошибку одновременно — код скомпилируется и пройдёт линт,
баг всплывёт только на глаз.

## 3. После — sealed-иерархия не даёт построить лишнее

```kotlin
sealed interface ScreenState {
    data object Loading : ScreenState
    data class Error(val message: String) : ScreenState
    data class Content(val data: List<Item>) : ScreenState
    data object Empty : ScreenState
}

fun reduce(state: ScreenState, event: Event): ScreenState = when (event) {
    is Event.Load -> ScreenState.Loading
    is Event.Loaded -> if (event.items.isEmpty()) ScreenState.Empty else ScreenState.Content(event.items)
    is Event.Failed -> ScreenState.Error(event.message)
}
```

`isLoading = true` и `error != null` одновременно теперь физически
невозможны: это два разных типа, а не два поля одного. Отрисовка обязана
разобрать `when` исчерпывающе:

```kotlin
@Composable
fun Screen(state: ScreenState) = when (state) {
    is ScreenState.Loading -> Spinner()
    is ScreenState.Error -> ErrorView(state.message)
    is ScreenState.Content -> ContentView(state.data)
    is ScreenState.Empty -> EmptyView()
}
```

Добавить пятую ветку `ScreenState` — компилятор укажет каждое место, которое
её не разбирает (`else` в `when` не добавлен намеренно: без него исчерпанность
проверяется, с ним — нет).

## 4. Компромисс

Sealed-иерархия не бесплатна:

- Данные, общие для нескольких веток (например «список избранного», видимый
  и в `Content`, и в `Error` как последний известный), приходится либо
  дублировать в каждом варианте, либо выносить в отдельное поле снаружи
  иерархии — а это снова две вещи для синхронизации, только уже осознанно.
- Переход между состояниями — это не `copy()` одного поля, а конструирование
  нового варианта; при большом состоянии (много полей `Content`) миграция с
  флагов требует переписать все места, а не одну строку.
- Выигрыш пропорционален числу реально исключающих друг друга комбинаций: если
  состояние по сути одно измерение (просто enum-подобный статус без разных
  полезных нагрузок на ветку) — флаг или enum не хуже sealed и проще.

Разница с флагами не в объёме кода, а в том, где обнаруживается ошибка:
плоская модель ловит забытый `error = null` на глаз или в рантайме, sealed —
на этапе компиляции самим фактом, что неверную комбинацию нельзя написать.
