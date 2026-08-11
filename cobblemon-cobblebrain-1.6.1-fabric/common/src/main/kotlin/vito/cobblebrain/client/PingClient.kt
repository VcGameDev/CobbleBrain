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

        val hitResult = cameraEntity.level().clip(context)

        if (hitResult is BlockHitResult && hitResult.type == HitResult.Type.BLOCK) {
            sendPingToServer?.invoke(
                hitResult.blockPos,
                hitResult.direction
            )
        } else {
            // Support mid-air / sky pings when looking into open air
            val airTargetPos = BlockPos.containing(
                eyePosition.add(
                    viewVector.x * 16.0,
                    viewVector.y * 16.0,
                    viewVector.z * 16.0
                )
            )
            sendPingToServer?.invoke(
                airTargetPos,
                Direction.UP
            )
        }
    }
}