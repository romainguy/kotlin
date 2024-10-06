// ISSUE: KT-71662

fun testStandardNavigation() {
    val resultA = pcla { otvOwner ->
        otvOwner.constrain(ScopeOwner(Value))
        // should fix OTv := ScopeOwner<Value> for scope navigation
        otvOwner.provide().fieldBackedProperty
        // expected: Interloper </: ScopeOwner<Value>
        otvOwner.constrain(<!ARGUMENT_TYPE_MISMATCH("ScopeOwner<SOT>; Interloper")!>Interloper<!>)
    }
    // expected: ScopeOwner<Value>
    <!DEBUG_INFO_EXPRESSION_TYPE("ScopeOwner<Value>")!>resultA<!>
}

fun testSafeNavigation() {
    val resultA = pcla { otvOwner ->
        otvOwner.constrain(ScopeOwner.Nullable(Value))
        // should fix OTv := ScopeOwner<Value>? for scope navigation
        otvOwner.provide()?.fieldBackedProperty
        // expected: Interloper </: ScopeOwner<Value>?
        otvOwner.constrain(<!ARGUMENT_TYPE_MISMATCH("ScopeOwner<SOT>?; Interloper")!>Interloper<!>)
    }
    // expected: ScopeOwner<Value>?
    <!DEBUG_INFO_EXPRESSION_TYPE("ScopeOwner<Value>?")!>resultA<!>
}


class TypeVariableOwner<T> {
    fun constrain(subtypeValue: T) {}
    fun provide(): T = null!!
}

fun <OT> pcla(lambda: (TypeVariableOwner<OT>) -> Unit): OT = null!!

interface BaseType

object Value

class ScopeOwner<SOT>(value: SOT): BaseType {
    val fieldBackedProperty: SOT = value
    companion object {
        fun <SOT> Nullable(value: SOT): ScopeOwner<SOT>? = null
    }
}

object Interloper: BaseType
