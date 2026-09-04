package vito.cobblebrain.client

import vito.cobblebrain.engine.StoryExecutor
import vito.cobblebrain.network.CobblebrainPayloads

object StoryControlClient {
    var sendControlRequest: ((CobblebrainPayloads.StoryControlRequestPayload) -> Unit)? = null

    fun start(storyId: String) {
        sendControlRequest?.invoke(CobblebrainPayloads.StoryControlRequestPayload(storyId, "START"))
    }

    fun pause(storyId: String) {
        StoryExecutor.pauseStory(storyId)
        sendControlRequest?.invoke(CobblebrainPayloads.StoryControlRequestPayload(storyId, "PAUSE"))
    }

    fun resume(storyId: String) {
        StoryExecutor.resumeStory(storyId)
        sendControlRequest?.invoke(CobblebrainPayloads.StoryControlRequestPayload(storyId, "RESUME"))
    }

    fun stop(storyId: String) {
        StoryExecutor.stopStory(storyId)
        vito.cobblebrain.engine.StoryDebugger.clearNodeStatuses(storyId)
        sendControlRequest?.invoke(CobblebrainPayloads.StoryControlRequestPayload(storyId, "STOP"))
    }
}

