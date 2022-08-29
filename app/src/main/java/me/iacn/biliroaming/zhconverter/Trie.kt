package me.iacn.biliroaming.zhconverter

class Trie<T> {
    private val root = TrieNode<T>(key = ' ')

    fun add(w: CharArray, value: T) {
        if (w.isEmpty()) {
            return
        }
        var p = root
        for (c in w) {
            val n = p.child(c) ?: p.addChild(c)
            p = n
        }
        p.isLeaf = true
        p.value = value
    }

    fun add(w: String?, value: T) {
        w?.let {
            add(it.toCharArray(), value)
        }
    }

    fun match(sen: CharArray, offset: Int, len: Int): TrieNode<T>? {
        var node: TrieNode<T>? = root
        for (i in 0 until len) {
            node = node?.child(sen[offset + i]) ?: return null
        }
        return node
    }

    @JvmOverloads
    fun bestMatch(sen: CharArray, offset: Int, len: Int = sen.size): TrieNode<T>? {
        var ret: TrieNode<T>? = null
        var node: TrieNode<T>? = root
        for (i in offset until len) {
            node = node?.child(sen[i]) ?: break
            if (node.isLeaf) {
                ret = node
            }
        }
        return ret
    }
}
