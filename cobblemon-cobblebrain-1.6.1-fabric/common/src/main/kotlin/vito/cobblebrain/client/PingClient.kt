package vito.cobblebrain.client

import net.minecraft.client.Minecraft
import net.minecraft.core.BlockPos
import net.minecraft.world.level.ClipContext
import net.minecraft.world.phys.HitResult

import net.minecraft.core.Direction
import net.minecraft.world.phys.BlockHitResult

object PingClient {

    var sendPingToServer:
            ((BlockPos, Direction) -> Unit)? = null

    fun triggerPingRaycast() {

        val client = Minecraft.getInstance()
        val cameraEntity = client.cameraEntity ?: return

        val pickRange = 64.0

        val eyePosition =
            cameraEntity.getEyePosition(1.0f)

        val viewVector =
            cameraEntity.getViewVector(1.0f)

        val endPosition =
            eyePosition.add(
                viewVector.x * pickRange,
                viewVector.y * pickRange,
                viewVector.z * pickRange
            )

        val context =
            ClipContext(
                eyePosition,
                endPosition,
                ClipContext.Block.OUTLINE,
                ClipContext.Fluid.NONE,
                cameraEntity
            )

        val hitResult =
            cameraEntity.level().clip(context)

        if (
            hitResult is BlockHitResult
        ) {

            sendPingToServer?.invoke(
                hitResult.blockPos,
                hitResult.direction
            )
        }
    }
}