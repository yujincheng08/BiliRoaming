package me.iacn.biliroaming.zhconverter

class TrieNode<V>(var key: Char) {
    private val children = hashMapOf<Char, TrieNode<V>>()

    var level = 0
    var isLeaf = false
    var value: V? = null

    fun addChild(k: Char) = TrieNode<V>(k).also {
        it.level = level + 1
        children[k] = it
    }

    fun child(k: Char) = children[k]
}
