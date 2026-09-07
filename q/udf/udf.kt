// То же поле с тем же ограничением, но поток однонаправленный.
// Разница не в количестве кода, а в том, что у значения один владелец,
// и вверх идёт факт о пользователе, а не готовое значение.
//
// Код модельный: показывает форму потока, в этом репозитории не собирается.

package lab.udf.oneway

import androidx.compose.foundation.text.BasicTextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.text.TextRange
import androidx.compose.ui.text.input.TextFieldValue
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.update

const val LIMIT = 20

// ── Состояние: текст и курсор принадлежат владельцу вместе ────────────────
// Если хранить только String, курсор остаётся собственностью виджета —
// и двунаправленность возвращается через него (см. two_way.kt, вариант B).
data class InputState(val field: TextFieldValue = TextFieldValue(""))

sealed interface InputEvent {
    // Вверх уходит намерение пользователя целиком, вместе с позицией курсора.
    data class Typed(val raw: TextFieldValue) : InputEvent
}

class InputViewModel {
    private val _state = MutableStateFlow(InputState())
    val state: StateFlow<InputState> = _state

    fun onEvent(event: InputEvent) = when (event) {
        is InputEvent.Typed -> _state.update { reduce(it, event.raw) }
    }

    // Единственное место, где значение меняется. Владелец вправе не принять
    // ввод как есть: он решает и текст, и куда после этого встанет курсор.
    private fun reduce(state: InputState, raw: TextFieldValue): InputState {
        if (raw.text.length <= LIMIT) return state.copy(field = raw)

        val kept = raw.text.take(LIMIT)
        val caret = minOf(raw.selection.start, kept.length)   // курсор не прыгает в конец
        return state.copy(field = TextFieldValue(kept, TextRange(caret)))
    }
}

@Composable
fun InputScreen(vm: InputViewModel) {
    val state by vm.state.collectAsState()
    BasicTextField(
        value = state.field,                                  // вниз: значение
        onValueChange = { vm.onEvent(InputEvent.Typed(it)) },  // вверх: событие
    )
}

// Почему расхождения нет:
// у BasicTextField не остаётся собственного состояния, которое владелец
// не видит, — текст и селекция приходят сверху одним значением. Отклонённый
// ввод не «откатывается»: он просто никогда не становится состоянием.

private fun <T> StateFlow<T>.collectAsState(): androidx.compose.runtime.State<T> = TODO()
