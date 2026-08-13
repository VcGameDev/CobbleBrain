package vito.cobblebrain.blocks.impl

import vito.cobblebrain.blocks.interfaces.IAction
import vito.cobblebrain.engine.StoryContext
import vito.cobblebrain.model.NodeData

class EndSceneBlock : IAction {
    override fun execute(context: StoryContext, node: NodeData) {
        // Bloco de finalização de cena acionado pelo motor StoryExecutor
    }
}
