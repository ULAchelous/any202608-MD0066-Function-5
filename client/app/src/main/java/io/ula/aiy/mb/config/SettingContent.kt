package io.ula.aiy.mb.config

enum class CONTENT_TYPE{
    BOOL,NUMBER
}
class SettingContent<T: Number>(val id: String, val initialValue: T,val type : CONTENT_TYPE,val initialFunc : (T) -> Boolean){
    var value : T;
    init {
        this.value = initialValue
    }

    private fun checkOut(){
        if(!initialFunc.invoke(this.value)){
            value = initialValue;
        }
    }

    public fun set(value : T){
        this.value = value;
        checkOut()
    }

    public fun getBoolean() : Boolean{
        checkOut()
        var b = if(this.value.toDouble() < 1.0) false else true
        return b
    }

    public fun getNumber() : T{
        checkOut()
        return this.value
    }

    public fun reset(){
        this.value = initialValue
    }
}
