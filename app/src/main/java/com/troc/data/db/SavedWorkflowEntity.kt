package com.troc.data.db

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "workflows")
data class SavedWorkflowEntity(
    @PrimaryKey val id: String,
    val name: String,
    val stepsJson: String, // JSON serialized list of WorkflowStep
    val createdAt: Long
)
