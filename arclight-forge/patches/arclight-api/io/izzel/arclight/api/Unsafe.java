package io.izzel.arclight.api;

import java.lang.StackWalker.Option;
import java.lang.StackWalker.StackFrame;
import java.lang.invoke.MethodHandle;
import java.lang.invoke.MethodHandles;
import java.lang.invoke.MethodHandles.Lookup;
import java.lang.invoke.MethodType;
import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.security.ProtectionDomain;
import java.util.Arrays;
import java.util.Objects;
import org.objectweb.asm.ClassReader;

public class Unsafe {

    private static final sun.misc.Unsafe unsafe;
    private static final Lookup lookup;
    private static final MethodHandle defineClass;
    private static final MethodHandle H_DEF_CLASS;
    private static final Unsafe.CallerClass INSTANCE;
    // Java 25: ensureClassInitialized/shouldBeInitialized were removed from sun.misc.Unsafe;
    // they are sourced from jdk.internal.misc.Unsafe via the trusted lookup instead.
    private static final MethodHandle ensureInitMH;
    private static final MethodHandle shouldInitMH;

    public static <T> T getStatic(Class<?> cl, String name) {
        try {
            ensureClassInitialized(cl);
            Field field = cl.getDeclaredField(name);
            Object materialByNameBase = staticFieldBase(field);
            long materialByNameOffset = staticFieldOffset(field);

            return (T) getObject(materialByNameBase, materialByNameOffset);
        } catch (Exception exception) {
            return null;
        }
    }

    public static Lookup lookup() {
        return Unsafe.lookup;
    }

    public static sun.misc.Unsafe getUnsafe() {
        return Unsafe.unsafe;
    }

    public static int getInt(Object o, long l) {
        return Unsafe.unsafe.getInt(o, l);
    }

    public static void putInt(Object o, long l, int i) {
        Unsafe.unsafe.putInt(o, l, i);
    }

    public static Object getObject(Object o, long l) {
        return Unsafe.unsafe.getObject(o, l);
    }

    public static void putObject(Object o, long l, Object o1) {
        Unsafe.unsafe.putObject(o, l, o1);
    }

    public static boolean getBoolean(Object o, long l) {
        return Unsafe.unsafe.getBoolean(o, l);
    }

    public static void putBoolean(Object o, long l, boolean b) {
        Unsafe.unsafe.putBoolean(o, l, b);
    }

    public static byte getByte(Object o, long l) {
        return Unsafe.unsafe.getByte(o, l);
    }

    public static void putByte(Object o, long l, byte b) {
        Unsafe.unsafe.putByte(o, l, b);
    }

    public static short getShort(Object o, long l) {
        return Unsafe.unsafe.getShort(o, l);
    }

    public static void putShort(Object o, long l, short i) {
        Unsafe.unsafe.putShort(o, l, i);
    }

    public static char getChar(Object o, long l) {
        return Unsafe.unsafe.getChar(o, l);
    }

    public static void putChar(Object o, long l, char c) {
        Unsafe.unsafe.putChar(o, l, c);
    }

    public static long getLong(Object o, long l) {
        return Unsafe.unsafe.getLong(o, l);
    }

    public static void putLong(Object o, long l, long l1) {
        Unsafe.unsafe.putLong(o, l, l1);
    }

    public static float getFloat(Object o, long l) {
        return Unsafe.unsafe.getFloat(o, l);
    }

    public static void putFloat(Object o, long l, float v) {
        Unsafe.unsafe.putFloat(o, l, v);
    }

    public static double getDouble(Object o, long l) {
        return Unsafe.unsafe.getDouble(o, l);
    }

    public static void putDouble(Object o, long l, double v) {
        Unsafe.unsafe.putDouble(o, l, v);
    }

    public static byte getByte(long l) {
        return Unsafe.unsafe.getByte(l);
    }

    public static void putByte(long l, byte b) {
        Unsafe.unsafe.putByte(l, b);
    }

    public static short getShort(long l) {
        return Unsafe.unsafe.getShort(l);
    }

    public static void putShort(long l, short i) {
        Unsafe.unsafe.putShort(l, i);
    }

    public static char getChar(long l) {
        return Unsafe.unsafe.getChar(l);
    }

    public static void putChar(long l, char c) {
        Unsafe.unsafe.putChar(l, c);
    }

    public static int getInt(long l) {
        return Unsafe.unsafe.getInt(l);
    }

    public static void putInt(long l, int i) {
        Unsafe.unsafe.putInt(l, i);
    }

    public static long getLong(long l) {
        return Unsafe.unsafe.getLong(l);
    }

    public static void putLong(long l, long l1) {
        Unsafe.unsafe.putLong(l, l1);
    }

    public static float getFloat(long l) {
        return Unsafe.unsafe.getFloat(l);
    }

    public static void putFloat(long l, float v) {
        Unsafe.unsafe.putFloat(l, v);
    }

    public static double getDouble(long l) {
        return Unsafe.unsafe.getDouble(l);
    }

    public static void putDouble(long l, double v) {
        Unsafe.unsafe.putDouble(l, v);
    }

    public static long getAddress(long l) {
        return Unsafe.unsafe.getAddress(l);
    }

    public static void putAddress(long l, long l1) {
        Unsafe.unsafe.putAddress(l, l1);
    }

    public static long allocateMemory(long l) {
        return Unsafe.unsafe.allocateMemory(l);
    }

    public static long reallocateMemory(long l, long l1) {
        return Unsafe.unsafe.reallocateMemory(l, l1);
    }

    public static void setMemory(Object o, long l, long l1, byte b) {
        Unsafe.unsafe.setMemory(o, l, l1, b);
    }

    public static void setMemory(long l, long l1, byte b) {
        Unsafe.unsafe.setMemory(l, l1, b);
    }

    public static void copyMemory(Object o, long l, Object o1, long l1, long l2) {
        Unsafe.unsafe.copyMemory(o, l, o1, l1, l2);
    }

    public static void copyMemory(long l, long l1, long l2) {
        Unsafe.unsafe.copyMemory(l, l1, l2);
    }

    public static void freeMemory(long l) {
        Unsafe.unsafe.freeMemory(l);
    }

    public static long staticFieldOffset(Field field) {
        return Unsafe.unsafe.staticFieldOffset(field);
    }

    public static long objectFieldOffset(Field field) {
        return Unsafe.unsafe.objectFieldOffset(field);
    }

    public static Object staticFieldBase(Field field) {
        return Unsafe.unsafe.staticFieldBase(field);
    }

    public static boolean shouldBeInitialized(Class<?> aClass) {
        try {
            return (boolean) Unsafe.shouldInitMH.invoke(aClass);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static void ensureClassInitialized(Class<?> aClass) {
        try {
            Unsafe.ensureInitMH.invoke(aClass);
        } catch (Throwable throwable) {
            throw new RuntimeException(throwable);
        }
    }

    public static int arrayBaseOffset(Class<?> aClass) {
        return Unsafe.unsafe.arrayBaseOffset(aClass);
    }

    public static int arrayIndexScale(Class<?> aClass) {
        return Unsafe.unsafe.arrayIndexScale(aClass);
    }

    public static int addressSize() {
        return Unsafe.unsafe.addressSize();
    }

    public static int pageSize() {
        return Unsafe.unsafe.pageSize();
    }

    public static Class<?> defineClass(String s, byte[] bytes, int i, int i1, ClassLoader classLoader, ProtectionDomain protectionDomain) {
        try {
            return (Class<?>) Unsafe.defineClass.invokeExact(s, bytes, i, i1, classLoader, protectionDomain);
        } catch (Throwable throwable) {
            throwException(throwable);
            return null;
        }
    }

    public static Class<?> defineAnonymousClass(Class<?> aClass, byte[] bytes) {
        return defineAnonymousClass(aClass, bytes, (Object[]) null);
    }

    public static Class<?> defineAnonymousClass(Class<?> aClass, byte[] bytes, Object[] objects) {
        if (Unsafe.H_DEF_CLASS != null) {
            try {
                return (Class<?>) Unsafe.H_DEF_CLASS.invokeExact(aClass.getClassLoader(), aClass, (new ClassReader(bytes)).getClassName(), bytes, 0, bytes.length, aClass.getProtectionDomain(), false, 11, (Object) objects);
            } catch (Throwable throwable) {
                throwException(throwable);
                return null;
            }
        } else {
            // sun.misc.Unsafe.defineAnonymousClass was removed in JDK 17; on modern JDKs the
            // H_DEF_CLASS path above is always used, so this branch is unreachable.
            throw new UnsupportedOperationException("defineAnonymousClass is unavailable on this JDK");
        }
    }

    public static Object allocateInstance(Class<?> aClass) throws InstantiationException {
        return Unsafe.unsafe.allocateInstance(aClass);
    }

    public static void throwException(Throwable throwable) {
        Unsafe.unsafe.throwException(throwable);
    }

    public static boolean compareAndSwapObject(Object o, long l, Object o1, Object o2) {
        return Unsafe.unsafe.compareAndSwapObject(o, l, o1, o2);
    }

    public static boolean compareAndSwapInt(Object o, long l, int i, int i1) {
        return Unsafe.unsafe.compareAndSwapInt(o, l, i, i1);
    }

    public static boolean compareAndSwapLong(Object o, long l, long l1, long l2) {
        return Unsafe.unsafe.compareAndSwapLong(o, l, l1, l2);
    }

    public static Object getObjectVolatile(Object o, long l) {
        return Unsafe.unsafe.getObjectVolatile(o, l);
    }

    public static void putObjectVolatile(Object o, long l, Object o1) {
        Unsafe.unsafe.putObjectVolatile(o, l, o1);
    }

    public static int getIntVolatile(Object o, long l) {
        return Unsafe.unsafe.getIntVolatile(o, l);
    }

    public static void putIntVolatile(Object o, long l, int i) {
        Unsafe.unsafe.putIntVolatile(o, l, i);
    }

    public static boolean getBooleanVolatile(Object o, long l) {
        return Unsafe.unsafe.getBooleanVolatile(o, l);
    }

    public static void putBooleanVolatile(Object o, long l, boolean b) {
        Unsafe.unsafe.putBooleanVolatile(o, l, b);
    }

    public static byte getByteVolatile(Object o, long l) {
        return Unsafe.unsafe.getByteVolatile(o, l);
    }

    public static void putByteVolatile(Object o, long l, byte b) {
        Unsafe.unsafe.putByteVolatile(o, l, b);
    }

    public static short getShortVolatile(Object o, long l) {
        return Unsafe.unsafe.getShortVolatile(o, l);
    }

    public static void putShortVolatile(Object o, long l, short i) {
        Unsafe.unsafe.putShortVolatile(o, l, i);
    }

    public static char getCharVolatile(Object o, long l) {
        return Unsafe.unsafe.getCharVolatile(o, l);
    }

    public static void putCharVolatile(Object o, long l, char c) {
        Unsafe.unsafe.putCharVolatile(o, l, c);
    }

    public static long getLongVolatile(Object o, long l) {
        return Unsafe.unsafe.getLongVolatile(o, l);
    }

    public static void putLongVolatile(Object o, long l, long l1) {
        Unsafe.unsafe.putLongVolatile(o, l, l1);
    }

    public static float getFloatVolatile(Object o, long l) {
        return Unsafe.unsafe.getFloatVolatile(o, l);
    }

    public static void putFloatVolatile(Object o, long l, float v) {
        Unsafe.unsafe.putFloatVolatile(o, l, v);
    }

    public static double getDoubleVolatile(Object o, long l) {
        return Unsafe.unsafe.getDoubleVolatile(o, l);
    }

    public static void putDoubleVolatile(Object o, long l, double v) {
        Unsafe.unsafe.putDoubleVolatile(o, l, v);
    }

    public static void putOrderedObject(Object o, long l, Object o1) {
        Unsafe.unsafe.putOrderedObject(o, l, o1);
    }

    public static void putOrderedInt(Object o, long l, int i) {
        Unsafe.unsafe.putOrderedInt(o, l, i);
    }

    public static void putOrderedLong(Object o, long l, long l1) {
        Unsafe.unsafe.putOrderedLong(o, l, l1);
    }

    public static void unpark(Object o) {
        Unsafe.unsafe.unpark(o);
    }

    public static void park(boolean b, long l) {
        Unsafe.unsafe.park(b, l);
    }

    public static int getLoadAverage(double[] doubles, int i) {
        return Unsafe.unsafe.getLoadAverage(doubles, i);
    }

    public static int getAndAddInt(Object o, long l, int i) {
        return Unsafe.unsafe.getAndAddInt(o, l, i);
    }

    public static long getAndAddLong(Object o, long l, long l1) {
        return Unsafe.unsafe.getAndAddLong(o, l, l1);
    }

    public static int getAndSetInt(Object o, long l, int i) {
        return Unsafe.unsafe.getAndSetInt(o, l, i);
    }

    public static long getAndSetLong(Object o, long l, long l1) {
        return Unsafe.unsafe.getAndSetLong(o, l, l1);
    }

    public static Object getAndSetObject(Object o, long l, Object o1) {
        return Unsafe.unsafe.getAndSetObject(o, l, o1);
    }

    public static void loadFence() {
        Unsafe.unsafe.loadFence();
    }

    public static void storeFence() {
        Unsafe.unsafe.storeFence();
    }

    public static void fullFence() {
        Unsafe.unsafe.fullFence();
    }

    public static Class<?> getCallerClass() {
        return Unsafe.INSTANCE.getCallerClass();
    }

    static {
        try {
            Field theUnsafe = sun.misc.Unsafe.class.getDeclaredField("theUnsafe");

            theUnsafe.setAccessible(true);
            unsafe = (sun.misc.Unsafe) theUnsafe.get((Object) null);

            // Lookup is already initialized by the JVM at this point; read the trusted IMPL_LOOKUP
            // directly (the old ensureClassInitialized(Lookup.class) used a now-removed method).
            Field field = Lookup.class.getDeclaredField("IMPL_LOOKUP");
            Object base = Unsafe.unsafe.staticFieldBase(field);
            long offset = Unsafe.unsafe.staticFieldOffset(field);

            lookup = (Lookup) Unsafe.unsafe.getObject(base, offset);

            // jdk.internal.misc.Unsafe instance, read via sun.misc.Unsafe to bypass module checks.
            Class<?> jdkInternalUnsafe = Class.forName("jdk.internal.misc.Unsafe");
            Field internalUnsafeField = jdkInternalUnsafe.getDeclaredField("theUnsafe");
            Object internalUnsafe = Unsafe.unsafe.getObject(Unsafe.unsafe.staticFieldBase(internalUnsafeField), Unsafe.unsafe.staticFieldOffset(internalUnsafeField));

            // ensureClassInitialized / shouldBeInitialized were removed from sun.misc.Unsafe (JDK 22+);
            // route them to jdk.internal.misc.Unsafe through the trusted lookup.
            ensureInitMH = Unsafe.lookup.unreflect(jdkInternalUnsafe.getMethod("ensureClassInitialized", Class.class)).bindTo(internalUnsafe);
            shouldInitMH = Unsafe.lookup.unreflect(jdkInternalUnsafe.getMethod("shouldBeInitialized", Class.class)).bindTo(internalUnsafe);

            MethodHandle mh;

            try {
                Method sunMisc = Unsafe.unsafe.getClass().getMethod("defineClass", String.class, byte[].class, Integer.TYPE, Integer.TYPE, ClassLoader.class, ProtectionDomain.class);

                mh = Unsafe.lookup.unreflect(sunMisc).bindTo(Unsafe.unsafe);
            } catch (Exception exception) {
                Method internalDefineClass = jdkInternalUnsafe.getMethod("defineClass", String.class, byte[].class, Integer.TYPE, Integer.TYPE, ClassLoader.class, ProtectionDomain.class);

                mh = Unsafe.lookup.unreflect(internalDefineClass).bindTo(internalUnsafe);
            }

            defineClass = (MethodHandle) Objects.requireNonNull(mh);
        } catch (Exception exception1) {
            throw new RuntimeException(exception1);
        }

        MethodHandle handle;

        try {
            handle = lookup().findStatic(ClassLoader.class, "defineClass0", MethodType.methodType(Class.class, ClassLoader.class, Class.class, String.class, byte[].class, Integer.TYPE, Integer.TYPE, ProtectionDomain.class, Boolean.TYPE, Integer.TYPE, Object.class));
        } catch (Throwable throwable) {
            try {
                handle = MethodHandles.dropArguments(lookup().findVirtual(ClassLoader.class, "defineClassInternal", MethodType.methodType(Class.class, Class.class, String.class, byte[].class, ProtectionDomain.class, Boolean.TYPE, Integer.TYPE, Object.class)), 4, Arrays.asList(Integer.TYPE, Integer.TYPE));
            } catch (Throwable throwable1) {
                handle = null;
            }
        }

        H_DEF_CLASS = handle;

        // StackWalker works on Java 9+ and avoids SecurityManager, whose constructor throws
        // on JDK 24+ (the Security Manager was permanently disabled by JEP 486).
        INSTANCE = new Unsafe.StackWalkerCallerClassimplements();
    }

    private interface CallerClass {

        Class<?> getCallerClass();
    }

    private static class StackWalkerCallerClassimplements implements Unsafe.CallerClass {

        private final StackWalker walker;

        private StackWalkerCallerClassimplements() {
            this.walker = StackWalker.getInstance(Option.RETAIN_CLASS_REFERENCE);
        }

        public Class<?> getCallerClass() {
            return (Class) this.walker.walk((sx) -> {
                return ((StackFrame) sx.skip(3L).findFirst().orElseThrow()).getDeclaringClass();
            });
        }

        StackWalkerCallerClassimplements(Object x0) {
            this();
        }
    }

}
