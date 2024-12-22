package com.example.grad_project2

data class GraphNode (
    val deviceId : String,
    val connections : MutableList<GraphNode> = mutableListOf()
)