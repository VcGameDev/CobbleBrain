package vito.cobblebrain.client.mcmti

import net.fabricmc.fabric.api.client.message.v1.ClientSendMessageEvents
import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import vito.cobblebrain.client.CobblebrainClientCommon
import vito.cobblebrain.config.ClientConfigHandler

object McmtiFabricHandler {
    var awaitingPokemonVoice: Boolean = false
    private var voiceRecordingStartTime: Long = 0L
    private var recognizeKey: KeyMapping? = null
    private var isMcmtiKeyResolved: Boolean = false

    fun getMcmtiKey(): KeyMapping? {
        if (!isMcmtiKeyResolved) {
            isMcmtiKeyResolved = true
            val candidatePackages = listOf(
                "me.jaffe2718.mcmti.MicrophoneTextInput",
                "io.github.jaffe2718.mcmti.MicrophoneTextInput"
            )
            for (pkg in candidatePackages) {
                try {
                    val clazz = Class.forName(pkg)
                    val field = clazz.getField("RECOGNIZE_KEY")
                    recognizeKey = field.get(null) as? KeyMapping
                    if (recognizeKey != null) break
                } catch (_: Throwable) {}
            }
            if (recognizeKey == null) {
                try {
                    recognizeKey = Minecraft.getInstance().options.keyMappings.firstOrNull {
                        it.name == "key.mcmti.recognize" || it.name.contains("mcmti", ignoreCase = true)
                    }
                } catch (_: Throwable) {}
            }
            if (recognizeKey != null) {
                println("[CobbleBrain STT] Successfully resolved MCMti RECOGNIZE_KEY: ${recognizeKey?.name}")
            } else {
                println("[CobbleBrain STT] Note: Could not resolve MCMti RECOGNIZE_KEY via reflection or keyMappings.")
            }
        }
        return recognizeKey
    }

    fun startRecording(): Boolean {
        val key = getMcmtiKey() ?: return false
        awaitingPokemonVoice = true
        voiceRecordingStartTime = System.currentTimeMillis()
        CobblebrainClientCommon.isVoiceRecording = true
        key.setDown(true)
        return true
    }

    fun stopRecording() {
        val key = getMcmtiKey()
        key?.setDown(false)
        CobblebrainClientCommon.isVoiceRecording = false
        voiceRecordingStartTime = System.currentTimeMillis()
    }

    fun register() {
        CobblebrainClientCommon.startVoiceRecording = { startRecording() }
        CobblebrainClientCommon.stopVoiceRecording = { stopRecording() }

        // Primary: Intercept outgoing chat sent by MCMTI (which sends transcribed audio via player.connection.sendChat)
        ClientSendMessageEvents.ALLOW_CHAT.register(ClientSendMessageEvents.AllowChat { message ->
            if (awaitingPokemonVoice && ClientConfigHandler.clientConfig.enableStt) {
                if (System.currentTimeMillis() - voiceRecordingStartTime > 20000L) {
                    awaitingPokemonVoice = false
                    return@AllowChat true
                }
                awaitingPokemonVoice = false
                val transcription = message.trim()
                Minecraft.getInstance().execute {
                    if (transcription.isNotBlank()) {
                        CobblebrainClientCommon.sendVoiceInputToServer?.invoke(transcription)
                        Minecraft.getInstance().player?.displayClientMessage(
                            Component.literal("§a✔ [CobbleBrain] \"$transcription\""),
                            true
                        )
                    } else {
                        Minecraft.getInstance().player?.displayClientMessage(
                            Component.literal("§c❌ [CobbleBrain] No speech detected."),
                            true
                        )
                    }
                }
                return@AllowChat false // Do not broadcast to public Minecraft chat
            }
            true
        })

        // Secondary fallback: Try registering reflection event listener if a custom event class exists in other MCMTI forks/versions
        try {
            val eventClasses = listOf(
                "me.jaffe2718.mcmti.fabric.event.McmtiSpeechRecognizerEvents",
                "me.jaffe2718.mcmti.client.event.McmtiSpeechRecognizerEvents",
                "me.jaffe2718.mcmti.event.McmtiSpeechRecognizerEvents",
                "io.github.jaffe2718.mcmti.fabric.event.McmtiSpeechRecognizerEvents",
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
            if (eventClass != null) {
                val field = eventClass.fields.firstOrNull { 
                    it.name.contains("TRANSCRIBED", ignoreCase = true) || it.name.contains("SPEECH", ignoreCase = true) 
                }
                if (field != null) {
                    val eventObj = field.get(null)
                    if (eventObj != null) {
                        val registerMethod = eventObj.javaClass.methods.firstOrNull { it.name == "register" && it.parameterCount == 1 }
                        if (registerMethod != null) {
                            val listenerInterface = registerMethod.parameterTypes[0]
                            val proxyListener = java.lang.reflect.Proxy.newProxyInstance(
                                listenerInterface.classLoader,
                                arrayOf(listenerInterface)
                            ) { _, method, args ->
                                if (method.name == "onTranscribe" || method.name == "transcribe" || method.name == "invoke" || method.name == "accept" || method.parameterCount >= 1) {
                                    if (ClientConfigHandler.clientConfig.enableStt && awaitingPokemonVoice) {
                                        awaitingPokemonVoice = false
                                        val transcription = extractText(args?.lastOrNull() ?: args?.firstOrNull()).trim()
                                        Minecraft.getInstance().execute {
                                            if (transcription.isNotBlank()) {
                                                CobblebrainClientCommon.sendVoiceInputToServer?.invoke(transcription)
                                                Minecraft.getInstance().player?.displayClientMessage(
                                                    Component.literal("§a✔ [CobbleBrain] \"$transcription\""),
                                                    true
                                                )
                                            } else {
                                                Minecraft.getInstance().player?.displayClientMessage(
                                                    Component.literal("§c❌ [CobbleBrain] No speech detected."),
                                                    true
                                                )
                                            }
                                        }
                                    }
                                }
                                null
                            }
                            registerMethod.invoke(eventObj, proxyListener)
                            println("[CobbleBrain STT] Successfully registered Fabric MCMti event listener via reflection.")
                        }
                    }
                }
            }
        } catch (e: Throwable) {
            println("[CobbleBrain STT] Fabric MCMti fallback event registration note: ${e.message}")
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
