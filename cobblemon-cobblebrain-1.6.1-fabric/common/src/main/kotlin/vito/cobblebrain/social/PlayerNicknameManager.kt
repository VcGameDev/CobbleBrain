package vito.cobblebrain.social

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap

object PlayerNicknameManager {
    private val nicknames = ConcurrentHashMap<UUID, String>()

    fun set(uuid: UUID, nickname: String) {
        nicknames[uuid] = nickname
    }

    fun get(uuid: UUID, fallback: String): String {
        val nickname = nicknames[uuid]
        return if (nickname.isNullOrBlank()) fallback else nickname
    }
}
