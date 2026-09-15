package vito.cobblebrain.social

import java.util.UUID
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.CopyOnWriteArrayList

object RecentEventsSystem {
    enum class CommandSource {
        AI, HUD
    }

    // Tracks whether a command currently set on a Pokémon came from AI or HUD
    val commandSources = ConcurrentHashMap<UUID, CommandSource>()

    interface RecentEvent {
        val timestamp: Long
        fun format(currentTime: Long): String
        fun canMergeWith(other: RecentEvent): Boolean
        fun merge(other: RecentEvent): RecentEvent
    }

    data class DamageEvent(
        val targetName: String,
        val attackerType: String,
        override val timestamp: Long,
        val count: Int = 1
    ) : RecentEvent {
        override fun format(currentTime: Long): String {
            val countStr = if (count > 1) " x$count" else ""
            return "${formatElapsed(timestamp, currentTime)} $targetName took damage from $attackerType$countStr"
        }
        override fun canMergeWith(other: RecentEvent): Boolean {
            return other is DamageEvent && other.targetName == this.targetName && other.attackerType == this.attackerType
        }
        override fun merge(other: RecentEvent): RecentEvent {
            val o = other as DamageEvent
            return DamageEvent(targetName, attackerType, maxOf(this.timestamp, o.timestamp), this.count + o.count)
        }
    }

    data class FaintEvent(
        val pokemonName: String,
        val cause: String,
        override val timestamp: Long
    ) : RecentEvent {
        override fun format(currentTime: Long): String {
            return "${formatElapsed(timestamp, currentTime)} $pokemonName fainted (Cause: $cause)"
        }
        override fun canMergeWith(other: RecentEvent): Boolean = false
        override fun merge(other: RecentEvent): RecentEvent = this
    }

    data class EatEvent(
        val pokemonName: String,
        val itemName: String,
        val source: String,
        val trigger: String,
        override val timestamp: Long
    ) : RecentEvent {
        override fun format(currentTime: Long): String {
            return "${formatElapsed(timestamp, currentTime)} $pokemonName ate $itemName from $source (triggered by $trigger)"
        }
        override fun canMergeWith(other: RecentEvent): Boolean = false
        override fun merge(other: RecentEvent): RecentEvent = this
    }

    data class ActionEvent(
        val pokemonName: String,
        val action: String,
        val trigger: String,
        override val timestamp: Long,
        val count: Int = 1
    ) : RecentEvent {
        override fun format(currentTime: Long): String {
            val countStr = if (count > 1) " x$count" else ""
            return "${formatElapsed(timestamp, currentTime)} $pokemonName used action $action (triggered by $trigger)$countStr"
        }
        override fun canMergeWith(other: RecentEvent): Boolean {
            return other is ActionEvent && other.pokemonName == this.pokemonName && other.action == this.action && other.trigger == this.trigger
        }
        override fun merge(other: RecentEvent): RecentEvent {
            val o = other as ActionEvent
            return ActionEvent(pokemonName, action, trigger, maxOf(this.timestamp, o.timestamp), this.count + o.count)
        }
    }

    data class PlayerKillEvent(
        val entityType: String,
        override val timestamp: Long,
        val count: Int = 1
    ) : RecentEvent {
        override fun format(currentTime: Long): String {
            val countStr = if (count > 1) " x$count" else ""
            return "${formatElapsed(timestamp, currentTime)} Player killed $entityType$countStr"
        }
        override fun canMergeWith(other: RecentEvent): Boolean {
            return other is PlayerKillEvent && other.entityType == this.entityType
        }
        override fun merge(other: RecentEvent): RecentEvent {
            val o = other as PlayerKillEvent
            return PlayerKillEvent(entityType, maxOf(this.timestamp, o.timestamp), this.count + o.count)
        }
    }

    data class PokemonKillEvent(
        val pokemonName: String,
        val entityType: String,
        val trigger: String,
        override val timestamp: Long,
        val count: Int = 1
    ) : RecentEvent {
        override fun format(currentTime: Long): String {
            val countStr = if (count > 1) " x$count" else ""
            return "${formatElapsed(timestamp, currentTime)} $pokemonName killed $entityType (triggered by $trigger)$countStr"
        }
        override fun canMergeWith(other: RecentEvent): Boolean {
            return other is PokemonKillEvent && other.pokemonName == this.pokemonName && other.entityType == this.entityType && other.trigger == this.trigger
        }
        override fun merge(other: RecentEvent): RecentEvent {
            val o = other as PokemonKillEvent
            return PokemonKillEvent(pokemonName, entityType, trigger, maxOf(this.timestamp, o.timestamp), this.count + o.count)
        }
    }

    data class PlayerSleepEvent(
        override val timestamp: Long
    ) : RecentEvent {
        override fun format(currentTime: Long): String {
            return "${formatElapsed(timestamp, currentTime)} Player went to sleep"
        }
        override fun canMergeWith(other: RecentEvent): Boolean = false
        override fun merge(other: RecentEvent): RecentEvent = this
    }

    data class BattleEvent(
        val opponent: String,
        val result: String,
        val isWild: Boolean,
        override val timestamp: Long
    ) : RecentEvent {
        override fun format(currentTime: Long): String {
            val typeStr = if (isWild) "wild" else "trainer/team"
            return "${formatElapsed(timestamp, currentTime)} Battle with $opponent ($typeStr): $result"
        }
        override fun canMergeWith(other: RecentEvent): Boolean = false
        override fun merge(other: RecentEvent): RecentEvent = this
    }

    data class WakeUpEvent(
        val pokemonName: String,
        val reason: String,
        override val timestamp: Long
    ) : RecentEvent {
        override fun format(currentTime: Long): String {
            return "${formatElapsed(timestamp, currentTime)} $pokemonName woke up because $reason"
        }
        override fun canMergeWith(other: RecentEvent): Boolean = false
        override fun merge(other: RecentEvent): RecentEvent = this
    }

    // Storage: pokemonUuid -> events list
    private val storage = ConcurrentHashMap<UUID, CopyOnWriteArrayList<RecentEvent>>()

    fun recordEvent(pokemonUuid: UUID, event: RecentEvent) {
        val list = storage.getOrPut(pokemonUuid) { CopyOnWriteArrayList() }
        list.add(event)
        while (list.size > 15) {
            list.removeAt(0)
        }
    }

    fun getAndClearEvents(pokemonUuids: List<UUID>): List<String> {
        val allEvents = mutableListOf<RecentEvent>()
        pokemonUuids.forEach { uuid ->
            val list = storage.remove(uuid)
            if (list != null) {
                allEvents.addAll(list)
            }
        }

        if (allEvents.isEmpty()) return emptyList()

        // Sort chronologically
        allEvents.sortBy { it.timestamp }

        // Condense repeated/mergeable events
        val condensed = mutableListOf<RecentEvent>()
        for (event in allEvents) {
            val last = condensed.lastOrNull()
            if (last != null && last.canMergeWith(event)) {
                condensed[condensed.size - 1] = last.merge(event)
            } else {
                condensed.add(event)
            }
        }

        val currentTime = System.currentTimeMillis()
        return condensed.map { it.format(currentTime) }
    }

    private fun formatElapsed(eventTime: Long, currentTime: Long): String {
        val diffMs = maxOf(0L, currentTime - eventTime)
        val diffSec = diffMs / 1000
        val hrs = diffSec / 3600
        val mins = (diffSec % 3600) / 60
        val secs = diffSec % 60
        return if (hrs > 0) {
            String.format("[%d:%02d:%02d]", hrs, mins, secs)
        } else {
            String.format("[%02d:%02d]", mins, secs)
        }
    }
}
