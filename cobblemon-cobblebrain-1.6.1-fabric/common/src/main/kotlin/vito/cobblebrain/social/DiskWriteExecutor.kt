package vito.cobblebrain.social

import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

object DiskWriteExecutor {

    @Volatile
    private var executor: ExecutorService = newExecutor()

    private fun newExecutor(): ExecutorService {
        return Executors.newSingleThreadExecutor { r ->
            val thread = Thread(r, "cobblebrain-disk-writer")
            thread.isDaemon = false
            thread
        }
    }

    /** Queues a write task to run off the server thread. */
    fun submit(task: () -> Unit) {
        val exec = executor
        if (exec.isShutdown) {
            // Failsafe: run synchronously if executor is already dead
            try { 
                task() 
            } catch (e: Exception) {
                println("[CobbleBrain] DiskWriteExecutor: fallback sync write failed: ${e.message}")
            }
            return
        }
        exec.submit {
            try {
                task()
            } catch (e: Exception) {
                println("[CobbleBrain] DiskWriteExecutor: async write failed: ${e.message}")
            }
        }
    }

    fun shutdown() {
        val exec = executor
        if (exec.isShutdown) return

        exec.shutdown()
        val finished = exec.awaitTermination(10, TimeUnit.SECONDS)
        if (!finished) {
            println("[CobbleBrain] DiskWriteExecutor: timed out waiting for writes to flush!")
            exec.shutdownNow()
        }

        // Re-create so it's ready if the server starts again in the same JVM (e.g. integrated server)
        executor = newExecutor()
    }
}
