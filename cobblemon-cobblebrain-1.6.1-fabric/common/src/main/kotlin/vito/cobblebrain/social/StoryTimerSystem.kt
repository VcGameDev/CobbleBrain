package vito.cobblebrain.social

import net.minecraft.server.MinecraftServer
import net.minecraft.network.chat.Component
import net.minecraft.ChatFormatting

object StoryTimerSystem {
    private val activeTimers = mutableMapOf<String, Long>() // timerId -> endTick

    fun startTimer(timerId: String, durationTicks: Long, server: MinecraftServer) {
        activeTimers[timerId] = server.tickCount + durationTicks
        println("[STORY TIMER] Started $timerId for $durationTicks ticks")
    }

    fun stopTimer(timerId: String) {
        activeTimers.remove(timerId)
        println("[STORY TIMER] Stopped $timerId")
    }

    fun isTimerActive(timerId: String): Boolean {
        return activeTimers.containsKey(timerId)
    }

    fun getRemainingTicks(timerId: String, server: MinecraftServer): Long {
        val end = activeTimers[timerId] ?: return 0
        return (end - server.tickCount).coerceAtLeast(0)
    }

    fun tick(server: MinecraftServer) {
        val expired = mutableListOf<String>()
        val now = server.tickCount.toLong()

        activeTimers.forEach { (id, end) ->
            if (now >= end) {
                expired.add(id)
            }
        }

        expired.forEach { id ->
            onTimerExpire(id, server)
            activeTimers.remove(id)
        }
    }

    private fun onTimerExpire(timerId: String, server: MinecraftServer) {
        println("[STORY TIMER] Timer $timerId expired!")
        
        // Exemplo: notificar todos os jogadores ou disparar evento de história
        // lembrar q isso é só uma sugestão, dps trocar pra ser apenas rodado em singleplayer ou LAN
        server.playerList.players.forEach { player ->
            player.sendSystemMessage(
                Component.literal("A story event has triggered! ($timerId)")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
            )
        }
        
        // posso adicionar lógica específica para cada timerId
        when (timerId) {
            "invasion" -> {
                // Disparar invasão
            }
            "mystery_solved" -> {
                // Liberar nova área
            }
        }
    }
}
