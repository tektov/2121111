package com.lumatv.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.StateSet
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread

class MainActivity : AppCompatActivity() {

    companion object {
        const val DEFAULT_PLAYLIST_NAME = "VipLime"
        const val DEFAULT_PLAYLIST_URL = "http://cdntv.online/high/c6t3h5p9vr/playlist.m3u8"
        const val EPG_LITE_URL = "http://epg.cdntv.online/lite.xml.gz"
        const val EPG_FULL_URL = "http://epg.cdntv.online/full.xml.gz"
    }

    private enum class Screen {
        CATEGORIES,
        CATEGORY_CHANNELS,
        PLAYER,
        SETTINGS
    }

    private var currentScreen = Screen.CATEGORIES

    private lateinit var ivGlobalWallpaper: ImageView
    private lateinit var screenCategories: LinearLayout
    private lateinit var topBarCategories: LinearLayout
    private lateinit var screenCategoryChannels: LinearLayout
    private lateinit var topBarChannels: LinearLayout
    private lateinit var screenPlayer: FrameLayout
    private lateinit var screenSettings: LinearLayout
    private lateinit var topBarSettings: LinearLayout

    // Экран 1: Категории
    private lateinit var tvAppLogoTitle: TextView
    private lateinit var btnPlaylistSelectorTop: Button
    private lateinit var btnSearchMain: Button
    private lateinit var btnRefreshMain: Button
    private lateinit var btnSettingsMain: Button
    private lateinit var rvCategories: RecyclerView

    // Экран 2: Каналы категории + окно предпросмотра при наведении
    private lateinit var btnBackFromCategory: Button
    private lateinit var tvCategoryHeaderTitle: TextView
    private lateinit var btnSearchCategory: Button
    private lateinit var rvCategoryChannels: RecyclerView
    private lateinit var hoverPreviewCard: LinearLayout
    private lateinit var hoverPlayerView: PlayerView
    private lateinit var tvHoverChannelName: TextView
    private lateinit var tvHoverShowTitle: TextView
    private lateinit var pbHoverProgress: ProgressBar
    private lateinit var tvHoverShowDesc: TextView

    // Экран 3: Плеер
    private lateinit var playerView: PlayerView
    private lateinit var tvClockOverlay: TextView
    private lateinit var miniZapBanner: LinearLayout
    private lateinit var tvZapChannelName: TextView
    private lateinit var tvZapShowName: TextView
    private lateinit var playerControlBar: LinearLayout
    private lateinit var playerRightPanel: LinearLayout
    private lateinit var tabLiveProgram: Button
    private lateinit var tabArchiveProgram: Button
    private lateinit var rvPlayerEpg: RecyclerView

    private lateinit var tvOsdBadge: TextView
    private lateinit var ivOsdLogo: ImageView
    private lateinit var tvOsdChannelName: TextView
    private lateinit var tvOsdCurrentShow: TextView
    private lateinit var tvOsdNextShow: TextView
    private lateinit var tvOsdTimeRight: TextView
    private lateinit var osdSeekBar: SeekBar

    private lateinit var btnOsdFullscreen: Button
    private lateinit var btnOsdRewind: Button
    private lateinit var btnOsdPlayPause: Button
    private lateinit var btnOsdForward: Button
    private lateinit var btnOsdQuickChannels: Button
    private lateinit var btnOsdFavorite: Button
    private lateinit var btnOsdPip: Button
    private lateinit var btnOsdAspect: Button
    private lateinit var btnOsdQuality: Button
    private lateinit var btnOsdInfo: Button

    // Мини-окно PiP
    private lateinit var floatingPipContainer: FrameLayout
    private lateinit var pipPlayerView: PlayerView
    private lateinit var tvPipTitle: TextView
    private lateinit var btnPipExpand: Button
    private lateinit var btnPipClose: Button

    // Оверлеи
    private lateinit var quickChannelOverlay: LinearLayout
    private lateinit var tvQuickCategoryTitle: TextView
    private lateinit var rvQuickChannels: RecyclerView

    private lateinit var programInfoOverlay: LinearLayout
    private lateinit var tvInfoShowTitle: TextView
    private lateinit var tvInfoStreamSpecs: TextView
    private lateinit var tvInfoShowDescription: TextView

    // Настройки
    private lateinit var btnCloseSettings: Button
    private lateinit var btnSetColorTheme: Button
    private lateinit var btnSetNeonGlow: Button
    private lateinit var opacitySliderCard: LinearLayout
    private lateinit var tvOpacityPercent: TextView
    private lateinit var seekBarGlassOpacity: SeekBar
    private lateinit var btnSetWallpaper: Button
    private lateinit var btnSetPlaylists: Button
    private lateinit var btnSetEpg: Button
    private lateinit var btnSetVideoAudio: Button
    private lateinit var btnSetBuffer: Button
    private lateinit var btnSetRefreshPlaylist: Button
    private lateinit var btnSetRefreshEpg: Button

    private lateinit var player: ExoPlayer

    private val playlists = mutableListOf<PlaylistEntry>()
    private var activePlaylistIndex = 0

    private var allChannels: List<Channel> = emptyList()
    private var categories: List<Pair<String, List<Channel>>> = emptyList()
    private var epgMap: Map<String, List<EpgProgram>> = emptyMap()
    private var epgIconsMap: Map<String, String> = emptyMap()

    private var currentCategoryName: String = ""
    private var currentCategoryChannels: List<Channel> = emptyList()
    private var quickBrowseCategoryIndex: Int = 0
    private var activeChannel: Channel? = null
    private var hoveredChannel: Channel? = null
    private var activeArchiveProgram: EpgProgram? = null
    private var archiveOffsetSeconds: Long = 0L
    private var isArchiveTab = false
    private var isFullscreenMode = false
    private var isInAppPipActive = false
    private var resizeModeIndex = 0
    private var selectedMaxHeight = 2160
    private var bufferSeconds = 10
    private var glassAlphaPercent = 85
    private var wallpaperPresetIndex = 0
    private var customWallpaperUrl = ""
    private var themePresetIndex = 0
    private var neonGlowIndex = 1

    private val themePresets = listOf(
        UiThemePreset(
            name = "Королевский синий (Liquid Glass)",
            bgStartColor = Color.parseColor("#CC1F335E"),
            bgEndColor = Color.parseColor("#B80E172E"),
            tileStartColor = Color.parseColor("#B3243B6E"),
            tileEndColor = Color.parseColor("#99122042"),
            focusStartColor = Color.parseColor("#EEB6E0FE"),
            focusEndColor = Color.parseColor("#D8C2DF96"),
            accentColor = Color.parseColor("#38BDF8"),
            defaultWallpaperIdx = 0
        ),
        UiThemePreset(
            name = "Кибер-фиолет (Ambilight Violet)",
            bgStartColor = Color.parseColor("#CC3B1D5E"),
            bgEndColor = Color.parseColor("#B8150A26"),
            tileStartColor = Color.parseColor("#B3321852"),
            tileEndColor = Color.parseColor("#99170B29"),
            focusStartColor = Color.parseColor("#EEF0ABFC"),
            focusEndColor = Color.parseColor("#D8C084FC"),
            accentColor = Color.parseColor("#E879F9"),
            defaultWallpaperIdx = 1
        ),
        UiThemePreset(
            name = "Изумрудный шторм (Emerald Glass)",
            bgStartColor = Color.parseColor("#CC13464C"),
            bgEndColor = Color.parseColor("#B8071D22"),
            tileStartColor = Color.parseColor("#B3113E44"),
            tileEndColor = Color.parseColor("#99081D21"),
            focusStartColor = Color.parseColor("#EE99F6E4"),
            focusEndColor = Color.parseColor("#D85EEAD4"),
            accentColor = Color.parseColor("#2DD4BF"),
            defaultWallpaperIdx = 2
        ),
        UiThemePreset(
            name = "Золотой обсидиан (OLED Gold)",
            bgStartColor = Color.parseColor("#CC262221"),
            bgEndColor = Color.parseColor("#B80B0908"),
            tileStartColor = Color.parseColor("#B3221F1D"),
            tileEndColor = Color.parseColor("#99100E0D"),
            focusStartColor = Color.parseColor("#EEFDE68A"),
            focusEndColor = Color.parseColor("#D8F59E0B"),
            accentColor = Color.parseColor("#FBBF24"),
            defaultWallpaperIdx = 3
        ),
        UiThemePreset(
            name = "Рубиновый закат (Crimson Glass)",
            bgStartColor = Color.parseColor("#CC521729"),
            bgEndColor = Color.parseColor("#B81C070E"),
            tileStartColor = Color.parseColor("#B3451222"),
            tileEndColor = Color.parseColor("#991C070D"),
            focusStartColor = Color.parseColor("#EEFECDD3"),
            focusEndColor = Color.parseColor("#D8FDA4AF"),
            accentColor = Color.parseColor("#FB7185"),
            defaultWallpaperIdx = 1
        )
    )

    // МЯГКОЕ ЗАДНЕЕ СВЕЧЕНИЕ AMBILIGHT ИЗ-ПОД СТЕКЛА (БЕЗ РЕЗКИХ КОНТУРНЫХ РАМОК)
    private val neonGlowPresets = listOf(
        NeonGlowPreset("Выключено (Чистое стекло)", Color.TRANSPARENT, 0f),
        NeonGlowPreset("Мягкий голубой Ambilight", Color.parseColor("#FF38BDF8"), 18f),
        NeonGlowPreset("Мягкий фиолетовый Ambilight", Color.parseColor("#FFC084FC"), 18f),
        NeonGlowPreset("Мягкий изумрудный Ambilight", Color.parseColor("#FF34D399"), 18f),
        NeonGlowPreset("Тёплый янтарный Ambilight", Color.parseColor("#FFFBBF24"), 18f),
        NeonGlowPreset("Лунный белый Ambilight", Color.parseColor("#FFE0F2FE"), 20f)
    )

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd MMM / HH:mm", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())

    private val hoverPlayRunnable = Runnable {
        val ch = hoveredChannel ?: return@Runnable
        if (currentScreen == Screen.CATEGORY_CHANNELS && !isInAppPipActive) {
            hoverPlayerView.player = player
            playStream(ch.url)
        }
    }

    private val hideMiniZapRunnable = Runnable {
        miniZapBanner.visibility = View.GONE
    }

    private val autoHidePreviewRunnable = Runnable {
        if (currentScreen == Screen.PLAYER && !isFullscreenMode && quickChannelOverlay.visibility != View.VISIBLE && programInfoOverlay.visibility != View.VISIBLE) {
            enterFullscreenMode()
        }
    }

    private val hideFullControlsRunnable = Runnable {
        if (isFullscreenMode && quickChannelOverlay.visibility != View.VISIBLE && programInfoOverlay.visibility != View.VISIBLE) {
            animateControlBarOut()
        }
    }

    private val clockRunnable = object : Runnable {
        override fun run() {
            tvClockOverlay.text = timeFormat.format(Date())
            updateArchiveProgressUi()
            handler.postDelayed(this, 5000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        loadSavedPlaylistsAndSettings()
        initPlayerWithBuffer(bufferSeconds)
        setupListeners()
        applyThemeAndNeonVisuals()
        applyGlassOpacity(glassAlphaPercent)
        applyWallpaperSetting()

        handler.post(clockRunnable)

        showCategoriesScreen()
        loadActivePlaylistAndEpg()
    }

    private fun initPlayerWithBuffer(bufSec: Int) {
        if (::player.isInitialized) {
            player.release()
        }
        val minBufMs = (bufSec * 1000).coerceAtLeast(2000)
        val maxBufMs = (bufSec * 2500).coerceAtLeast(10000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBufMs, maxBufMs, 1500, 3000)
            .build()

        player = ExoPlayer.Builder(this)
            .setLoadControl(loadControl)
            .build()
        applyVideoQualityConstraint()
        playerView.player = player
    }

    private fun bindViews() {
        ivGlobalWallpaper = findViewById(R.id.ivGlobalWallpaper)
        screenCategories = findViewById(R.id.screenCategories)
        topBarCategories = findViewById(R.id.topBarCategories)
        screenCategoryChannels = findViewById(R.id.screenCategoryChannels)
        topBarChannels = findViewById(R.id.topBarChannels)
        screenPlayer = findViewById(R.id.screenPlayer)
        screenSettings = findViewById(R.id.screenSettings)
        topBarSettings = findViewById(R.id.topBarSettings)

        tvAppLogoTitle = findViewById(R.id.tvAppLogoTitle)
        btnPlaylistSelectorTop = findViewById(R.id.btnPlaylistSelectorTop)
        btnSearchMain = findViewById(R.id.btnSearchMain)
        btnRefreshMain = findViewById(R.id.btnRefreshMain)
        btnSettingsMain = findViewById(R.id.btnSettingsMain)
        rvCategories = findViewById(R.id.rvCategories)
        rvCategories.layoutManager = GridLayoutManager(this, 2)

        btnBackFromCategory = findViewById(R.id.btnBackFromCategory)
        tvCategoryHeaderTitle = findViewById(R.id.tvCategoryHeaderTitle)
        btnSearchCategory = findViewById(R.id.btnSearchCategory)
        rvCategoryChannels = findViewById(R.id.rvCategoryChannels)
        rvCategoryChannels.layoutManager = GridLayoutManager(this, 2)

        hoverPreviewCard = findViewById(R.id.hoverPreviewCard)
        hoverPlayerView = findViewById(R.id.hoverPlayerView)
        tvHoverChannelName = findViewById(R.id.tvHoverChannelName)
        tvHoverShowTitle = findViewById(R.id.tvHoverShowTitle)
        pbHoverProgress = findViewById(R.id.pbHoverProgress)
        tvHoverShowDesc = findViewById(R.id.tvHoverShowDesc)

        playerView = findViewById(R.id.playerView)
        tvClockOverlay = findViewById(R.id.tvClockOverlay)
        miniZapBanner = findViewById(R.id.miniZapBanner)
        tvZapChannelName = findViewById(R.id.tvZapChannelName)
        tvZapShowName = findViewById(R.id.tvZapShowName)
        playerControlBar = findViewById(R.id.playerControlBar)
        playerRightPanel = findViewById(R.id.playerRightPanel)
        tabLiveProgram = findViewById(R.id.tabLiveProgram)
        tabArchiveProgram = findViewById(R.id.tabArchiveProgram)
        rvPlayerEpg = findViewById(R.id.rvPlayerEpg)
        rvPlayerEpg.layoutManager = LinearLayoutManager(this)

        tvOsdBadge = findViewById(R.id.tvOsdBadge)
        ivOsdLogo = findViewById(R.id.ivOsdLogo)
        tvOsdChannelName = findViewById(R.id.tvOsdChannelName)
        tvOsdCurrentShow = findViewById(R.id.tvOsdCurrentShow)
        tvOsdNextShow = findViewById(R.id.tvOsdNextShow)
        tvOsdTimeRight = findViewById(R.id.tvOsdTimeRight)
        osdSeekBar = findViewById(R.id.osdSeekBar)

        btnOsdFullscreen = findViewById(R.id.btnOsdFullscreen)
        btnOsdRewind = findViewById(R.id.btnOsdRewind)
        btnOsdPlayPause = findViewById(R.id.btnOsdPlayPause)
        btnOsdForward = findViewById(R.id.btnOsdForward)
        btnOsdQuickChannels = findViewById(R.id.btnOsdQuickChannels)
        btnOsdFavorite = findViewById(R.id.btnOsdFavorite)
        btnOsdPip = findViewById(R.id.btnOsdPip)
        btnOsdAspect = findViewById(R.id.btnOsdAspect)
        btnOsdQuality = findViewById(R.id.btnOsdQuality)
        btnOsdInfo = findViewById(R.id.btnOsdInfo)

        floatingPipContainer = findViewById(R.id.floatingPipContainer)
        pipPlayerView = findViewById(R.id.pipPlayerView)
        tvPipTitle = findViewById(R.id.tvPipTitle)
        btnPipExpand = findViewById(R.id.btnPipExpand)
        btnPipClose = findViewById(R.id.btnPipClose)

        quickChannelOverlay = findViewById(R.id.quickChannelOverlay)
        tvQuickCategoryTitle = findViewById(R.id.tvQuickCategoryTitle)
        rvQuickChannels = findViewById(R.id.rvQuickChannels)
        rvQuickChannels.layoutManager = LinearLayoutManager(this)

        programInfoOverlay = findViewById(R.id.programInfoOverlay)
        tvInfoShowTitle = findViewById(R.id.tvInfoShowTitle)
        tvInfoStreamSpecs = findViewById(R.id.tvInfoStreamSpecs)
        tvInfoShowDescription = findViewById(R.id.tvInfoShowDescription)

        btnCloseSettings = findViewById(R.id.btnCloseSettings)
        btnSetColorTheme = findViewById(R.id.btnSetColorTheme)
        btnSetNeonGlow = findViewById(R.id.btnSetNeonGlow)
        opacitySliderCard = findViewById(R.id.opacitySliderCard)
        tvOpacityPercent = findViewById(R.id.tvOpacityPercent)
        seekBarGlassOpacity = findViewById(R.id.seekBarGlassOpacity)
        btnSetWallpaper = findViewById(R.id.btnSetWallpaper)
        btnSetPlaylists = findViewById(R.id.btnSetPlaylists)
        btnSetEpg = findViewById(R.id.btnSetEpg)
        btnSetVideoAudio = findViewById(R.id.btnSetVideoAudio)
        btnSetBuffer = findViewById(R.id.btnSetBuffer)
        btnSetRefreshPlaylist = findViewById(R.id.btnSetRefreshPlaylist)
        btnSetRefreshEpg = findViewById(R.id.btnSetRefreshEpg)
    }

    private fun setupListeners() {
        btnPlaylistSelectorTop.setOnClickListener { showPlaylistManagerDialog() }
        btnSettingsMain.setOnClickListener {
            switchScreen(Screen.SETTINGS)
            btnSetColorTheme.requestFocus()
        }
        btnCloseSettings.setOnClickListener { showCategoriesScreen() }

        btnSetColorTheme.setOnClickListener { showColorThemeDialog() }
        btnSetNeonGlow.setOnClickListener { showNeonGlowDialog() }

        seekBarGlassOpacity.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                val clamped = progress.coerceIn(25, 100)
                glassAlphaPercent = clamped
                tvOpacityPercent.text = "$clamped%"
                applyGlassOpacity(clamped)
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {
                getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
                    .edit().putInt("glass_alpha_pct", glassAlphaPercent).apply()
            }
        })

        opacitySliderCard.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    val next = (glassAlphaPercent - 5).coerceIn(25, 100)
                    seekBarGlassOpacity.progress = next
                    getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
                        .edit().putInt("glass_alpha_pct", next).apply()
                    return@setOnKeyListener true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    val next = (glassAlphaPercent + 5).coerceIn(25, 100)
                    seekBarGlassOpacity.progress = next
                    getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
                        .edit().putInt("glass_alpha_pct", next).apply()
                    return@setOnKeyListener true
                }
            }
            false
        }

        btnSetWallpaper.setOnClickListener { showWallpaperSelectorDialog() }

        btnRefreshMain.setOnClickListener { loadActivePlaylistAndEpg() }
        btnSetRefreshPlaylist.setOnClickListener { loadActivePlaylistAndEpg(); showCategoriesScreen() }
        btnSetRefreshEpg.setOnClickListener { loadActivePlaylistAndEpg(); showCategoriesScreen() }

        btnSearchMain.setOnClickListener { showSearchDialog() }
        btnSearchCategory.setOnClickListener { showSearchDialog() }

        btnSetPlaylists.setOnClickListener { showPlaylistManagerDialog() }
        btnSetEpg.setOnClickListener { showEpgSelectorDialog() }
        btnSetVideoAudio.setOnClickListener { showVideoAudioMenuDialog() }
        btnSetBuffer.setOnClickListener { showBufferDialog() }

        btnBackFromCategory.setOnClickListener {
            if (!isInAppPipActive) {
                player.stop()
            }
            hoverPlayerView.player = null
            showCategoriesScreen()
        }

        tabLiveProgram.setOnClickListener { selectPlayerTab(archive = false) }
        tabArchiveProgram.setOnClickListener { selectPlayerTab(archive = true) }

        btnOsdFullscreen.setOnClickListener {
            if (isFullscreenMode) {
                enterPreviewMode()
            } else {
                enterFullscreenMode()
            }
        }

        btnOsdRewind.setOnClickListener { seekArchiveBySeconds(-30L) }
        btnOsdForward.setOnClickListener { seekArchiveBySeconds(30L) }

        osdSeekBar.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && activeArchiveProgram != null) {
                if (keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                    seekArchiveBySeconds(-60L)
                    return@setOnKeyListener true
                } else if (keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                    seekArchiveBySeconds(60L)
                    return@setOnKeyListener true
                }
            }
            false
        }

        osdSeekBar.setOnSeekBarChangeListener(object : SeekBar.OnSeekBarChangeListener {
            override fun onProgressChanged(seekBar: SeekBar?, progress: Int, fromUser: Boolean) {
                if (fromUser && activeArchiveProgram != null) {
                    val prog = activeArchiveProgram ?: return
                    val totalSec = ((prog.endMs - prog.startMs) / 1000L).coerceAtLeast(60L)
                    val targetOffset = (totalSec * progress) / 100L
                    seekArchiveToAbsoluteOffset(targetOffset)
                }
            }
            override fun onStartTrackingTouch(seekBar: SeekBar?) {}
            override fun onStopTrackingTouch(seekBar: SeekBar?) {}
        })

        btnOsdPlayPause.setOnClickListener {
            if (player.isPlaying) {
                player.pause()
                btnOsdPlayPause.text = "▶"
            } else {
                player.play()
                btnOsdPlayPause.text = "❚❚"
            }
        }

        btnOsdQuickChannels.setOnClickListener {
            if (!isFullscreenMode) enterFullscreenMode()
            openQuickChannelList()
        }

        btnOsdFavorite.setOnClickListener {
            activeChannel?.let { ch ->
                ch.isFavorite = !ch.isFavorite
                saveFavorites()
                rebuildCategories()
                btnOsdFavorite.setTextColor(if (ch.isFavorite) Color.parseColor("#FFD166") else Color.WHITE)
                Toast.makeText(this, if (ch.isFavorite) "Добавлено в Избранное" else "Удалено из Избранного", Toast.LENGTH_SHORT).show()
            }
        }

        btnOsdPip.setOnClickListener { startInAppPictureInPicture() }
        btnPipExpand.setOnClickListener { expandFromInAppPip() }
        btnPipClose.setOnClickListener { closeInAppPip(stopPlayback = true) }

        // НАВИГАЦИЯ ВЛЕВО ИЗ ВСПЛЫВШЕГО ОКНА PiP ВОЗВРАЩАЕТ ФОКУС НА ОСНОВНОЙ ЭКРАН!
        btnPipExpand.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                returnFocusFromPipToMainScreen()
                return@setOnKeyListener true
            }
            false
        }

        btnOsdAspect.setOnClickListener { showAspectRatioDialog() }
        btnOsdQuality.setOnClickListener { showVideoAudioMenuDialog() }

        btnOsdInfo.setOnClickListener {
            if (!isFullscreenMode) enterFullscreenMode()
            openProgramInfoCard()
        }

        playerView.setOnClickListener {
            if (isFullscreenMode) {
                if (playerControlBar.visibility == View.VISIBLE) {
                    animateControlBarOut()
                } else {
                    animateControlBarIn()
                }
            } else {
                enterFullscreenMode()
            }
        }
    }

    private fun returnFocusFromPipToMainScreen() {
        when (currentScreen) {
            Screen.CATEGORIES -> rvCategories.requestFocus()
            Screen.CATEGORY_CHANNELS -> rvCategoryChannels.requestFocus()
            Screen.SETTINGS -> btnSetColorTheme.requestFocus()
            Screen.PLAYER -> playerView.requestFocus()
        }
    }

    // ОБНОВЛЕНИЕ ОКНА ПРЕДПРОСМОТРА ПРИ НАВЕДЕНИИ ПУЛЬТОМ НА КАНАЛ В СЕТКЕ (ФОТО 2)
    private fun onChannelHovered(ch: Channel) {
        hoveredChannel = ch
        val now = System.currentTimeMillis()
        val live = getLiveProgram(ch)

        tvHoverChannelName.text = ch.name
        tvHoverShowTitle.text = live?.title ?: "Прямой эфир"
        pbHoverProgress.progress = live?.progressPercent(now) ?: 100
        tvHoverShowDesc.text = live?.description?.takeIf { it.isNotBlank() }
            ?: "Нажмите ОК на пульте, чтобы открыть полноэкранный просмотр или архив передач."

        if (!isInAppPipActive) {
            handler.removeCallbacks(hoverPlayRunnable)
            handler.postDelayed(hoverPlayRunnable, 450L)
        }
    }

    private fun seekArchiveBySeconds(deltaSec: Long) {
        val ch = activeChannel ?: return
        val prog = activeArchiveProgram ?: return
        val totalSec = ((prog.endMs - prog.startMs) / 1000L).coerceAtLeast(60L)

        if (player.isCurrentMediaItemSeekable && player.duration > 0) {
            val targetMs = (player.currentPosition + deltaSec * 1000L).coerceIn(0L, player.duration)
            player.seekTo(targetMs)
            archiveOffsetSeconds = (targetMs / 1000L).coerceIn(0L, totalSec)
        } else {
            val newOffset = (archiveOffsetSeconds + deltaSec).coerceIn(0L, (totalSec - 15L).coerceAtLeast(0L))
            archiveOffsetSeconds = newOffset
            val url = Parsers.buildArchiveUrl(ch, prog, newOffset)
            playStream(url)
        }

        updateArchiveProgressUi()
        val sign = if (deltaSec >= 0) "+" else ""
        showMiniZapNotification(
            title = "Архив • ${ch.name}",
            subtitle = "Перемотка ${sign}${deltaSec}с (${formatSecondsMmSs(archiveOffsetSeconds)} / ${formatSecondsMmSs(totalSec)})"
        )
    }

    private fun seekArchiveToAbsoluteOffset(targetOffsetSec: Long) {
        val ch = activeChannel ?: return
        val prog = activeArchiveProgram ?: return
        val totalSec = ((prog.endMs - prog.startMs) / 1000L).coerceAtLeast(60L)
        val clamped = targetOffsetSec.coerceIn(0L, (totalSec - 15L).coerceAtLeast(0L))
        archiveOffsetSeconds = clamped

        if (player.isCurrentMediaItemSeekable && player.duration > 0) {
            val pct = (clamped * 100L) / totalSec
            player.seekTo((player.duration * pct) / 100L)
        } else {
            val url = Parsers.buildArchiveUrl(ch, prog, clamped)
            playStream(url)
        }
        updateArchiveProgressUi()
    }

    private fun updateArchiveProgressUi() {
        val prog = activeArchiveProgram ?: return
        val totalSec = ((prog.endMs - prog.startMs) / 1000L).coerceAtLeast(60L)
        val posSec = if (player.isCurrentMediaItemSeekable && player.duration > 0) {
            (player.currentPosition / 1000L).coerceIn(0L, totalSec)
        } else {
            archiveOffsetSeconds
        }
        val pct = ((posSec * 100L) / totalSec).toInt().coerceIn(0, 100)
        osdSeekBar.progress = pct
        tvOsdTimeRight.text = "${formatSecondsMmSs(posSec)} / ${formatSecondsMmSs(totalSec)}\nАрхив"
    }

    private fun formatSecondsMmSs(sec: Long): String {
        val m = sec / 60L
        val s = sec % 60L
        return String.format(Locale.US, "%02d:%02d", m, s)
    }

    private fun showMiniZapNotification(title: String, subtitle: String) {
        tvZapChannelName.text = title
        tvZapShowName.text = subtitle
        miniZapBanner.visibility = View.VISIBLE
        handler.removeCallbacks(hideMiniZapRunnable)
        handler.postDelayed(hideMiniZapRunnable, 2200L)
    }

    private fun showColorThemeDialog() {
        val names = themePresets.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Цветовая тема интерфейса")
            .setSingleChoiceItems(names, themePresetIndex) { dialog, which ->
                themePresetIndex = which
                val theme = themePresets[which]
                if (wallpaperPresetIndex != 4) {
                    wallpaperPresetIndex = theme.defaultWallpaperIdx
                }
                getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE).edit()
                    .putInt("theme_idx", themePresetIndex)
                    .putInt("wallpaper_idx", wallpaperPresetIndex)
                    .apply()
                applyThemeAndNeonVisuals()
                applyWallpaperSetting()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showNeonGlowDialog() {
        val names = neonGlowPresets.map { it.name }.toTypedArray()
        AlertDialog.Builder(this)
            .setTitle("Свечение Ambilight под окнами")
            .setSingleChoiceItems(names, neonGlowIndex) { dialog, which ->
                neonGlowIndex = which
                getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE).edit()
                    .putInt("neon_glow_idx", neonGlowIndex)
                    .apply()
                applyThemeAndNeonVisuals()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    // СТЕКЛЯННЫЕ ОКНА БЕЗ РЕЗКИХ КОНТУРОВ С МЯГКИМ СВЕЧЕНИЕМ AMBILIGHT ИЗ-ПОД НИХ
    private fun applyThemeAndNeonVisuals() {
        val theme = themePresets.getOrElse(themePresetIndex) { themePresets[0] }
        val ambilight = neonGlowPresets.getOrElse(neonGlowIndex) { neonGlowPresets[1] }

        btnSetColorTheme.text = "Цветовая тема интерфейса: ${theme.name}"
        btnSetNeonGlow.text = "Свечение Ambilight под окнами: ${ambilight.name}"

        tvAppLogoTitle.setTextColor(theme.accentColor)
        tvOpacityPercent.setTextColor(theme.accentColor)
        osdSeekBar.progressTintList = ColorStateList.valueOf(theme.accentColor)
        osdSeekBar.thumbTintList = ColorStateList.valueOf(theme.accentColor)
        pbHoverProgress.progressTintList = ColorStateList.valueOf(theme.accentColor)

        val panels = listOf(
            topBarCategories,
            topBarChannels,
            topBarSettings,
            hoverPreviewCard,
            playerControlBar,
            playerRightPanel,
            quickChannelOverlay,
            programInfoOverlay,
            miniZapBanner,
            floatingPipContainer
        )
        for (panel in panels) {
            panel.background = buildBorderlessGlassPanel(theme)
            applyAmbilightGlow(panel, ambilight)
        }

        val settingsRows = listOf(
            opacitySliderCard,
            btnSetColorTheme,
            btnSetNeonGlow,
            btnSetWallpaper,
            btnSetPlaylists,
            btnSetEpg,
            btnSetVideoAudio,
            btnSetBuffer,
            btnSetRefreshPlaylist,
            btnSetRefreshEpg
        )
        for (row in settingsRows) {
            row.background = buildBorderlessGlassTile(theme, radiusDp = 14f)
            applyAmbilightGlow(row, ambilight)
        }

        rvCategories.adapter?.notifyDataSetChanged()
        rvCategoryChannels.adapter?.notifyDataSetChanged()
        rvPlayerEpg.adapter?.notifyDataSetChanged()
        rvQuickChannels.adapter?.notifyDataSetChanged()
    }

    private fun buildBorderlessGlassPanel(theme: UiThemePreset): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(theme.bgStartColor, theme.bgEndColor)
        ).apply {
            cornerRadius = dpToPx(20).toFloat()
            // Без жёсткой контурной рамки — чистое стекло
            setStroke(0, Color.TRANSPARENT)
        }
    }

    private fun buildBorderlessGlassTile(theme: UiThemePreset, radiusDp: Float = 16f): StateListDrawable {
        val r = dpToPxFloat(radiusDp)
        val focusedDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(theme.focusStartColor, theme.focusEndColor)
        ).apply {
            cornerRadius = r
            setStroke(0, Color.TRANSPARENT)
        }

        val normalDrawable = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(theme.tileStartColor, theme.tileEndColor)
        ).apply {
            cornerRadius = r
            setStroke(0, Color.TRANSPARENT)
        }

        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focusedDrawable)
            addState(StateSet.WILD_CARD, normalDrawable)
        }
    }

    private fun applyAmbilightGlow(view: View, ambilight: NeonGlowPreset) {
        val elevPx = dpToPxFloat(ambilight.elevationDp)
        view.elevation = elevPx
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
            if (ambilight.shadowColor != Color.TRANSPARENT) {
                view.outlineAmbientShadowColor = ambilight.shadowColor
                view.outlineSpotShadowColor = ambilight.shadowColor
            } else {
                view.outlineAmbientShadowColor = Color.TRANSPARENT
                view.outlineSpotShadowColor = Color.TRANSPARENT
            }
        }
    }

    private fun applyGlassOpacity(percent: Int) {
        val alphaFloat = (percent / 100f).coerceIn(0.25f, 1.0f)
        playerControlBar.alpha = alphaFloat
        playerRightPanel.alpha = alphaFloat
        quickChannelOverlay.alpha = alphaFloat
        programInfoOverlay.alpha = alphaFloat
        hoverPreviewCard.alpha = alphaFloat
        topBarCategories.alpha = alphaFloat
        topBarChannels.alpha = alphaFloat
        rvCategories.alpha = alphaFloat
        rvCategoryChannels.alpha = alphaFloat
    }

    private fun showWallpaperSelectorDialog() {
        val items = arrayOf(
            "Северное сияние (Световые волны)",
            "Вечерний закат (Неоновое сияние)",
            "Изумрудный океан (Северное свечение)",
            "Полночный графит (Глубокий космос)",
            "Загрузить своё фото по ссылке (URL)"
        )
        AlertDialog.Builder(this)
            .setTitle("Выбор фонового изображения")
            .setSingleChoiceItems(items, wallpaperPresetIndex) { dialog, which ->
                if (which == 4) {
                    dialog.dismiss()
                    showCustomWallpaperUrlDialog()
                } else {
                    wallpaperPresetIndex = which
                    customWallpaperUrl = ""
                    getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE).edit()
                        .putInt("wallpaper_idx", wallpaperPresetIndex)
                        .putString("wallpaper_url", "")
                        .apply()
                    applyWallpaperSetting()
                    dialog.dismiss()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showCustomWallpaperUrlDialog() {
        val input = EditText(this).apply {
            hint = "https://example.com/wallpaper.jpg"
            setText(customWallpaperUrl)
            isFocusable = true
        }
        AlertDialog.Builder(this)
            .setTitle("Ссылка на фоновую картинку")
            .setView(input)
            .setPositiveButton("Применить") { _, _ ->
                val url = input.text.toString().trim()
                if (url.isNotEmpty()) {
                    wallpaperPresetIndex = 4
                    customWallpaperUrl = url
                    getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE).edit()
                        .putInt("wallpaper_idx", 4)
                        .putString("wallpaper_url", url)
                        .apply()
                    applyWallpaperSetting()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun applyWallpaperSetting() {
        val presetNames = arrayOf(
            "Северное сияние",
            "Вечерний закат",
            "Изумрудный океан",
            "Полночный графит",
            "Своё фото (URL)"
        )
        btnSetWallpaper.text = "Фоновое изображение: ${presetNames.getOrElse(wallpaperPresetIndex) { "Северное сияние" }}"

        if (wallpaperPresetIndex == 4 && customWallpaperUrl.isNotBlank()) {
            ivGlobalWallpaper.load(customWallpaperUrl) {
                crossfade(true)
                error(R.drawable.bg_wallpaper_aurora)
            }
            return
        }

        val resId = when (wallpaperPresetIndex) {
            1 -> R.drawable.bg_wallpaper_sunset
            2 -> R.drawable.bg_wallpaper_emerald
            3 -> R.drawable.bg_wallpaper_midnight
            else -> R.drawable.bg_wallpaper_aurora
        }
        ivGlobalWallpaper.setImageResource(resId)
    }

    private fun animateControlBarIn() {
        miniZapBanner.visibility = View.GONE
        updateControlBarWidth(isSplitPreview = !isFullscreenMode)

        if (playerControlBar.visibility != View.VISIBLE) {
            playerControlBar.visibility = View.VISIBLE
            playerControlBar.translationY = 260f
            playerControlBar.animate()
                .translationY(0f)
                .setDuration(260L)
                .setInterpolator(DecelerateInterpolator())
                .setListener(null)
                .start()
        }
        if (activeArchiveProgram != null) {
            btnOsdForward.requestFocus()
        } else {
            btnOsdPlayPause.requestFocus()
        }
        if (isFullscreenMode) {
            handler.removeCallbacks(hideFullControlsRunnable)
            handler.postDelayed(hideFullControlsRunnable, 6000L)
        }
    }

    private fun animateControlBarOut() {
        handler.removeCallbacks(hideFullControlsRunnable)
        if (playerControlBar.visibility == View.VISIBLE) {
            playerControlBar.animate()
                .translationY(260f)
                .setDuration(220L)
                .setInterpolator(DecelerateInterpolator())
                .setListener(object : AnimatorListenerAdapter() {
                    override fun onAnimationEnd(animation: Animator) {
                        playerControlBar.visibility = View.GONE
                        playerControlBar.translationY = 0f
                        if (isFullscreenMode) playerView.requestFocus()
                    }
                })
                .start()
        }
    }

    private fun updateControlBarWidth(isSplitPreview: Boolean) {
        val lp = playerControlBar.layoutParams as FrameLayout.LayoutParams
        if (isSplitPreview) {
            lp.marginEnd = dpToPx(462)
        } else {
            lp.marginEnd = dpToPx(16)
        }
        playerControlBar.layoutParams = lp
    }

    private fun dpToPx(dp: Int): Int {
        return (dp * resources.displayMetrics.density).toInt()
    }

    private fun dpToPxFloat(dp: Float): Float {
        return dp * resources.displayMetrics.density
    }

    private fun resetPreviewAutoHideTimer() {
        handler.removeCallbacks(autoHidePreviewRunnable)
        if (currentScreen == Screen.PLAYER && !isFullscreenMode) {
            handler.postDelayed(autoHidePreviewRunnable, 5500L)
        }
    }

    private fun enterPreviewMode() {
        isFullscreenMode = false
        handler.removeCallbacks(hideFullControlsRunnable)
        handler.removeCallbacks(hideMiniZapRunnable)
        miniZapBanner.visibility = View.GONE
        quickChannelOverlay.visibility = View.GONE
        programInfoOverlay.visibility = View.GONE

        playerRightPanel.visibility = View.VISIBLE
        updateControlBarWidth(isSplitPreview = true)
        playerControlBar.translationY = 0f
        playerControlBar.visibility = View.VISIBLE
        btnOsdFullscreen.text = "⛶"

        btnOsdFullscreen.post {
            btnOsdFullscreen.requestFocus()
        }
        resetPreviewAutoHideTimer()
    }

    private fun enterFullscreenMode() {
        isFullscreenMode = true
        handler.removeCallbacks(autoHidePreviewRunnable)
        quickChannelOverlay.visibility = View.GONE
        programInfoOverlay.visibility = View.GONE
        playerRightPanel.visibility = View.GONE

        playerControlBar.visibility = View.GONE
        btnOsdFullscreen.text = "❐"
        playerView.requestFocus()
    }

    private fun startInAppPictureInPicture() {
        val ch = activeChannel ?: return
        isInAppPipActive = true
        handler.removeCallbacks(autoHidePreviewRunnable)
        handler.removeCallbacks(hoverPlayRunnable)
        playerView.player = null
        hoverPlayerView.player = null
        pipPlayerView.player = player
        pipPlayerView.resizeMode = playerView.resizeMode
        tvPipTitle.text = ch.name
        floatingPipContainer.visibility = View.VISIBLE

        showCategoriesScreen()
        Toast.makeText(this, "Мини-окно активно. Нажмите ▶ вправо с правой колонки, чтобы управлять окном", Toast.LENGTH_SHORT).show()
    }

    private fun expandFromInAppPip() {
        isInAppPipActive = false
        floatingPipContainer.visibility = View.GONE
        pipPlayerView.player = null
        playerView.player = player
        activeChannel?.let { ch ->
            switchScreen(Screen.PLAYER)
            updateOsdInfo(ch, activeArchiveProgram)
            enterFullscreenMode()
        }
    }

    private fun closeInAppPip(stopPlayback: Boolean) {
        isInAppPipActive = false
        floatingPipContainer.visibility = View.GONE
        pipPlayerView.player = null
        playerView.player = player
        if (stopPlayback) {
            player.stop()
        }
        returnFocusFromPipToMainScreen()
    }

    private fun showVideoAudioMenuDialog() {
        val aspectNames = arrayOf("По размеру экрана (Fit)", "Растянуть на весь экран (Fill)", "Увеличение без полос (Zoom)", "Фиксированный 16:9", "Фиксированный 4:3")
        val qualityLabel = when (selectedMaxHeight) {
            2160 -> "До 4K (2160p — максимум)"
            1080 -> "Full HD (до 1080p)"
            720 -> "HD (до 720p — минимум)"
            else -> "Авто"
        }
        val currentAudio = player.audioFormat?.language?.uppercase(Locale.getDefault()) ?: "По умолчанию"

        val mainItems = arrayOf(
            "1. Масштаб экрана: ${aspectNames[resizeModeIndex]}",
            "2. Качество видео: $qualityLabel",
            "3. Выбор аудиодорожки (сейчас: $currentAudio)"
        )

        AlertDialog.Builder(this)
            .setTitle("Настройки видео и аудио")
            .setItems(mainItems) { _, which ->
                when (which) {
                    0 -> showAspectRatioDialog()
                    1 -> showVideoQualityDialog()
                    2 -> showAudioTracksDialog()
                }
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showAspectRatioDialog() {
        val names = arrayOf(
            "По размеру экрана (Fit — с сохранением пропорций)",
            "Растянуть на весь экран (Fill — заполнение)",
            "Увеличение / Обрезка (Zoom — убрать чёрные полосы)",
            "Соотношение 16:9 (Широкоформатный)",
            "Соотношение 4:3 (Классический ТВ)"
        )
        val modes = intArrayOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
        )

        AlertDialog.Builder(this)
            .setTitle("Выберите масштаб экрана")
            .setSingleChoiceItems(names, resizeModeIndex) { dialog, which ->
                resizeModeIndex = which
                playerView.resizeMode = modes[which]
                pipPlayerView.resizeMode = modes[which]
                hoverPlayerView.resizeMode = modes[which]
                getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
                    .edit().putInt("resize_mode_idx", resizeModeIndex).apply()
                Toast.makeText(this, "Масштаб: ${names[which].substringBefore(" (")}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showVideoQualityDialog() {
        val names = arrayOf(
            "Максимум до 4K (2160p — без ограничений)",
            "Высокое Full HD (до 1080p)",
            "Стандартное HD (до 720p — минимум)"
        )
        val heights = intArrayOf(2160, 1080, 720)
        val checked = heights.indexOf(selectedMaxHeight).takeIf { it >= 0 } ?: 0

        AlertDialog.Builder(this)
            .setTitle("Качество видеопотока (4K — 720p)")
            .setSingleChoiceItems(names, checked) { dialog, which ->
                selectedMaxHeight = heights[which]
                applyVideoQualityConstraint()
                getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
                    .edit().putInt("max_video_height", selectedMaxHeight).apply()
                Toast.makeText(this, "Качество: ${names[which].substringBefore(" (")}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun applyVideoQualityConstraint() {
        if (!::player.isInitialized) return
        val maxW = when (selectedMaxHeight) {
            2160 -> 3840
            1080 -> 1920
            else -> 1280
        }
        player.trackSelectionParameters = player.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(maxW, selectedMaxHeight)
            .setMinVideoSize(1280, 720)
            .build()
    }

    private fun showAudioTracksDialog() {
        val labels = mutableListOf<String>()
        val overrides = mutableListOf<TrackSelectionOverride?>()
        var activeIdx = 0

        labels.add("Авто (основная дорожка канала)")
        overrides.add(null)

        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        for (group in audioGroups) {
            for (tIdx in 0 until group.length) {
                val fmt = group.getTrackFormat(tIdx)
                val langCode = fmt.language ?: "und"
                val langTitle = when (langCode.lowercase(Locale.US)) {
                    "ru", "rus" -> "Русский"
                    "en", "eng" -> "Английский (Original)"
                    "uk", "ukr" -> "Украинский"
                    "de", "deu", "ger" -> "Немецкий"
                    "fr", "fra", "fre" -> "Французский"
                    else -> langCode.uppercase(Locale.US)
                }
                val extra = fmt.label?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
                val chInfo = if (fmt.channelCount > 2) " 5.1" else if (fmt.channelCount == 2) " Stereo" else ""
                labels.add("Дорожка ${labels.size}: $langTitle$extra$chInfo")
                overrides.add(TrackSelectionOverride(group.mediaTrackGroup, tIdx))
                if (group.isTrackSelected(tIdx)) {
                    activeIdx = labels.size - 1
                }
            }
        }

        if (labels.size == 1) {
            labels.add("Текущий поток содержит 1 звуковую дорожку")
            overrides.add(null)
        }

        AlertDialog.Builder(this)
            .setTitle("Выбор аудиодорожки")
            .setSingleChoiceItems(labels.toTypedArray(), activeIdx) { dialog, which ->
                val ov = overrides[which]
                val builder = player.trackSelectionParameters.buildUpon()
                if (ov == null) {
                    builder.clearOverridesOfType(C.TRACK_TYPE_AUDIO)
                } else {
                    builder.setOverrideForType(ov)
                }
                player.trackSelectionParameters = builder.build()
                Toast.makeText(this, "Выбрано: ${labels[which]}", Toast.LENGTH_SHORT).show()
                dialog.dismiss()
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun openQuickChannelList() {
        playerControlBar.visibility = View.GONE
        miniZapBanner.visibility = View.GONE
        programInfoOverlay.visibility = View.GONE
        quickChannelOverlay.visibility = View.VISIBLE

        quickBrowseCategoryIndex = categories.indexOfFirst { it.first == currentCategoryName }
            .takeIf { it >= 0 } ?: 0
        renderQuickCategoryList()
    }

    private fun renderQuickCategoryList() {
        val pair = categories.getOrNull(quickBrowseCategoryIndex)
        val catTitle = pair?.first ?: currentCategoryName
        val list = pair?.second ?: currentCategoryChannels

        tvQuickCategoryTitle.text = catTitle
        rvQuickChannels.adapter = ChannelAdapter(
            channels = list,
            liveLookup = { getLiveProgram(it) },
            logoLookup = { resolveChannelLogo(it) },
            onHover = null
        ) { chosenChannel ->
            currentCategoryName = catTitle
            currentCategoryChannels = list
            activeChannel = chosenChannel
            activeArchiveProgram = null
            archiveOffsetSeconds = 0L
            playStream(chosenChannel.url)
            updateOsdInfo(chosenChannel, null)
            quickChannelOverlay.visibility = View.GONE
            playerView.requestFocus()
        }
        rvQuickChannels.post { rvQuickChannels.requestFocus() }
    }

    private fun openProgramInfoCard() {
        playerControlBar.visibility = View.GONE
        miniZapBanner.visibility = View.GONE
        quickChannelOverlay.visibility = View.GONE
        programInfoOverlay.visibility = View.VISIBLE

        val ch = activeChannel
        val prog = activeArchiveProgram ?: ch?.let { getLiveProgram(it) }

        tvInfoShowTitle.text = prog?.title ?: (ch?.name ?: "Прямой эфир")

        val vFmt = player.videoFormat
        val aFmt = player.audioFormat
        val vRes = if (vFmt != null && vFmt.width > 0) "${vFmt.width} × ${vFmt.height}" else "Авто (до ${selectedMaxHeight}p)"
        val aLang = aFmt?.language ?: "Русский"
        val aCh = if (aFmt != null && aFmt.channelCount > 1) "Стерео" else "Моно"
        tvInfoStreamSpecs.text = "Видео: $vRes, аудио: $aLang ($aCh)"

        val desc = prog?.description?.takeIf { it.isNotBlank() }
            ?: "Описание передачи отсутствует в телепрограмме EPG для данного канала."
        tvInfoShowDescription.text = desc
    }

    private fun switchScreen(s: Screen) {
        currentScreen = s
        if (s != Screen.PLAYER) {
            handler.removeCallbacks(autoHidePreviewRunnable)
        }
        if (s != Screen.CATEGORY_CHANNELS) {
            handler.removeCallbacks(hoverPlayRunnable)
            hoverPlayerView.player = null
        }
        screenCategories.visibility = if (s == Screen.CATEGORIES) View.VISIBLE else View.GONE
        screenCategoryChannels.visibility = if (s == Screen.CATEGORY_CHANNELS) View.VISIBLE else View.GONE
        screenPlayer.visibility = if (s == Screen.PLAYER) View.VISIBLE else View.GONE
        screenSettings.visibility = if (s == Screen.SETTINGS) View.VISIBLE else View.GONE
    }

    private fun showCategoriesScreen() {
        updateTopPlaylistButtonTitle()
        switchScreen(Screen.CATEGORIES)
        rvCategories.adapter = CategoryAdapter(categories) { name, list ->
            openCategory(name, list)
        }
        rvCategories.post {
            if (categories.isNotEmpty()) rvCategories.requestFocus() else btnPlaylistSelectorTop.requestFocus()
        }
    }

    private fun openCategory(name: String, list: List<Channel>) {
        currentCategoryName = name
        currentCategoryChannels = list
        tvCategoryHeaderTitle.text = name
        switchScreen(Screen.CATEGORY_CHANNELS)

        // В режиме сетки каналов с живым окном справа скрываем плавающее PiP, чтобы не перекрывать превью
        if (isInAppPipActive) {
            floatingPipContainer.visibility = View.GONE
        }

        rvCategoryChannels.adapter = ChannelAdapter(
            channels = list,
            liveLookup = { getLiveProgram(it) },
            logoLookup = { resolveChannelLogo(it) },
            onHover = { hovered -> onChannelHovered(hovered) }
        ) { selectedChannel ->
            handler.removeCallbacks(hoverPlayRunnable)
            hoverPlayerView.player = null
            if (isInAppPipActive) {
                closeInAppPip(stopPlayback = false)
            }
            openPlayerPreview(selectedChannel)
        }
        list.firstOrNull()?.let { onChannelHovered(it) }
        rvCategoryChannels.post { rvCategoryChannels.requestFocus() }
    }

    private fun openPlayerPreview(channel: Channel) {
        activeChannel = channel
        activeArchiveProgram = null
        archiveOffsetSeconds = 0L
        playerView.player = player
        switchScreen(Screen.PLAYER)
        updateOsdInfo(channel, null)
        playStream(channel.url)
        selectPlayerTab(archive = false)
        enterPreviewMode()
    }

    private fun selectPlayerTab(archive: Boolean) {
        isArchiveTab = archive
        tabLiveProgram.isSelected = !archive
        tabArchiveProgram.isSelected = archive

        val channel = activeChannel ?: return
        val now = System.currentTimeMillis()
        val allProgs = getProgramsForChannel(channel)

        if (!archive) {
            val upcoming = allProgs.filter { it.endMs > now }.take(45)
            rvPlayerEpg.adapter = EpgAdapter(upcoming, isArchive = false) {
                activeArchiveProgram = null
                archiveOffsetSeconds = 0L
                playStream(channel.url)
                updateOsdInfo(channel, null)
                resetPreviewAutoHideTimer()
            }
        } else {
            val maxAgeMs = (channel.catchupDays.takeIf { it > 0 } ?: 5) * 24L * 3600L * 1000L
            val past = allProgs.filter { it.isPast(now) && it.startMs >= now - maxAgeMs }
                .sortedByDescending { it.startMs }
                .take(70)
            rvPlayerEpg.adapter = EpgAdapter(past, isArchive = true) { archiveProg ->
                activeArchiveProgram = archiveProg
                archiveOffsetSeconds = 0L
                val url = Parsers.buildArchiveUrl(channel, archiveProg, 0L)
                playStream(url)
                updateOsdInfo(channel, archiveProg)
                btnOsdFullscreen.post { btnOsdFullscreen.requestFocus() }
                resetPreviewAutoHideTimer()
            }
        }
    }

    private fun updateOsdInfo(channel: Channel, archiveProg: EpgProgram?) {
        activeArchiveProgram = archiveProg
        val isArch = archiveProg != null
        btnOsdRewind.visibility = if (isArch) View.VISIBLE else View.GONE
        btnOsdForward.visibility = if (isArch) View.VISIBLE else View.GONE
        osdSeekBar.isFocusable = isArch

        tvOsdBadge.text = channel.name.take(3).uppercase(Locale.getDefault())
        tvOsdChannelName.text = channel.name
        btnOsdFavorite.setTextColor(if (channel.isFavorite) Color.parseColor("#FFD166") else Color.WHITE)

        val logo = resolveChannelLogo(channel)
        if (logo.isNotEmpty()) {
            ivOsdLogo.visibility = View.VISIBLE
            ivOsdLogo.load(logo) {
                crossfade(true)
                listener(
                    onSuccess = { _, _ -> tvOsdBadge.visibility = View.GONE },
                    onError = { _, _ -> ivOsdLogo.visibility = View.GONE; tvOsdBadge.visibility = View.VISIBLE }
                )
            }
        } else {
            ivOsdLogo.visibility = View.GONE
            tvOsdBadge.visibility = View.VISIBLE
        }

        if (archiveProg != null) {
            tvOsdCurrentShow.text = archiveProg.title
            tvOsdNextShow.text = "АРХИВ • ◀▶ перемотка 30с / 1м • ${dateTimeFormat.format(Date(archiveProg.startMs))}"
            updateArchiveProgressUi()
            return
        }

        val now = System.currentTimeMillis()
        val progs = getProgramsForChannel(channel)
        val liveIdx = progs.indexOfFirst { it.isLive(now) }

        if (liveIdx >= 0) {
            val cur = progs[liveIdx]
            val next = progs.getOrNull(liveIdx + 1)
            tvOsdCurrentShow.text = cur.title
            tvOsdNextShow.text = if (next != null) "Далее: ${next.title}" else "Прямой эфир"
            tvOsdTimeRight.text = "${timeFormat.format(Date(cur.startMs))}\nДалее ${timeFormat.format(Date(cur.endMs))}"
            osdSeekBar.progress = cur.progressPercent(now)
        } else {
            tvOsdCurrentShow.text = channel.name
            tvOsdNextShow.text = "Прямой эфир"
            tvOsdTimeRight.text = timeFormat.format(Date())
            osdSeekBar.progress = 100
        }
    }

    private fun playStream(url: String) {
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
        btnOsdPlayPause.text = "❚❚"
    }

    private fun loadActivePlaylistAndEpg() {
        val activePl = playlists.getOrNull(activePlaylistIndex)
            ?: PlaylistEntry(DEFAULT_PLAYLIST_NAME, DEFAULT_PLAYLIST_URL)
        val prefs = getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
        val savedEpgUrl = prefs.getString("epg_url", EPG_LITE_URL) ?: EPG_LITE_URL

        Toast.makeText(this, "Загрузка: ${activePl.name}...", Toast.LENGTH_SHORT).show()
        thread {
            try {
                val parsed = Parsers.parseM3u(activePl.url)
                runOnUiThread {
                    if (parsed.channels.isNotEmpty()) {
                        allChannels = parsed.channels
                        restoreFavorites()
                        rebuildCategories()
                        if (currentScreen == Screen.CATEGORIES) showCategoriesScreen()
                        Toast.makeText(this, "Каналов загружено: ${allChannels.size}", Toast.LENGTH_SHORT).show()
                    } else {
                        Toast.makeText(this, "Плейлист пуст или недоступен", Toast.LENGTH_LONG).show()
                    }
                }

                val epgToLoad = savedEpgUrl.ifBlank { parsed.detectedEpgUrl ?: EPG_LITE_URL }
                val xmltv = Parsers.parseXmltv(epgToLoad)
                runOnUiThread {
                    epgMap = xmltv.programsByKey
                    epgIconsMap = xmltv.iconsByKey
                    if (currentScreen == Screen.CATEGORY_CHANNELS) {
                        rvCategoryChannels.adapter?.notifyDataSetChanged()
                        hoveredChannel?.let { onChannelHovered(it) }
                    } else if (currentScreen == Screen.PLAYER) {
                        selectPlayerTab(isArchiveTab)
                        activeChannel?.let { updateOsdInfo(it, activeArchiveProgram) }
                    }
                    Toast.makeText(this, "EPG и логотипы загружены", Toast.LENGTH_SHORT).show()
                }
            } catch (e: Exception) {
                runOnUiThread {
                    Toast.makeText(this, "Ошибка загрузки: ${e.message}", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun rebuildCategories() {
        val result = mutableListOf<Pair<String, List<Channel>>>()
        val favs = allChannels.filter { it.isFavorite }
        if (favs.isNotEmpty()) {
            result.add("Избранное" to favs)
        }
        val grouped = allChannels.groupBy { it.category }
        for ((cat, list) in grouped) {
            result.add(cat to list)
        }
        categories = result
    }

    private fun getProgramsForChannel(channel: Channel): List<EpgProgram> {
        if (channel.tvgId.isNotEmpty()) {
            epgMap[channel.tvgId]?.let { return it }
            epgMap[Parsers.normalizeKey(channel.tvgId)]?.let { return it }
        }
        if (channel.tvgName.isNotEmpty()) {
            epgMap[Parsers.normalizeKey(channel.tvgName)]?.let { return it }
        }
        return epgMap[Parsers.normalizeKey(channel.name)] ?: emptyList()
    }

    private fun resolveChannelLogo(channel: Channel): String {
        if (channel.logoUrl.isNotBlank()) return channel.logoUrl
        if (channel.tvgId.isNotEmpty()) {
            epgIconsMap[channel.tvgId]?.let { return it }
            epgIconsMap[Parsers.normalizeKey(channel.tvgId)]?.let { return it }
        }
        if (channel.tvgName.isNotEmpty()) {
            epgIconsMap[Parsers.normalizeKey(channel.tvgName)]?.let { return it }
        }
        return epgIconsMap[Parsers.normalizeKey(channel.name)] ?: ""
    }

    private fun getLiveProgram(channel: Channel): EpgProgram? {
        val now = System.currentTimeMillis()
        return getProgramsForChannel(channel).firstOrNull { it.isLive(now) }
    }

    override fun dispatchKeyEvent(event: KeyEvent): Boolean {
        if (event.action == KeyEvent.ACTION_DOWN) {
            // ПЕРЕХОД ВПРАВО С ПРАВОЙ КОЛОНКИ НА ВСПЛЫВШЕЕ ОКНО PiP (РАЗВЕРНУТЬ / ЗАКРЫТЬ)
            if (isInAppPipActive && floatingPipContainer.visibility == View.VISIBLE && event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                val focused = currentFocus
                if (focused != null && focused != btnPipExpand && focused != btnPipClose) {
                    val loc = IntArray(2)
                    focused.getLocationOnScreen(loc)
                    val screenWidth = resources.displayMetrics.widthPixels
                    // Если фокус находится в правой половине экрана (правая колонка или правая верхняя кнопка)
                    if (loc[0] > screenWidth * 0.45f) {
                        btnPipExpand.requestFocus()
                        return true
                    }
                }
            }

            if (currentScreen == Screen.PLAYER) {
                if (!isFullscreenMode) {
                    resetPreviewAutoHideTimer()
                }

                if (programInfoOverlay.visibility == View.VISIBLE) {
                    if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
                        programInfoOverlay.visibility = View.GONE
                        playerView.requestFocus()
                        return true
                    }
                }

                if (quickChannelOverlay.visibility == View.VISIBLE) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (categories.isNotEmpty()) {
                                quickBrowseCategoryIndex = (quickBrowseCategoryIndex - 1 + categories.size) % categories.size
                                renderQuickCategoryList()
                            }
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (categories.isNotEmpty()) {
                                quickBrowseCategoryIndex = (quickBrowseCategoryIndex + 1) % categories.size
                                renderQuickCategoryList()
                            }
                            return true
                        }
                        KeyEvent.KEYCODE_BACK -> {
                            quickChannelOverlay.visibility = View.GONE
                            playerView.requestFocus()
                            return true
                        }
                    }
                    return super.dispatchKeyEvent(event)
                }

                if (isFullscreenMode && playerControlBar.visibility != View.VISIBLE) {
                    when (event.keyCode) {
                        KeyEvent.KEYCODE_DPAD_DOWN -> {
                            openQuickChannelList()
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_UP -> {
                            openProgramInfoCard()
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_LEFT -> {
                            if (activeArchiveProgram != null) {
                                seekArchiveBySeconds(-30L)
                            } else {
                                switchChannelInCategoryInstant(-1)
                            }
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_RIGHT -> {
                            if (activeArchiveProgram != null) {
                                seekArchiveBySeconds(30L)
                            } else {
                                switchChannelInCategoryInstant(1)
                            }
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_REWIND -> {
                            if (activeArchiveProgram != null) seekArchiveBySeconds(-60L)
                            return true
                        }
                        KeyEvent.KEYCODE_MEDIA_FAST_FORWARD -> {
                            if (activeArchiveProgram != null) seekArchiveBySeconds(60L)
                            return true
                        }
                        KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU -> {
                            animateControlBarIn()
                            return true
                        }
                    }
                }

                if (isFullscreenMode && playerControlBar.visibility == View.VISIBLE) {
                    handler.removeCallbacks(hideFullControlsRunnable)
                    handler.postDelayed(hideFullControlsRunnable, 6000L)
                }
            }
        }
        return super.dispatchKeyEvent(event)
    }

    override fun onKeyDown(keyCode: Int, event: KeyEvent?): Boolean {
        if (keyCode == KeyEvent.KEYCODE_BACK) {
            when (currentScreen) {
                Screen.PLAYER -> {
                    if (programInfoOverlay.visibility == View.VISIBLE) {
                        programInfoOverlay.visibility = View.GONE
                        playerView.requestFocus()
                        return true
                    }
                    if (quickChannelOverlay.visibility == View.VISIBLE) {
                        quickChannelOverlay.visibility = View.GONE
                        playerView.requestFocus()
                        return true
                    }
                    if (isFullscreenMode) {
                        if (playerControlBar.visibility == View.VISIBLE) {
                            animateControlBarOut()
                            return true
                        }
                        enterPreviewMode()
                        return true
                    }
                    handler.removeCallbacks(autoHidePreviewRunnable)
                    player.stop()
                    openCategory(currentCategoryName, currentCategoryChannels)
                    return true
                }
                Screen.CATEGORY_CHANNELS -> {
                    if (!isInAppPipActive) {
                        player.stop()
                    }
                    hoverPlayerView.player = null
                    showCategoriesScreen()
                    return true
                }
                Screen.SETTINGS -> {
                    showCategoriesScreen()
                    return true
                }
                Screen.CATEGORIES -> { }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun switchChannelInCategoryInstant(offset: Int) {
        val cur = activeChannel ?: return
        if (currentCategoryChannels.isEmpty()) return
        val idx = currentCategoryChannels.indexOfFirst { it.url == cur.url }
        if (idx >= 0) {
            val nextIdx = (idx + offset + currentCategoryChannels.size) % currentCategoryChannels.size
            val nextCh = currentCategoryChannels[nextIdx]
            activeChannel = nextCh
            activeArchiveProgram = null
            archiveOffsetSeconds = 0L
            playStream(nextCh.url)
            updateOsdInfo(nextCh, null)

            val live = getLiveProgram(nextCh)
            showMiniZapNotification("${nextIdx + 1}. ${nextCh.name}", live?.title ?: "Прямой эфир")
        }
    }

    private fun showPlaylistManagerDialog() {
        val names = playlists.mapIndexed { idx, pl ->
            val mark = if (idx == activePlaylistIndex) "✓ " else "   "
            "$mark${pl.name}"
        }.toMutableList()
        names.add("+ Добавить новый плейлист M3U")

        AlertDialog.Builder(this)
            .setTitle("Выбор плейлиста")
            .setItems(names.toTypedArray()) { _, which ->
                if (which == playlists.size) {
                    showAddNewPlaylistDialog()
                } else {
                    activePlaylistIndex = which
                    savePlaylists()
                    updateTopPlaylistButtonTitle()
                    loadActivePlaylistAndEpg()
                }
            }
            .setNeutralButton("Удалить текущий") { _, _ ->
                if (playlists.size > 1) {
                    playlists.removeAt(activePlaylistIndex)
                    activePlaylistIndex = 0
                    savePlaylists()
                    updateTopPlaylistButtonTitle()
                    loadActivePlaylistAndEpg()
                } else {
                    Toast.makeText(this, "Нельзя удалить единственный плейлист", Toast.LENGTH_SHORT).show()
                }
            }
            .setNegativeButton("Закрыть", null)
            .show()
    }

    private fun showAddNewPlaylistDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }
        val etName = EditText(this).apply {
            hint = "Название (например: Мой IPTV)"
            isFocusable = true
        }
        val etUrl = EditText(this).apply {
            hint = "http://..."
            isFocusable = true
        }
        layout.addView(etName)
        layout.addView(etUrl)

        AlertDialog.Builder(this)
            .setTitle("Добавить плейлист M3U")
            .setView(layout)
            .setPositiveButton("Добавить и загрузить") { _, _ ->
                val url = etUrl.text.toString().trim()
                val name = etName.text.toString().trim().ifEmpty { "Плейлист ${playlists.size + 1}" }
                if (url.isNotEmpty()) {
                    playlists.add(PlaylistEntry(name, url))
                    activePlaylistIndex = playlists.size - 1
                    savePlaylists()
                    updateTopPlaylistButtonTitle()
                    loadActivePlaylistAndEpg()
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun showEpgSelectorDialog() {
        val prefs = getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
        val options = arrayOf(
            "VipLime Lite (быстрая загрузка EPG)",
            "VipLime Full (полная телепрограмма и описания)",
            "Ввести свою ссылку XMLTV"
        )
        AlertDialog.Builder(this)
            .setTitle("Источник телепрограммы EPG")
            .setItems(options) { _, which ->
                when (which) {
                    0 -> {
                        prefs.edit().putString("epg_url", EPG_LITE_URL).apply()
                        loadActivePlaylistAndEpg()
                    }
                    1 -> {
                        prefs.edit().putString("epg_url", EPG_FULL_URL).apply()
                        loadActivePlaylistAndEpg()
                    }
                    2 -> {
                        val input = EditText(this).apply {
                            setText(prefs.getString("epg_url", EPG_LITE_URL))
                            isFocusable = true
                        }
                        AlertDialog.Builder(this)
                            .setTitle("Ссылка на XMLTV (.xml / .xml.gz)")
                            .setView(input)
                            .setPositiveButton("Сохранить") { _, _ ->
                                prefs.edit().putString("epg_url", input.text.toString().trim()).apply()
                                loadActivePlaylistAndEpg()
                            }
                            .setNegativeButton("Отмена", null)
                            .show()
                    }
                }
            }
            .show()
    }

    private fun showBufferDialog() {
        val labels = arrayOf("Минимальный (2 сек)", "Средний (10 сек)", "Большой (25 сек)", "Максимальный (45 сек)")
        val values = intArrayOf(2, 10, 25, 45)
        AlertDialog.Builder(this)
            .setTitle("Буферизация видео")
            .setItems(labels) { _, which ->
                bufferSeconds = values[which]
                getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
                    .edit().putInt("buffer_sec", bufferSeconds).apply()
                btnSetBuffer.text = "Буферизация видео: $bufferSeconds сек"
                initPlayerWithBuffer(bufferSeconds)
                activeChannel?.let { playStream(it.url) }
                Toast.makeText(this, "Буфер установлен: $bufferSeconds сек", Toast.LENGTH_SHORT).show()
            }
            .show()
    }

    private fun showSearchDialog() {
        val input = EditText(this).apply {
            hint = "Введите название канала..."
            isFocusable = true
        }
        AlertDialog.Builder(this)
            .setTitle("Поиск канала")
            .setView(input)
            .setPositiveButton("Найти") { _, _ ->
                val q = input.text.toString().trim().lowercase(Locale.getDefault())
                if (q.isNotEmpty()) {
                    val found = allChannels.filter { it.name.lowercase(Locale.getDefault()).contains(q) }
                    openCategory("Поиск: $q", found)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun loadSavedPlaylistsAndSettings() {
        val prefs = getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
        bufferSeconds = prefs.getInt("buffer_sec", 10)
        selectedMaxHeight = prefs.getInt("max_video_height", 2160)
        resizeModeIndex = prefs.getInt("resize_mode_idx", 0)
        glassAlphaPercent = prefs.getInt("glass_alpha_pct", 85).coerceIn(25, 100)
        wallpaperPresetIndex = prefs.getInt("wallpaper_idx", 0)
        customWallpaperUrl = prefs.getString("wallpaper_url", "") ?: ""
        themePresetIndex = prefs.getInt("theme_idx", 0).coerceIn(0, themePresets.size - 1)
        neonGlowIndex = prefs.getInt("neon_glow_idx", 1).coerceIn(0, neonGlowPresets.size - 1)

        seekBarGlassOpacity.progress = glassAlphaPercent
        tvOpacityPercent.text = "$glassAlphaPercent%"
        btnSetBuffer.text = "Буферизация видео: $bufferSeconds сек"
        activePlaylistIndex = prefs.getInt("active_playlist_idx", 0)

        val modes = intArrayOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_WIDTH,
            AspectRatioFrameLayout.RESIZE_MODE_FIXED_HEIGHT
        )
        if (resizeModeIndex in modes.indices) {
            playerView.resizeMode = modes[resizeModeIndex]
        }

        val jsonStr = prefs.getString("playlists_json", null)
        playlists.clear()
        if (!jsonStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    playlists.add(PlaylistEntry(obj.getString("name"), obj.getString("url")))
                }
            } catch (_: Exception) { }
        }
        if (playlists.isEmpty()) {
            playlists.add(PlaylistEntry(DEFAULT_PLAYLIST_NAME, DEFAULT_PLAYLIST_URL))
            savePlaylists()
        }
        if (activePlaylistIndex !in playlists.indices) activePlaylistIndex = 0
        updateTopPlaylistButtonTitle()
    }

    private fun savePlaylists() {
        val arr = JSONArray()
        for (pl in playlists) {
            val obj = JSONObject()
            obj.put("name", pl.name)
            obj.put("url", pl.url)
            arr.put(obj)
        }
        getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE).edit()
            .putString("playlists_json", arr.toString())
            .putInt("active_playlist_idx", activePlaylistIndex)
            .apply()
    }

    private fun updateTopPlaylistButtonTitle() {
        val name = playlists.getOrNull(activePlaylistIndex)?.name ?: DEFAULT_PLAYLIST_NAME
        btnPlaylistSelectorTop.text = "Плейлист: $name ▼"
    }

    private fun saveFavorites() {
        val prefs = getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
        val favs = allChannels.filter { it.isFavorite }.map { it.url }.toSet()
        prefs.edit().putStringSet("favorites", favs).apply()
    }

    private fun restoreFavorites() {
        val prefs = getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
        val favs = prefs.getStringSet("favorites", emptySet()) ?: emptySet()
        allChannels.forEach { it.isFavorite = favs.contains(it.url) }
    }

    override fun onDestroy() {
        super.onDestroy()
        handler.removeCallbacks(clockRunnable)
        handler.removeCallbacks(hideFullControlsRunnable)
        handler.removeCallbacks(hideMiniZapRunnable)
        handler.removeCallbacks(autoHidePreviewRunnable)
        handler.removeCallbacks(hoverPlayRunnable)
        player.release()
    }

    inner class CategoryAdapter(
        private val items: List<Pair<String, List<Channel>>>,
        private val onClick: (String, List<Channel>) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvCategoryName)
            val tvCount: TextView = v.findViewById(R.id.tvCategoryCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (name, list) = items[position]
            val theme = themePresets.getOrElse(themePresetIndex) { themePresets[0] }
            val ambilight = neonGlowPresets.getOrElse(neonGlowIndex) { neonGlowPresets[1] }

            holder.itemView.background = buildBorderlessGlassTile(theme, radiusDp = 16f)
            applyAmbilightGlow(holder.itemView, ambilight)

            holder.tvName.text = name
            holder.tvCount.text = list.size.toString()

            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                val c = if (hasFocus) Color.parseColor("#0F172A") else Color.WHITE
                val sub = if (hasFocus) Color.parseColor("#0F172A") else Color.parseColor("#E2E8F0")
                holder.tvName.setTextColor(c)
                holder.tvCount.setTextColor(sub)
            }
            holder.itemView.setOnClickListener { onClick(name, list) }
        }

        override fun getItemCount(): Int = items.size
    }

    inner class ChannelAdapter(
        private val channels: List<Channel>,
        private val liveLookup: (Channel) -> EpgProgram?,
        private val logoLookup: (Channel) -> String,
        private val onHover: ((Channel) -> Unit)?,
        private val onClick: (Channel) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvBadge: TextView = v.findViewById(R.id.tvChannelBadge)
            val ivLogo: ImageView = v.findViewById(R.id.ivChannelLogo)
            val tvName: TextView = v.findViewById(R.id.tvChannelName)
            val tvProg: TextView = v.findViewById(R.id.tvChannelCurrentProgram)
            val tvFav: TextView = v.findViewById(R.id.tvFavStar)
            val pbProgress: ProgressBar = v.findViewById(R.id.pbChannelProgress)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_channel, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ch = channels[position]
            val now = System.currentTimeMillis()
            val live = liveLookup(ch)
            val logo = logoLookup(ch)
            val theme = themePresets.getOrElse(themePresetIndex) { themePresets[0] }
            val ambilight = neonGlowPresets.getOrElse(neonGlowIndex) { neonGlowPresets[1] }

            holder.itemView.background = buildBorderlessGlassTile(theme, radiusDp = 16f)
            applyAmbilightGlow(holder.itemView, ambilight)
            holder.pbProgress.progressTintList = ColorStateList.valueOf(theme.accentColor)

            holder.tvBadge.text = ch.name.take(3).uppercase(Locale.getDefault())
            holder.tvName.text = ch.name
            holder.tvProg.text = live?.title ?: "Прямой эфир"
            holder.tvFav.visibility = if (ch.isFavorite) View.VISIBLE else View.GONE
            holder.pbProgress.progress = live?.progressPercent(now) ?: 0

            if (logo.isNotEmpty()) {
                holder.ivLogo.visibility = View.VISIBLE
                holder.tvBadge.visibility = View.VISIBLE
                holder.ivLogo.load(logo) {
                    crossfade(true)
                    listener(
                        onSuccess = { _, _ -> holder.tvBadge.visibility = View.GONE },
                        onError = { _, _ -> holder.ivLogo.visibility = View.GONE; holder.tvBadge.visibility = View.VISIBLE }
                    )
                }
            } else {
                holder.ivLogo.visibility = View.GONE
                holder.tvBadge.visibility = View.VISIBLE
            }

            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                val titleC = if (hasFocus) Color.parseColor("#0F172A") else Color.WHITE
                val subC = if (hasFocus) Color.parseColor("#1E293B") else Color.parseColor("#CBD5E1")
                holder.tvName.setTextColor(titleC)
                holder.tvProg.setTextColor(subC)
                if (hasFocus) {
                    onHover?.invoke(ch)
                }
            }
            holder.itemView.setOnClickListener { onClick(ch) }
        }

        override fun getItemCount(): Int = channels.size
    }

    inner class EpgAdapter(
        private val programs: List<EpgProgram>,
        private val isArchive: Boolean,
        private val onClick: (EpgProgram) -> Unit
    ) : RecyclerView.Adapter<EpgAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvTitle: TextView = v.findViewById(R.id.tvEpgTitle)
            val tvTime: TextView = v.findViewById(R.id.tvEpgTime)
            val tvDur: TextView = v.findViewById(R.id.tvEpgDuration)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_epg, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val p = programs[position]
            val now = System.currentTimeMillis()
            val startStr = if (isArchive) dateTimeFormat.format(Date(p.startMs)) else timeFormat.format(Date(p.startMs))
            val endStr = timeFormat.format(Date(p.endMs))
            val theme = themePresets.getOrElse(themePresetIndex) { themePresets[0] }

            holder.itemView.background = buildBorderlessGlassTile(theme, radiusDp = 14f)

            holder.tvTitle.text = p.title
            holder.tvTime.text = if (!isArchive && p.isLive(now)) {
                "Сейчас / $startStr — $endStr"
            } else {
                "$startStr — $endStr"
            }
            holder.tvDur.text = "${p.durationMinutes()} минут"

            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                val titleC = if (hasFocus) Color.parseColor("#0F172A") else Color.WHITE
                val subC = if (hasFocus) Color.parseColor("#1E293B") else Color.parseColor("#CBD5E1")
                holder.tvTitle.setTextColor(titleC)
                holder.tvTime.setTextColor(subC)
                holder.tvDur.setTextColor(subC)
            }
            holder.itemView.setOnClickListener { onClick(p) }
        }

        override fun getItemCount(): Int = programs.size
    }
}
