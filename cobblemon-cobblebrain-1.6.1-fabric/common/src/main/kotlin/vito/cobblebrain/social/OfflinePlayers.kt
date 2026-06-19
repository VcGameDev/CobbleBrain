package vito.cobblebrain.social

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object OfflinePlayers {
    val offlineMode = ConcurrentHashMap<UUID, Boolean>()
    val offlineTalkMode = ConcurrentHashMap<UUID, Boolean>()

    fun isOffline(uuid: UUID): Boolean {
        return offlineMode.getOrDefault(uuid, false)
    }

    fun isOfflineTalk(uuid: UUID): Boolean {
        return offlineTalkMode.getOrDefault(uuid, false)
    }

    fun removePlayer(uuid: UUID) {
        offlineMode.remove(uuid)
        offlineTalkMode.remove(uuid)
    }
}
