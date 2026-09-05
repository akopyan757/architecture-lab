// Ловушка: «у нас уже sealed, мигрировать нечего».
// Sealed по одной оси не отменяет флагов по всем остальным.

package lab.orders.contract

sealed interface Resource<out T> {
    data object Loading : Resource<Nothing>
    data class Error(val message: String) : Resource<Nothing>
    data class Success<T>(val data: T) : Resource<T>
}

// Ось «загрузка / успех / ошибка» закрыта. А внутри Success — снова плоско:
data class OrdersContent(
    val orders: List<Order>,
    val isRefreshing: Boolean = false,   // ← флаг вернулся
    val isSubmitting: Boolean = false,   // ← и ещё один
    val submitError: String? = null,     // ← и ошибка второго рода
)

// isRefreshing = true вместе с isSubmitting = true — снова комбинация,
// которой не должно быть, и снова никто её не запрещает.
//
// Настоящий вопрос на таком проекте не «флаги → sealed», а:
// что из внутренних флагов ПОДНИМАЕТСЯ до вариантов Resource,
// а что остаётся полем внутри Success.
//
// Это решение принимается ОДИН РАЗ на проект — и оплачивает его
// первый мигрирующий экран, а не тот, кому оно понадобилось.
