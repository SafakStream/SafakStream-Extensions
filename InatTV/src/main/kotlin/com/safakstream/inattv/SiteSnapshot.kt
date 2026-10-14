package com.safakstream.inattv

data class SiteSnapshot(
    val url: String,
    val channels: List<SportsChannel>,
    val config: PlayerConfig,
    val checkedAt: Long
)
