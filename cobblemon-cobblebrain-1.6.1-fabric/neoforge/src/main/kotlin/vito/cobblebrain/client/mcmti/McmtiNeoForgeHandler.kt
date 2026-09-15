package vito.cobblebrain.client.mcmti

import net.minecraft.client.KeyMapping
import net.minecraft.client.Minecraft
import net.minecraft.network.chat.Component
import net.neoforged.neoforge.client.event.ClientChatEvent
import net.neoforged.neoforge.common.NeoForge
import vito.cobblebrain.client.CobblebrainClientCommon
import vito.cobblebrain.config.ClientConfigHandler

object McmtiNeoForgeHandler {
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

        // Primary: Intercept outgoing chat sent by MCMTI
        NeoForge.EVENT_BUS.addListener { event: ClientChatEvent ->
            if (awaitingPokemonVoice && ClientConfigHandler.clientConfig.enableStt) {
                if (System.currentTimeMillis() - voiceRecordingStartTime > 20000L) {
                    awaitingPokemonVoice = false
                    return@addListener
                }
                awaitingPokemonVoice = false
                val transcription = event.message.trim()
                event.isCanceled = true // Do not broadcast to public Minecraft chat
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

        // Secondary fallback: Custom reflection event listener if available in certain versions
        try {
            val eventClasses = listOf(
                "me.jaffe2718.mcmti.neoforge.event.SpeechRecognizerEvent\$Transcribed",
                "me.jaffe2718.mcmti.client.event.SpeechRecognizerEvent\$Transcribed",
                "me.jaffe2718.mcmti.event.SpeechRecognizerEvent\$Transcribed",
                "io.github.jaffe2718.mcmti.neoforge.event.SpeechRecognizerEvent\$Transcribed",
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
            if (transcribedClass != null) {
                val consumer = java.util.function.Consumer<Any> { event ->
                    if (transcribedClass.isInstance(event)) {
                        if (ClientConfigHandler.clientConfig.enableStt && awaitingPokemonVoice) {
                            awaitingPokemonVoice = false
                            val transcription = extractText(event).trim()
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
            }
        } catch (e: Throwable) {
            println("[CobbleBrain STT] NeoForge MCMti fallback event registration note: ${e.message}")
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
