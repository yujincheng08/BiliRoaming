package me.iacn.biliroaming.zhconverter

class TrieNode<V>(var key: Char) {
    private val children = hashMapOf<Char, TrieNode<V>>()

    var level = 0
    var isLeaf = false
    var value: V? = null

    fun addChild(k: Char): TrieNode<V> {
        val node = TrieNode<V>(k)
        node.level = level + 1
        children[k] = node
        return node
    }

    fun child(k: Char) = children[k]

    override fun toString(): String {
        return buildString {
            append(key)
            value?.let {
                append(":")
                append(it)
            }
        }
    }
}