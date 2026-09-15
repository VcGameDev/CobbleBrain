package vito.cobblebrain.blocks.interfaces

import vito.cobblebrain.engine.StoryContext
import vito.cobblebrain.model.NodeData

interface ITrigger {
    fun evaluate(context: StoryContext, node: NodeData): Boolean
}

interface IAction {
    fun execute(context: StoryContext, node: NodeData)
}
