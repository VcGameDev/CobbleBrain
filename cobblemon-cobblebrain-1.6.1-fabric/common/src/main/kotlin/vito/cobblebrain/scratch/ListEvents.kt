package vito.cobblebrain.scratch

import com.cobblemon.mod.common.api.events.CobblemonEvents
import java.lang.reflect.Modifier

fun main() {
    println("=== COBBLEMON EVENTS ===")
    val clazz = CobblemonEvents::class.java
    for (field in clazz.declaredFields) {
        if (Modifier.isPublic(field.modifiers) && Modifier.isStatic(field.modifiers)) {
            println(field.name)
        }
    }
}
