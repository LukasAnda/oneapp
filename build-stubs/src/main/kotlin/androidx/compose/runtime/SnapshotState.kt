package androidx.compose.runtime

fun <T> mutableStateListOf(vararg elements: T): MutableList<T> = mutableListOf(*elements)
