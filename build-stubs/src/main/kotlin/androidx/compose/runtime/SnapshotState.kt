package androidx.compose.runtime

fun <T> mutableStateListOf(vararg elements: T): MutableList<T> = mutableListOf(*elements)
fun <K, V> mutableStateMapOf(vararg pairs: Pair<K, V>): MutableMap<K, V> = mutableMapOf(*pairs)
