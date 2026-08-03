package vito.cobblebrain.client.mcmti

import vito.cobblebrain.client.CobblebrainClientCommon
import vito.cobblebrain.config.ClientConfigHandler

object McmtiFabricHandler {
    var awaitingPokemonVoice: Boolean = false

    fun register() {
        try {
            val eventClasses = listOf(
                "io.github.jaffe2718.mcmti.client.event.McmtiSpeechRecognizerEvents",
                "io.github.jaffe2718.mcmti.event.McmtiSpeechRecognizerEvents",
                "io.github.jaffe2718.mcmti.event.SpeechRecognizerEvents"
            )
            var eventClass: Class<*>? = null
            for (className in eventClasses) {
                try {
                    eventClass = Class.forName(className)
                    if (eventClass != null) break
                } catch (_: ClassNotFoundException) {}
            }
            if (eventClass == null) return

            val field = eventClass.fields.firstOrNull { 
                it.name.contains("TRANSCRIBED", ignoreCase = true) || it.name.contains("SPEECH", ignoreCase = true) 
            } ?: return

            val eventObj = field.get(null) ?: return

            val registerMethod = eventObj.javaClass.methods.firstOrNull { it.name == "register" && it.parameterCount == 1 } ?: return
            val listenerInterface = registerMethod.parameterTypes[0]

            val proxyListener = java.lang.reflect.Proxy.newProxyInstance(
                listenerInterface.classLoader,
                arrayOf(listenerInterface)
            ) { _, method, args ->
                if (method.name == "onTranscribe" || method.name == "transcribe" || method.name == "invoke" || method.name == "accept" || method.parameterCount >= 1) {
                    if (ClientConfigHandler.clientConfig.enableStt && awaitingPokemonVoice) {
                        awaitingPokemonVoice = false
                        val transcription = extractText(args?.lastOrNull() ?: args?.firstOrNull()).trim()
                        if (transcription.isNotBlank()) {
                            CobblebrainClientCommon.sendVoiceInputToServer?.invoke(transcription)
                        }
                    }
                }
                null
            }

            registerMethod.invoke(eventObj, proxyListener)
            println("[CobbleBrain STT] Successfully registered Fabric MCMti event listener via reflection.")
        } catch (e: Throwable) {
            println("[CobbleBrain STT] Fabric MCMti event registration note: ${e.message}")
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
