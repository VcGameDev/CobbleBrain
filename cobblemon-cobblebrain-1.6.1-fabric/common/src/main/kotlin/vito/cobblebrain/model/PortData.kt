package vito.cobblebrain.model

import java.util.UUID

enum class PortType {
    INPUT,
    OUTPUT
}

data class PortData(
    val id: String = UUID.randomUUID().toString(),
    var name: String = "Porta",
    var type: PortType = PortType.OUTPUT,
    var dataType: String = "flow"
)
