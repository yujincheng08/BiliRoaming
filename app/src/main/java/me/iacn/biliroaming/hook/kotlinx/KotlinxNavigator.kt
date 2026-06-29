package me.iacn.biliroaming.hook.kotlinx

import me.iacn.biliroaming.utils.callMethodOrNull

/**
 * 基于 kotlinx.serialization descriptor 字段名导航反序列化后的对象。
 * 不依赖混淆字段名，使用稳定的 descriptor 元素名（如 "data", "tab", "id"）定位。
 *
 * 用法：
 * ```
 * val root = KotlinxNavigator(result, deserializer)
 * val tabData = root.child("data")            // 子对象
 * val bottoms = tabData.get("tab") as List<*> // 列表字段
 * val itemDesc = tabData.listElementDescriptor("tab")
 * bottoms.forEach { item ->
 *     val nav = KotlinxNavigator.of(item, itemDesc)
 *     val id = nav.get("id")
 * }
 * ```
 */
class KotlinxNavigator private constructor(
    val obj: Any,
    private val serializer: Any?,
    descriptorOverride: Any?,
) {
    /** 当前对象的 descriptor */
    val descriptor: Any = descriptorOverride
        ?: serializer?.callMethodOrNull("getDescriptor")
        ?: throw IllegalArgumentException("serializer must not be null")

    /** 父类属性数 = descriptor 总元素数 - 当前类声明的字段数 */
    private val parentOffset: Int by lazy {
        val total = descriptor.callMethodOrNull("getElementsCount") as Int
        total - obj.javaClass.declaredFields.size
    }

    // -- 基础字段访问 --

    /** 通过 descriptor 元素名获取对应的 Java [Field] */
    fun field(name: String): java.lang.reflect.Field {
        val idx = descriptor.callMethodOrNull("getElementIndex", name) as Int
        return obj.javaClass.declaredFields[idx - parentOffset].also { it.isAccessible = true }
    }

    fun get(name: String): Any? = field(name).get(obj)

    fun set(name: String, value: Any?) {
        field(name).set(obj, value)
    }

    fun setInt(name: String, value: Int) {
        field(name).setInt(obj, value)
    }

    // -- 导航 --

    /**
     * 获取子对象（非集合类型字段）的 navigator。
     * 通过 [serializer.childSerializers()] 链获取子序列化器并自动展开 NullableSerializer。
     */
    fun child(name: String): KotlinxNavigator? {
        val childObj = get(name) ?: return null
        val childSer = resolveChildSerializer(name) ?: return null
        return KotlinxNavigator(childObj, childSer)
    }

    /**
     * 获取 List 类型字段的元素 descriptor。
     * 通过 ArrayListSerializer → CollectionLikeSerializer.elementSerializer 链解析。
     */
    fun listElementDescriptor(name: String): Any? {
        val listSer = resolveChildSerializer(name) ?: return null
        val elemSer = unwrapListElement(listSer) ?: return null
        return elemSer.callMethodOrNull("getDescriptor")
    }

    // -- 内部 --

    /** 从 parent serializer 的 childSerializers 中获取指定字段的子序列化器（已展开 Nullable） */
    private fun resolveChildSerializer(name: String): Any? {
        if (serializer == null) return null
        val idx = descriptor.callMethodOrNull("getElementIndex", name) as Int
        val children = serializer.callMethodOrNull("childSerializers") as? Array<*>
        return children?.get(idx)?.let { unwrapNullable(it) }
    }

    companion object {
        /** 从 serializer 创建（完整导航能力） */
        operator fun invoke(obj: Any, serializer: Any): KotlinxNavigator =
            KotlinxNavigator(obj, serializer, null)

        /**
         * 创建仅支持 get/set/field 的 navigator（无 serializer，不能导航子对象）。
         * 适用于 List 元素的 descriptor。
         */
        fun of(obj: Any, descriptor: Any): KotlinxNavigator =
            KotlinxNavigator(obj, null, descriptor)

        /**
         * 展开 NullableSerializer 包装，获取内部原始序列化器。
         * 依赖 kotlinx.serialization 库类的 "serializer" 私有字段。
         */
        fun unwrapNullable(ser: Any): Any =
            findField(ser.javaClass, "serializer")?.also { it.isAccessible = true }?.get(ser)
                ?: ser

        /**
         * 从列表序列化器（ArrayListSerializer 等）中提取元素序列化器。
         * 依赖 CollectionLikeSerializer 的 "elementSerializer" 私有字段。
         */
        fun unwrapListElement(ser: Any): Any? =
            findField(ser.javaClass, "elementSerializer")?.also { it.isAccessible = true }?.get(ser)

        /** 沿类层次向上查找字段，直到找到或到达 Object */
        private fun findField(cls: Class<*>, name: String): java.lang.reflect.Field? {
            var current: Class<*>? = cls
            while (current != null) {
                try { return current.getDeclaredField(name) }
                catch (_: NoSuchFieldException) { current = current.superclass }
            }
            return null
        }
    }
}
