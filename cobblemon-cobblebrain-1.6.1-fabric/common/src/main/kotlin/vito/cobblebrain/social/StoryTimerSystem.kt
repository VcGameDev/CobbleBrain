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
        
        // Example: notify all players or trigger story event
        // Note: suggestion for now, could be changed to singleplayer or LAN only
        server.playerList.players.forEach { player ->
            player.sendSystemMessage(
                Component.literal("A story event has triggered! ($timerId)")
                    .withStyle(ChatFormatting.DARK_RED, ChatFormatting.BOLD)
            )
        }
        
        // Specific logic per timerId
        when (timerId) {
            "invasion" -> {
                // Trigger invasion
            }
            "mystery_solved" -> {
                // Unlock new area
            }
        }
    }
}
