package me.iacn.biliroaming.zhconverter

class Trie<T> {
    private val root = TrieNode<T>(key = ' ')

    fun add(w: String, value: T) {
        if (w.isEmpty()) return
        var p = root
        for (c in w.toCharArray()) {
            p = p.child(c) ?: p.addChild(c)
        }
        p.isLeaf = true
        p.value = value
    }

    fun bestMatch(sen: CharArray, offset: Int, len: Int = sen.size): TrieNode<T>? {
        var ret: TrieNode<T>? = null
        var node: TrieNode<T>? = root
        for (i in offset until len) {
            node = node?.child(sen[i]) ?: break
            if (node.isLeaf) ret = node
        }
        return ret
    }
}
