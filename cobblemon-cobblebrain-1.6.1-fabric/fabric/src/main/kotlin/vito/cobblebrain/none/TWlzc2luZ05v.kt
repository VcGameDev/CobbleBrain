package vito.cobblebrain.missingno

import net.minecraft.server.level.ServerPlayer
import net.minecraft.world.entity.Mob
import net.minecraft.world.effect.MobEffectInstance
import net.minecraft.world.effect.MobEffects
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import net.minecraft.world.entity.Entity
import vito.cobblebrain.none.KnowledgeLevel
import kotlin.random.Random

object TWlzc2luZ05v {
    var knowledge: KnowledgeLevel = KnowledgeLevel.VERY_LOW

    // Atualiza conhecimento conforme os dias
    fun updateKnowledge(daysPassed: Int) {
        knowledge = when {
            daysPassed < 5 -> KnowledgeLevel.VERY_LOW
            daysPassed < 15 -> KnowledgeLevel.LOW
            daysPassed < 30 -> KnowledgeLevel.MEDIUM
            else -> KnowledgeLevel.HIGH
        }
    }

    // Infecção de mobs próximos ao jogador
    fun infectNearbyMobs(player: ServerPlayer) {
        val radius = 10.0 // raio de infecção
        val mobs = player.serverLevel().getEntitiesOfClass(
            Mob::class.java,
            player.boundingBox.inflate(radius)
        )

        for (mob in mobs) {
            when (knowledge) {
                KnowledgeLevel.VERY_LOW -> {
                    // Apenas gritos estranhos
                    player.serverLevel().playSound(
                        null,
                        mob.blockPosition(),
                        SoundEvents.ENDERMAN_SCREAM,
                        SoundSource.HOSTILE,
                        0.5f,
                        Random.nextFloat() * 0.5f + 0.5f
                    )
                }
                KnowledgeLevel.LOW -> {
                    // Além dos gritos, pode causar dano leve
                    mob.hurt(mob.damageSources().magic(), 2f)
                }
                KnowledgeLevel.MEDIUM -> {
                    // Desbloqueia desaparecimento ocasional
                    if (Random.nextDouble() < 0.2) {
                        mob.remove(Entity.RemovalReason.KILLED)
                    } else {
                        mob.hurt(mob.damageSources().magic(), 4f)
                    }
                }
                KnowledgeLevel.HIGH -> {
                    // Desbloqueia ataques entre mobs
                    if (Random.nextDouble() < 0.3) {
                        val target = mobs.randomOrNull()
                        if (target != null && target != mob) {
                            mob.target = target
                        }
                    }
                    mob.addEffect(MobEffectInstance(MobEffects.WITHER, 200))
                }
            }
        }
    }
}

enum class KnowledgeLevel { VERY_LOW, LOW, MEDIUM, HIGH }
