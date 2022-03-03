package me.iacn.biliroaming.utils;

import java.io.Closeable;
import java.lang.reflect.Field;
import java.lang.reflect.Member;

public class DexHelper implements AutoCloseable, Closeable {
    public static final int NO_CLASS_INDEX = -1;
    private final ClassLoader classLoader;
    private final long token;

    public DexHelper(ClassLoader classLoader) {
        this.classLoader = classLoader;
        token = load(classLoader);
    }

    public native long[] findMethodUsingString(
            String str, boolean matchPrefix, long returnType, short parameterCount,
            String parameterShorty, long declaringClass, long[] parameterTypes,
            long[] containsParameterTypes, int[] dexPriority, boolean findFirst);

    public native long[] findMethodInvoking(long methodIndex, long returnType,
                                     short parameterCount, String parameterShorty,
                                     long declaringClass, long[] parameterTypes,
                                     long[] containsParameterTypes,
                                     int[] dexPriority, boolean findFirst);

    public native long[] findMethodInvoked(long methodIndex, long returnType,
                                    short parameterCount, String parameterShorty,
                                    long declaringClass, long[] parameterTypes,
                                    long[] containsParameterTypes,
                                    int[] dexPriority, boolean findFirst);

    public native long[] findMethodSettingField(
            long fieldIndex, long returnType, short parameterCount,
            String parameterShorty, long declaringClass, long[] parameterTypes,
            long[] containsParameterTypes, int[] dexPriority, boolean findFirst);

    public native long[] findMethodGettingField(
            long fieldIndex, long returnType, short parameterCount,
            String parameterShorty, long declaringClass, long[] parameterTypes,
            long[] containsParameterTypes, int[] dexPriority, boolean findFirst);

    public native long[] findField(long type, int[] dexPriority, boolean findFirst);

    public native Member decodeMethodIndex(long methodIndex);

    public native long encodeMethodIndex(Member method);

    public native Field decodeFieldIndex(long fieldIndex);

    public native long encodeFieldIndex(Field field);

    public native long encodeClassIndex(Class<?> clazz);

    public native Class<?> decodeClassIndex(long classIndex);

    public native void createFullCache();

    @Override
    public native void close();

    @Override
    protected void finalize() {
        close();
    }

    private native long load(ClassLoader classLoader);
}

