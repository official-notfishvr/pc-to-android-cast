package com.pcscreencast.ui.model

data class FsItem(
    val name: String,
    val type: String,
    val size: Long? = null
)

data class FsListResponse(
    val path: String,
    val items: List<FsItem>
)
