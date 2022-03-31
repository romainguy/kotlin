// CHECK_BYTECODE_LISTING
// WITH_STDLIB
// TARGET_BACKEND: JVM_IR
// WORKS_WHEN_VALUE_CLASS
// LANGUAGE: +ValueClasses

@JvmInline
value class A<T: Any>(val x: List<T>)

@JvmInline
value class B(val x: UInt)

@JvmInline
value class C(val x: Int, val y: B, val z: String = "3")

@JvmInline
value class D(val x: C) {
    constructor(x: Int, y: B, z: Int) : this(C(1, B(2U)))
    init {
        println("x")
    }
}

@JvmInline
value class E(val x: D)

@JvmInline
value class R<T: Any>(val x: Int, val y: UInt, val z: E, val t: A<T>)

// todo nontrivial constructors
// todo add default parameters

fun box() = "OK"
