// Compile-time stub. Plugins compile against this; the real implementation
// is provided by kotlinx-coroutines-core bundled in the host APK.
package kotlinx.coroutines.flow

interface Flow<out T>

interface StateFlow<out T> : Flow<T> {
    val value: T
}

interface MutableStateFlow<T> : StateFlow<T> {
    override var value: T
}

@Suppress("FunctionName")
fun <T> MutableStateFlow(value: T): MutableStateFlow<T> = error("stub")
