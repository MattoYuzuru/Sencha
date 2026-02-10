package com.sencha.sencha.core.domain

import kotlinx.coroutines.flow.StateFlow

interface ArtifactRepository {
    fun artifacts(): StateFlow<List<Artifact>>

    fun find(id: ArtifactId): Artifact?

    fun snapshot(): List<Artifact>

    fun createTextArtifact(request: CreateTextArtifactRequest): Artifact

    fun createAudioArtifact(request: CreateAudioArtifactRequest): Artifact

    fun refreshFromStore()
}
