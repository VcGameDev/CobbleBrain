package vito.cobblebrain.engine

import net.minecraft.server.MinecraftServer
import java.util.UUID
import java.util.concurrent.ConcurrentLinkedQueue
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

data class DelayedTask(
    val id: String = UUID.randomUUID().toString(),
    val targetTimeMs: Long,
    val callback: () -> Unit
)

object TickManager {
    private val tasks = ConcurrentLinkedQueue<DelayedTask>()
    private val executor = Executors.newSingleThreadScheduledExecutor { r ->
        Thread(r, "CobbleBrain-TickManager").apply { isDaemon = true }
    }

    init {
        executor.scheduleAtFixedRate({
            tick()
        }, 50, 50, TimeUnit.MILLISECONDS)
    }

    fun schedule(delayTicks: Int, callback: () -> Unit) {
        if (delayTicks <= 0) {
            callback()
        } else {
            val delayMs = delayTicks * 50L
            val targetMs = System.currentTimeMillis() + delayMs
            tasks.add(DelayedTask(targetTimeMs = targetMs, callback = callback))
        }
    }

    fun tick(server: MinecraftServer? = null) {
        if (tasks.isEmpty()) return
        val now = System.currentTimeMillis()

        val iterator = tasks.iterator()
        while (iterator.hasNext()) {
            val task = iterator.next()
            if (now >= task.targetTimeMs) {
                iterator.remove()
                try {
                    task.callback()
                } catch (e: Exception) {
                    e.printStackTrace()
                }
            }
        }
    }

    fun clear() {
        tasks.clear()
    }
}
