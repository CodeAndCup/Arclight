# Vendored patches — Java 25 runtime compatibility

`io.izzel.arclight:arclight-api:1.5.4` is consumed as a binary dependency (its source is not
published). To run on Java 25 it needed patching, so the patched class source is kept here for
review/reproducibility and the rebuilt jar is vendored at
`arclight-forge/libs/arclight-api-1.5.4-j25.jar` (referenced by `arclight-forge/build.gradle`).

## `arclight-api/io/izzel/arclight/api/Unsafe.java`
Patched from the decompiled 1.5.4 class. Changes vs upstream:
- `ensureClassInitialized` / `shouldBeInitialized` are routed to `jdk.internal.misc.Unsafe`
  (the `sun.misc.Unsafe` versions were removed in JDK 22+), resolved through the trusted IMPL_LOOKUP.
- The caller-class provider always uses `StackWalker` (the `SecurityManager` constructor throws on
  JDK 24+, JEP 486).
- The dead `sun.misc.Unsafe.defineAnonymousClass` fallback branch throws instead (gone since JDK 17).
- Restored the `(Object)` cast on the last `defineAnonymousClass` invokeExact argument (a cast the
  decompiler had dropped; without it, plugin event-executor generation threw WrongMethodTypeException).

### Rebuilding the jar
```bash
# compile only Unsafe.java against asm, then swap its classes into a copy of the original jar
javac -XDignore.symbol.file -cp asm-9.x.jar -d out \
  patches/arclight-api/io/izzel/arclight/api/Unsafe.java
# (extract original arclight-api-1.5.4.jar, replace io/izzel/arclight/api/Unsafe*.class with the
#  freshly compiled ones, re-jar as arclight-api-1.5.4-j25.jar)
```
