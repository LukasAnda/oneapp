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
