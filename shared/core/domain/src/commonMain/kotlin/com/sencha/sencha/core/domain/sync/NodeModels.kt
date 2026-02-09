package com.sencha.sencha.core.domain.sync

import kotlinx.serialization.Serializable

@Serializable
data class NodeInfo(
    val nodeId: String,
    val name: String,
    val address: String,
    val lastSeenEpochMillis: Long?,
)
