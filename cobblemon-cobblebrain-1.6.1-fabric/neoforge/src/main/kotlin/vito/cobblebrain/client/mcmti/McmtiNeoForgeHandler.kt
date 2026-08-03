package vito.cobblebrain.client.mcmti

import net.neoforged.neoforge.common.NeoForge
import vito.cobblebrain.client.CobblebrainClientCommon
import vito.cobblebrain.config.ClientConfigHandler

object McmtiNeoForgeHandler {
    var awaitingPokemonVoice: Boolean = false

    fun register() {
        try {
            val eventClasses = listOf(
                "io.github.jaffe2718.mcmti.client.event.SpeechRecognizerEvent\$Transcribed",
                "io.github.jaffe2718.mcmti.event.SpeechRecognizerEvent\$Transcribed"
            )
            var transcribedClass: Class<*>? = null
            for (className in eventClasses) {
                try {
                    transcribedClass = Class.forName(className)
                    if (transcribedClass != null) break
                } catch (_: ClassNotFoundException) {}
            }
            if (transcribedClass == null) return

            val consumer = java.util.function.Consumer<Any> { event ->
                if (transcribedClass.isInstance(event)) {
                    if (ClientConfigHandler.clientConfig.enableStt && awaitingPokemonVoice) {
                        awaitingPokemonVoice = false
                        val transcription = extractText(event).trim()
                        if (transcription.isNotBlank()) {
                            CobblebrainClientCommon.sendVoiceInputToServer?.invoke(transcription)
                        }
                        try {
                            val cancelableMethod = event.javaClass.getMethod("isCancelable")
                            val isCancelable = cancelableMethod.invoke(event) as? Boolean ?: false
                            if (isCancelable) {
                                val setCanceledMethod = event.javaClass.getMethod("setCanceled", Boolean::class.javaPrimitiveType)
                                setCanceledMethod.invoke(event, true)
                            }
                        } catch (_: Throwable) {}
                    }
                }
            }

            val addListenerMethod = NeoForge.EVENT_BUS.javaClass.methods.firstOrNull { 
                it.name == "addListener" && it.parameterCount == 1 
            }
            if (addListenerMethod != null) {
                addListenerMethod.invoke(NeoForge.EVENT_BUS, consumer)
                println("[CobbleBrain STT] Successfully registered NeoForge MCMti event listener via reflection.")
            }
        } catch (e: Throwable) {
            println("[CobbleBrain STT] NeoForge MCMti event registration note: ${e.message}")
        }
    }

    private fun extractText(obj: Any?): String {
        if (obj == null) return ""
        if (obj is String) return obj
        val clazz = obj.javaClass
        for (methodName in listOf("getTranscription", "getText", "getResult", "transcription", "text", "result")) {
            try {
                val method = clazz.methods.firstOrNull { it.name.equals(methodName, ignoreCase = true) && it.parameterCount == 0 }
                if (method != null) {
                    val res = method.invoke(obj)
                    if (res is String && res.isNotBlank()) return res
                }
            } catch (_: Throwable) {}
        }
        for (fieldName in listOf("transcription", "text", "result")) {
            try {
                val field = clazz.declaredFields.firstOrNull { it.name.equals(fieldName, ignoreCase = true) }
                if (field != null) {
                    field.isAccessible = true
                    val res = field.get(obj)
                    if (res is String && res.isNotBlank()) return res
                }
            } catch (_: Throwable) {}
        }
        return obj.toString()
    }
}
