package vito.cobblebrain.model

import java.util.UUID

data class ConnectionData(
    val id: String = UUID.randomUUID().toString(),
    val fromNodeId: String,
    val fromPortId: String,
    val toNodeId: String,
    val toPortId: String
)
