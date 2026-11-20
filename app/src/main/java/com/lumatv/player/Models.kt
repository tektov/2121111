package com.lumatv.player

data class PlaylistEntry(
    val name: String,
    val url: String
)

data class Channel(
    val number: Int,
    val name: String,
    val category: String,
    val url: String,
    val tvgId: String = "",
    val tvgName: String = "",
    val logoUrl: String = "",
    val catchupDays: Int = 0,
    val catchupSource: String = "",
    var isFavorite: Boolean = false
)

data class EpgProgram(
    val channelId: String,
    val title: String,
    val description: String,
    val startMs: Long,
    val endMs: Long
) {
    fun isLive(nowMs: Long): Boolean = nowMs in startMs until endMs
    fun isPast(nowMs: Long): Boolean = endMs <= nowMs
    fun durationMinutes(): Long = ((endMs - startMs) / 60000L).coerceAtLeast(1L)

    fun progressPercent(nowMs: Long): Int {
        if (nowMs <= startMs) return 0
        if (nowMs >= endMs) return 100
        val total = (endMs - startMs).coerceAtLeast(1L)
        return (((nowMs - startMs) * 100L) / total).toInt().coerceIn(0, 100)
    }
}

data class UiThemePreset(
    val name: String,
    val bgStartColor: Int,
    val bgEndColor: Int,
    val tileStartColor: Int,
    val tileEndColor: Int,
    val focusStartColor: Int,
    val focusEndColor: Int,
    val accentColor: Int,
    val defaultWallpaperIdx: Int
)

data class NeonGlowPreset(
    val name: String,
    val borderColor: Int,
    val shadowColor: Int,
    val strokeWidthDp: Float,
    val elevationDp: Float
)
