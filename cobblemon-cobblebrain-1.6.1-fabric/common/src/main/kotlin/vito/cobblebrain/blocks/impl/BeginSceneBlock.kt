package vito.cobblebrain.blocks.impl

import vito.cobblebrain.blocks.interfaces.ITrigger
import vito.cobblebrain.engine.StoryContext
import vito.cobblebrain.model.NodeData

class BeginSceneBlock : ITrigger {
    override fun evaluate(context: StoryContext, node: NodeData): Boolean {
        return true
    }
}
