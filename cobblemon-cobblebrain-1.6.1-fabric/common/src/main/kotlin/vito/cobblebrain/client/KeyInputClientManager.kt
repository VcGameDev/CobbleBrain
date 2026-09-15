package vito.cobblebrain.client

import com.mojang.blaze3d.platform.InputConstants
import net.minecraft.client.Minecraft
import net.minecraft.client.gui.Font
import net.minecraft.client.gui.GuiGraphics
import net.minecraft.sounds.SoundEvents
import net.minecraft.sounds.SoundSource
import org.lwjgl.glfw.GLFW
import vito.cobblebrain.network.CobblebrainPayloads
import kotlin.math.cos
import kotlin.math.sin

object KeyInputClientManager {
    var sendResultPayload: ((CobblebrainPayloads.KeyInputResultPayload) -> Unit)? = null

    private var activeRequest: CobblebrainPayloads.StartKeyInputPayload? = null
    val standaloneListeners = java.util.concurrent.ConcurrentHashMap<String, CobblebrainPayloads.StartKeyInputPayload>()
    private val standaloneKeysDown = java.util.concurrent.ConcurrentHashMap<String, Boolean>()
    private val standaloneLastPulseMs = java.util.concurrent.ConcurrentHashMap<String, Long>()

    // Runtime State
    var holdProgress: Float = 0f
    var mashCount: Float = 0f
    var elapsedTimeSec: Float = 0f
    private var lastPulseTimeMs: Long = 0L
    private var wasKeyDown: Boolean = false
    private var hadInitialPress: Boolean = false
    private var mashImpactPulse: Float = 0f

    fun startInput(payload: CobblebrainPayloads.StartKeyInputPayload) {
        if (payload.isStandalone) {
            standaloneListeners[payload.nodeId] = payload
            standaloneKeysDown[payload.nodeId] = false
            standaloneLastPulseMs[payload.nodeId] = System.currentTimeMillis()
            return
        }
        activeRequest = payload
        holdProgress = 0f
        mashCount = 0f
        elapsedTimeSec = 0f
        lastPulseTimeMs = System.currentTimeMillis()
        wasKeyDown = false
        hadInitialPress = false
        mashImpactPulse = 0f
    }

    fun cancelInput(nodeId: String? = null) {
        if (nodeId == null || nodeId.isBlank() || activeRequest?.nodeId == nodeId) {
            activeRequest = null
            holdProgress = 0f
            mashCount = 0f
            elapsedTimeSec = 0f
        }
        if (nodeId == null || nodeId.isBlank()) {
            standaloneListeners.clear()
            standaloneKeysDown.clear()
            standaloneLastPulseMs.clear()
        } else {
            standaloneListeners.remove(nodeId)
            standaloneKeysDown.remove(nodeId)
            standaloneLastPulseMs.remove(nodeId)
        }
    }

    @Suppress("unused")
    fun isInputActive(): Boolean = activeRequest != null

    private fun sendResult(req: CobblebrainPayloads.StartKeyInputPayload, event: String) {
        sendResultPayload?.invoke(
            CobblebrainPayloads.KeyInputResultPayload(
                storyId = req.storyId,
                nodeId = req.nodeId,
                resultEvent = event
            )
        )
    }

    private fun playSuccessSound(mc: Minecraft) {
        val player = mc.player ?: return
        mc.level?.playSound(player, player.x, player.y, player.z, SoundEvents.EXPERIENCE_ORB_PICKUP, SoundSource.PLAYERS, 0.8f, 1.2f)
    }

    private fun playHitSound(mc: Minecraft) {
        val player = mc.player ?: return
        mc.level?.playSound(player, player.x, player.y, player.z, SoundEvents.UI_BUTTON_CLICK.value(), SoundSource.PLAYERS, 0.5f, 1.4f)
    }

    fun clientTick() {
        val mc = Minecraft.getInstance()

        // 1. Process Autonomous / Standalone Key Listeners (No IN port, triggers every click)
        if (mc.screen == null && standaloneListeners.isNotEmpty()) {
            for ((nodeId, listener) in standaloneListeners) {
                val isDown = isTargetKeyDown(listener.targetKey)
                val wasDown = standaloneKeysDown[nodeId] ?: false

                when (listener.inputMode.uppercase()) {
                    "PRESS" -> {
                        if (isDown && !wasDown) {
                            playHitSound(mc)
                            sendResult(listener, "SUCCESS")
                        }
                    }
                    "RELEASE" -> {
                        if (!isDown && wasDown) {
                            playHitSound(mc)
                            sendResult(listener, "SUCCESS")
                        }
                    }
                    "HOLD_STREAM" -> {
                        if (isDown) {
                            val pulseMs = (maxOf(1, listener.pulseIntervalTicks) * 50).toLong()
                            val now = System.currentTimeMillis()
                            val lastPulse = standaloneLastPulseMs[nodeId] ?: 0L
                            if (now - lastPulse >= pulseMs) {
                                standaloneLastPulseMs[nodeId] = now
                                playHitSound(mc)
                                sendResult(listener, "PULSE")
                            }
                        } else if (wasDown) {
                            sendResult(listener, "RELEASED")
                        }
                    }
                    else -> {
                        if (isDown && !wasDown) {
                            playHitSound(mc)
                            sendResult(listener, "SUCCESS")
                        }
                    }
                }
                standaloneKeysDown[nodeId] = isDown
            }
        }

        // 2. Process Sequential QTE Request (Connected via IN port)
        val req = activeRequest ?: return


        // Cancel if player opens menu / chat / inventory
        if (req.cancelOnMenuOpen && mc.screen != null) {
            sendResult(req, "TIMEOUT")
            activeRequest = null
            return
        }

        val deltaSec = 0.05f // 1 tick = 50ms = 0.05s
        elapsedTimeSec += deltaSec

        // Timeout check
        if (req.timeoutSec > 0.0 && elapsedTimeSec >= req.timeoutSec) {
            val player = mc.player
            if (player != null) {
                mc.level?.playSound(player, player.x, player.y, player.z, SoundEvents.NOTE_BLOCK_BASS.value(), SoundSource.PLAYERS, 1.0f, 0.6f)
            }
            sendResult(req, "TIMEOUT")
            activeRequest = null
            return
        }

        val isDown = isTargetKeyDown(req.targetKey)

        when (req.inputMode.uppercase()) {
            "PRESS" -> {
                if (isDown && !wasKeyDown) {
                    playSuccessSound(mc)
                    sendResult(req, "SUCCESS")
                    activeRequest = null
                }
            }
            "HOLD_ONE_SHOT" -> {
                if (isDown) {
                    val duration = maxOf(0.1f, req.holdDurationSec.toFloat())
                    holdProgress += deltaSec / duration
                    if (holdProgress >= 1.0f) {
                        holdProgress = 1.0f
                        playSuccessSound(mc)
                        sendResult(req, "SUCCESS")
                        activeRequest = null
                    }
                } else {
                    // Reset progress on early release
                    holdProgress = 0f
                }
            }
            "HOLD_STREAM" -> {
                if (isDown) {
                    val pulseMs = (maxOf(1, req.pulseIntervalTicks) * 50).toLong()
                    val now = System.currentTimeMillis()
                    if (now - lastPulseTimeMs >= pulseMs) {
                        lastPulseTimeMs = now
                        sendResult(req, "PULSE")
                    }
                    wasKeyDown = true
                } else if (wasKeyDown) {
                    // Key was released after being held
                    sendResult(req, "RELEASED")
                    activeRequest = null
                }
            }
            "RELEASE" -> {
                if (isDown) {
                    hadInitialPress = true
                } else if (hadInitialPress && wasKeyDown) {
                    playSuccessSound(mc)
                    sendResult(req, "SUCCESS")
                    activeRequest = null
                }
            }
            "MASH" -> {
                // Key press hit
                if (isDown && !wasKeyDown) {
                    mashCount += 1.0f
                    mashImpactPulse = 1.0f
                    playHitSound(mc)

                    if (mashCount >= req.mashTargetCount) {
                        playSuccessSound(mc)
                        sendResult(req, "SUCCESS")
                        activeRequest = null
                        return
                    }
                }

                // Decay
                val decay = maxOf(0.0f, req.mashDecayPerSec.toFloat())
                mashCount = (mashCount - decay * deltaSec).coerceAtLeast(0f)
                mashImpactPulse = (mashImpactPulse - 4.0f * deltaSec).coerceAtLeast(0f)
            }
        }

        wasKeyDown = isDown
    }

    fun isTargetKeyDown(keyName: String): Boolean {
        val mc = Minecraft.getInstance()
        val window = mc.window.window
        val clean = keyName.trim().uppercase()

        // Mouse buttons
        if (clean in listOf("LMB", "MOUSE_LEFT", "LEFT_CLICK", "BUTTON_1")) {
            return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_1) == GLFW.GLFW_PRESS
        }
        if (clean in listOf("RMB", "MOUSE_RIGHT", "RIGHT_CLICK", "BUTTON_2")) {
            return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_2) == GLFW.GLFW_PRESS
        }
        if (clean in listOf("MMB", "MOUSE_MIDDLE", "MIDDLE_CLICK", "BUTTON_3")) {
            return GLFW.glfwGetMouseButton(window, GLFW.GLFW_MOUSE_BUTTON_3) == GLFW.GLFW_PRESS
        }

        // Minecraft KeyMappings
        when (clean) {
            "USE", "USE_KEY" -> return mc.options.keyUse.isDown
            "ATTACK", "ATTACK_KEY" -> return mc.options.keyAttack.isDown
            "JUMP", "SPACE" -> return mc.options.keyJump.isDown || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_SPACE)
            "SNEAK", "SHIFT" -> return mc.options.keyShift.isDown || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_SHIFT) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_SHIFT)
            "SPRINT", "CTRL" -> return mc.options.keySprint.isDown || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_LEFT_CONTROL) || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_RIGHT_CONTROL)
            "FORWARD", "W" -> return mc.options.keyUp.isDown || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_W)
            "BACK", "S" -> return mc.options.keyDown.isDown || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_S)
            "LEFT", "A" -> return mc.options.keyLeft.isDown || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_A)
            "RIGHT", "D" -> return mc.options.keyRight.isDown || InputConstants.isKeyDown(window, GLFW.GLFW_KEY_D)
        }

        val glfwCode = resolveGlfwKey(clean)
        return if (glfwCode != GLFW.GLFW_KEY_UNKNOWN) {
            InputConstants.isKeyDown(window, glfwCode)
        } else {
            false
        }
    }

    private fun resolveGlfwKey(name: String): Int {
        if (name.length == 1) {
            val c = name[0]
            if (c in 'A'..'Z') return GLFW.GLFW_KEY_A + (c - 'A')
            if (c in '0'..'9') return GLFW.GLFW_KEY_0 + (c - '0')
        }
        return when (name.removePrefix("KEY_")) {
            "F" -> GLFW.GLFW_KEY_F
            "E" -> GLFW.GLFW_KEY_E
            "Q" -> GLFW.GLFW_KEY_Q
            "R" -> GLFW.GLFW_KEY_R
            "T" -> GLFW.GLFW_KEY_T
            "G" -> GLFW.GLFW_KEY_G
            "C" -> GLFW.GLFW_KEY_C
            "V" -> GLFW.GLFW_KEY_V
            "X" -> GLFW.GLFW_KEY_X
            "Z" -> GLFW.GLFW_KEY_Z
            "SPACE" -> GLFW.GLFW_KEY_SPACE
            "ENTER", "RETURN" -> GLFW.GLFW_KEY_ENTER
            "ESCAPE", "ESC" -> GLFW.GLFW_KEY_ESCAPE
            "TAB" -> GLFW.GLFW_KEY_TAB
            "BACKSPACE" -> GLFW.GLFW_KEY_BACKSPACE
            "LEFT_SHIFT", "LSHIFT" -> GLFW.GLFW_KEY_LEFT_SHIFT
            "RIGHT_SHIFT", "RSHIFT" -> GLFW.GLFW_KEY_RIGHT_SHIFT
            "LEFT_CONTROL", "LCTRL" -> GLFW.GLFW_KEY_LEFT_CONTROL
            "RIGHT_CONTROL", "RCTRL" -> GLFW.GLFW_KEY_RIGHT_CONTROL
            "LEFT_ALT", "LALT" -> GLFW.GLFW_KEY_LEFT_ALT
            "UP" -> GLFW.GLFW_KEY_UP
            "DOWN" -> GLFW.GLFW_KEY_DOWN
            "LEFT" -> GLFW.GLFW_KEY_LEFT
            "RIGHT" -> GLFW.GLFW_KEY_RIGHT
            else -> name.toIntOrNull() ?: GLFW.GLFW_KEY_UNKNOWN
        }
    }

    fun renderHud(guiGraphics: GuiGraphics, font: Font, screenWidth: Int, screenHeight: Int) {
        val req = activeRequest ?: return
        if (!req.showHud) return

        val cx = screenWidth / 2
        val cy = screenHeight / 2

        val mode = req.inputMode.uppercase()
        val keyBadge = "[${req.targetKey.removePrefix("KEY_")}]"
        val prompt = req.promptText.ifBlank {
            when (mode) {
                "HOLD_ONE_SHOT" -> "Hold $keyBadge"
                "HOLD_STREAM" -> "Channel $keyBadge"
                "RELEASE" -> "Release $keyBadge"
                "MASH" -> "Mash $keyBadge rapidly!"
                else -> "Press $keyBadge"
            }
        }

        // 1. Radial Progress Ring around crosshair (for HOLD modes)
        if (mode == "HOLD_ONE_SHOT" || mode == "HOLD_STREAM") {
            val r = 20.0
            val segments = 40
            val activeSegments = (holdProgress * segments).toInt().coerceIn(0, segments)

            // Dim background ring
            for (i in 0 until segments) {
                val angle = (i.toDouble() / segments) * 2 * Math.PI - Math.PI / 2
                val px = (cx + cos(angle) * r).toInt()
                val py = (cy + sin(angle) * r).toInt()
                guiGraphics.fill(px - 1, py - 1, px + 2, py + 2, 0x44FFFFFF)
            }

            // Active Progress ring with glowing Cyan/Emerald
            val ringColor = if (holdProgress >= 0.95f) 0xFF10B981.toInt() else 0xFF38BDF8.toInt()
            for (i in 0..activeSegments) {
                val angle = (i.toDouble() / segments) * 2 * Math.PI - Math.PI / 2
                val px = (cx + cos(angle) * r).toInt()
                val py = (cy + sin(angle) * r).toInt()
                guiGraphics.fill(px - 2, py - 2, px + 2, py + 2, ringColor)
            }
        }

        // 2. Arcade Mash Impact Bar (for MASH mode)
        if (mode == "MASH") {
            val barW = 120
            val barH = 8
            val barX = cx - barW / 2
            val barY = cy + 28

            val maxTarget = maxOf(1, req.mashTargetCount)
            val fillRatio = (mashCount / maxTarget).coerceIn(0f, 1f)
            val fillW = (barW * fillRatio).toInt()

            // Outer Glow & Border
            val pulseOffset = (mashImpactPulse * 2).toInt()
            guiGraphics.fill(barX - 2 - pulseOffset, barY - 2 - pulseOffset, barX + barW + 2 + pulseOffset, barY + barH + 2 + pulseOffset, 0xAA0F172A.toInt())
            guiGraphics.fill(barX - 1, barY - 1, barX + barW + 1, barY + barH + 1, 0xFF38BDF8.toInt())
            guiGraphics.fill(barX, barY, barX + barW, barY + barH, 0xDD1E293B.toInt())

            // Filled bar with color shift from Orange -> Red -> Emerald
            val fillColor = when {
                fillRatio >= 0.8f -> 0xFF10B981.toInt()
                fillRatio >= 0.4f -> 0xFFF59E0B.toInt()
                else -> 0xFFEF4444.toInt()
            }
            if (fillW > 0) {
                guiGraphics.fill(barX, barY, barX + fillW, barY + barH, fillColor)
            }

            // Mash Counter Text
            val mashText = "⚡ MASH! ${mashCount.toInt()}/$maxTarget"
            val textW = font.width(mashText)
            guiGraphics.drawString(font, mashText, cx - textW / 2, barY + 12, 0xFFFFD700.toInt(), true)
        }

        // 3. Prompt Banner Card below crosshair
        val bannerY = if (mode == "MASH") cy + 50 else cy + 28
        val bannerPadding = 8
        val textWidth = font.width(prompt)
        val cardW = textWidth + bannerPadding * 2 + 16
        val cardH = 18
        val cardX = cx - cardW / 2

        guiGraphics.fill(cardX - 1, bannerY - 1, cardX + cardW + 1, bannerY + cardH + 1, 0x8838BDF8.toInt())
        guiGraphics.fill(cardX, bannerY, cardX + cardW, bannerY + cardH, 0xDD0F172A.toInt())

        // Key glyph highlight box
        val keyW = font.width(keyBadge) + 4
        guiGraphics.fill(cardX + 4, bannerY + 2, cardX + 4 + keyW, bannerY + cardH - 2, 0xFF0284C7.toInt())
        guiGraphics.drawString(font, keyBadge, cardX + 6, bannerY + 5, 0xFFFFFFFF.toInt(), false)

        // Custom prompt text
        guiGraphics.drawString(font, prompt, cardX + keyW + 8, bannerY + 5, 0xFFE2E8F0.toInt(), false)

        // 4. Subtle Timeout Countdown Indicator
        if (req.timeoutSec > 0.0) {
            val remainSec = maxOf(0f, req.timeoutSec.toFloat() - elapsedTimeSec)
            val timerText = String.format(java.util.Locale.US, "⏳ %.1fs", remainSec)
            val timerW = font.width(timerText)
            guiGraphics.drawString(font, timerText, cx - timerW / 2, bannerY + cardH + 4, if (remainSec < 2.0f) 0xFFEF4444.toInt() else 0xFF94A3B8.toInt(), false)
        }
    }
}
