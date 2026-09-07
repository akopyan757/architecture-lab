// Двунаправленный поток: поле ввода с ограничением «не длиннее 20 символов».
// Классическая форма — двусторонний биндинг на Android View.
//
// Код модельный: он показывает форму потока, а не взят из прода и не собирается
// в этом репозитории. Метки [1]..[5] — шаги, на которые ссылается trace.md.

package lab.udf.twoway

import android.widget.EditText

// ── Наблюдаемое значение ──────────────────────────────────────────────────
class ObservableField<T>(private var value: T) {
    private val listeners = mutableListOf<(T) -> Unit>()
    fun get(): T = value
    fun set(new: T) {                       // [2] запись со стороны View
        value = new
        listeners.forEach { it(new) }       // [3] связь вниз срабатывает сразу
    }
    fun onChange(l: (T) -> Unit) { listeners += l }
}

const val LIMIT = 20

class TwoWayScreen(private val editText: EditText) {

    val text = ObservableField("")

    // Правило владельца: строка не длиннее LIMIT.
    fun setText(value: String) = text.set(value.take(LIMIT))

    // ── Вариант A: биндинг без стража — эхо не завершается ────────────────
    fun bindNaive() {
        text.onChange { editText.setText(it) }              // вниз
        editText.doAfterTextChanged { setText(it) }         // вверх: не событие, а значение
        // Печать одной буквы: [1] ввод → [2] set → [3] onChange → setText →
        // снова doAfterTextChanged → [2] ... цикл не имеет условия выхода.
    }

    // ── Вариант B: страж по равенству — цикл гаснет, курсор уезжает ───────
    fun bindGuardedByEquality() {
        text.onChange {
            if (editText.text.toString() != it) editText.setText(it)  // [4]
        }
        editText.doAfterTextChanged { setText(it) }
        // Цикл конечен: второй проход сравнивает равные строки и не пишет.
        // Но setText на шаге [4] сбрасывает позицию курсора в конец строки:
        // View не знает, что владелец лишь обрезал хвост, для неё это новая строка.
    }

    // ── Вариант C: страж по флагу — расхождение остаётся навсегда ─────────
    private var isUpdating = false

    fun bindGuardedByFlag() {
        text.onChange {
            if (isUpdating) return@onChange   // [5] связь вниз подавлена целиком
            isUpdating = true
            editText.setText(it)
            isUpdating = false
        }
        editText.doAfterTextChanged {
            isUpdating = true
            setText(it)
            isUpdating = false
        }
        // Здесь на шаге [5] обновление сверху не доедет до View вообще.
        // Модель хранит 20 символов, на экране остаётся 21. Ни одна сторона
        // не считает себя неправой — обе уже применили своё изменение.
    }
}

// Заглушка расширения из androidx.core, чтобы файл читался автономно.
private fun EditText.doAfterTextChanged(action: (String) -> Unit): Unit = TODO()
