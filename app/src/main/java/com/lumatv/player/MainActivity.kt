package com.lumatv.player

import android.animation.Animator
import android.animation.AnimatorListenerAdapter
import android.app.AlertDialog
import android.content.Context
import android.media.AudioManager
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.StateListDrawable
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.StateSet
import android.view.GestureDetector
import android.view.KeyEvent
import android.view.MotionEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.view.animation.DecelerateInterpolator
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.SeekBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.common.TrackSelectionOverride
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.mediacodec.MediaCodecSelector
import androidx.media3.exoplayer.mediacodec.MediaCodecUtil
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.exoplayer.trackselection.DefaultTrackSelector
import androidx.media3.exoplayer.upstream.DefaultBandwidthMeter
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.GridLayoutManager
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import coil.load
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.concurrent.thread
import kotlin.math.abs

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
    private lateinit var btnSearchMain: Button
    private lateinit var btnLayoutCategories: Button
    private lateinit var btnSettingsMain: Button
    private lateinit var rvCategories: RecyclerView

    // Экран 2: Каналы / кинотеатры категории + фильтры по году, жанру, стране
    private lateinit var btnBackFromCategory: Button
    private lateinit var tvCategoryHeaderTitle: TextView
    private lateinit var tvPlayerStatus: TextView
    private lateinit var glassModalOverlay: FrameLayout
    private lateinit var glassModalCard: LinearLayout
    private lateinit var tvGlassModalTitle: TextView
    private lateinit var rvGlassModalOptions: RecyclerView
    private var modalReturnFocusView: View? = null
    private var streamRetryCount = 0
    private lateinit var btnLayoutChannels: Button
    private lateinit var btnSearchCategory: Button
    private lateinit var rvCategoryChannels: RecyclerView

    // Экран 3: Плеер
    private lateinit var playerView: PlayerView
    private lateinit var videoPictureOverlay: View
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
    private lateinit var btnOsdFavorite: Button
    private lateinit var btnOsdPip: Button
    private lateinit var btnOsdAspect: Button
    private lateinit var btnOsdQuality: Button

    // Мини-окно PiP
    private lateinit var floatingPipContainer: FrameLayout
    private lateinit var pipPlayerView: PlayerView
    private lateinit var tvPipTitle: TextView
    private lateinit var btnPipExpand: Button
    private lateinit var btnPipClose: Button

    // Оверлеи
    private lateinit var quickChannelOverlay: LinearLayout
    private lateinit var btnQuickPrevCat: Button
    private lateinit var btnQuickNextCat: Button
    private lateinit var btnQuickClose: Button
    private lateinit var tvQuickCategoryTitle: TextView
    private lateinit var rvQuickChannels: RecyclerView

    private lateinit var programInfoOverlay: LinearLayout
    private lateinit var tvInfoShowTitle: TextView
    private lateinit var tvInfoStreamSpecs: TextView
    private lateinit var tvInfoShowDescription: TextView

    // Настройки
    private lateinit var btnCloseSettings: Button
    private lateinit var btnSetHiddenCategories: Button
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
    private var activeArchiveProgram: EpgProgram? = null
    private var archiveOffsetSeconds: Long = 0L
    private var isArchiveTab = false
    private var isFullscreenMode = false
    private var isInAppPipActive = false
    private var resizeModeIndex = 0
    private var selectedMaxHeight = 2160
    private var forceMaxBitrate = true
    private var decoderMode = 0 // 0=Аппаратный (Авто), 1=Аппаратный + Туннелирование, 2=Программный
    private val recentChannelUrls = mutableListOf<String>()
    private val hiddenCategories = mutableSetOf<String>()
    private var previousChannelUrl: String = ""
    private var sleepTimerMinutes: Int = 0
    private var sleepTimerEndMs: Long = 0L
    private var brightnessPercent = 100
    private var sharpnessLevel = 1 // 0=Мягкая, 1=Стандарт (ТВ), 2=Высокая чёткость, 3=Максимум
    private var bufferSeconds = 10
    private var glassAlphaPercent = 85
    private var wallpaperPresetIndex = 0
    private var customWallpaperUrl = ""
    private var themePresetIndex = 0
    private var neonGlowIndex = 1
    private var catLayoutModeIndex = 0 // 0=2 col, 1=3 col large tile, 2=4 col medium tile, 3=list
    private var chanLayoutModeIndex = 0 // 0=2 col, 1=3 col large tile, 2=4 col medium tile, 3=list
    private var lastBackPressMs = 0L
    private var shouldAutoResumeLastChannel = false
    private var pendingResumeChannelUrl: String = ""
    private var pendingResumeCategoryName: String = ""
    private var wasPlayingBeforePause = false

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

    private val hideMiniZapRunnable = Runnable {
        miniZapBanner.visibility = View.GONE
    }

    private val autoHidePreviewRunnable = Runnable {
        if (currentScreen == Screen.PLAYER && !isFullscreenMode && quickChannelOverlay.visibility != View.VISIBLE && programInfoOverlay.visibility != View.VISIBLE && glassModalOverlay.visibility != View.VISIBLE) {
            enterFullscreenMode()
        }
    }

    private val hideFullControlsRunnable = Runnable {
        if (isFullscreenMode && quickChannelOverlay.visibility != View.VISIBLE && programInfoOverlay.visibility != View.VISIBLE && glassModalOverlay.visibility != View.VISIBLE) {
            animateControlBarOut()
        }
    }

    private val hideQuickChannelsRunnable = Runnable {
        if (quickChannelOverlay.visibility == View.VISIBLE) {
            closeQuickChannelList()
        }
    }

    private val hideGlassModalRunnable = Runnable {
        if (glassModalOverlay.visibility == View.VISIBLE && currentScreen == Screen.PLAYER) {
            closeGlassModal()
        }
    }

    private val sleepTimerRunnable = Runnable {
        sleepTimerMinutes = 0
        sleepTimerEndMs = 0L
        saveLastWatchedState(null, isWatching = false)
        if (::player.isInitialized) {
            player.stop()
        }
        Toast.makeText(this, "Таймер сна: приложение закрывается", Toast.LENGTH_LONG).show()
        finish()
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
        initDefaultCatalogPlaylists()
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

    private fun initDefaultCatalogPlaylists() {
        playlists.clear()
        playlists.add(
            PlaylistEntry(
                name = DEFAULT_PLAYLIST_NAME,
                url = DEFAULT_PLAYLIST_URL,
                rawResId = 0
            )
        )
    }

    private fun initPlayerWithBuffer(bufSec: Int) {
        if (::player.isInitialized) {
            player.release()
        }
        val minBufMs = (bufSec * 1000).coerceAtLeast(2000)
        val maxBufMs = (bufSec * 2500).coerceAtLeast(10000)
        val loadControl = DefaultLoadControl.Builder()
            .setBufferDurationsMs(minBufMs, maxBufMs, 1200, 2500)
            .setPrioritizeTimeOverSizeThresholds(true)
            .build()

        val renderersFactory = DefaultRenderersFactory(this)
            .setEnableDecoderFallback(true)
            .forceEnableMediaCodecAsynchronousQueueing()
        if (decoderMode == 2) {
            renderersFactory.setMediaCodecSelector(MediaCodecSelector { mimeType, requiresSecureDecoder, requiresTunnelingDecoder ->
                val list = MediaCodecUtil.getDecoderInfos(mimeType, requiresSecureDecoder, requiresTunnelingDecoder)
                if (mimeType.startsWith("video/")) {
                    val sw = list.filter { it.softwareOnly }
                    if (sw.isNotEmpty()) sw + list.filterNot { it.softwareOnly } else list
                } else {
                    list
                }
            })
        }

        val httpFactory = DefaultHttpDataSource.Factory()
            .setUserAgent("Televizo/1.9.9.44")
            .setAllowCrossProtocolRedirects(true)
            .setConnectTimeoutMs(15000)
            .setReadTimeoutMs(20000)

        val mediaSourceFactory = DefaultMediaSourceFactory(this)
            .setDataSourceFactory(httpFactory)

        val bandwidthMeter = DefaultBandwidthMeter.Builder(this)
            .setInitialBitrateEstimate(10_000_000L)
            .build()

        val trackSelector = DefaultTrackSelector(this)

        player = ExoPlayer.Builder(this, renderersFactory)
            .setMediaSourceFactory(mediaSourceFactory)
            .setTrackSelector(trackSelector)
            .setBandwidthMeter(bandwidthMeter)
            .setLoadControl(loadControl)
            .build()
        player.videoScalingMode = C.VIDEO_SCALING_MODE_SCALE_TO_FIT
        player.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_BUFFERING -> {
                        tvPlayerStatus.text = "Буферизация..."
                        tvPlayerStatus.visibility = View.VISIBLE
                    }
                    Player.STATE_READY -> {
                        streamRetryCount = 0
                        tvPlayerStatus.visibility = View.GONE
                    }
                    Player.STATE_ENDED -> {
                        tvPlayerStatus.visibility = View.GONE
                    }
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                if (streamRetryCount < 1 && activeChannel != null) {
                    streamRetryCount++
                    tvPlayerStatus.text = "Переподключение..."
                    tvPlayerStatus.visibility = View.VISIBLE
                    handler.postDelayed({
                        activeChannel?.let {
                            player.prepare()
                            player.play()
                        }
                    }, 1500L)
                } else {
                    tvPlayerStatus.text = "Поток недоступен"
                    tvPlayerStatus.visibility = View.VISIBLE
                }
            }
        })
        applyVideoQualityConstraint()
        if (isInAppPipActive) {
            pipPlayerView.player = player
        } else {
            playerView.player = player
        }
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
        btnSearchMain = findViewById(R.id.btnSearchMain)
        btnLayoutCategories = findViewById(R.id.btnLayoutCategories)
        btnSettingsMain = findViewById(R.id.btnSettingsMain)
        rvCategories = findViewById(R.id.rvCategories)
        rvCategories.layoutManager = GridLayoutManager(this, 2)
        rvCategories.setHasFixedSize(true)
        rvCategories.itemAnimator = null
        rvCategories.setItemViewCacheSize(16)

        btnBackFromCategory = findViewById(R.id.btnBackFromCategory)
        tvCategoryHeaderTitle = findViewById(R.id.tvCategoryHeaderTitle)
        tvPlayerStatus = findViewById(R.id.tvPlayerStatus)
        glassModalOverlay = findViewById(R.id.glassModalOverlay)
        glassModalCard = findViewById(R.id.glassModalCard)
        tvGlassModalTitle = findViewById(R.id.tvGlassModalTitle)
        rvGlassModalOptions = findViewById(R.id.rvGlassModalOptions)
        rvGlassModalOptions.layoutManager = LinearLayoutManager(this)
        glassModalOverlay.setOnClickListener { closeGlassModal() }
        rvGlassModalOptions.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                resetGlassModalAutoHideTimer()
            }
        })
        btnLayoutChannels = findViewById(R.id.btnLayoutChannels)
        btnSearchCategory = findViewById(R.id.btnSearchCategory)
        rvCategoryChannels = findViewById(R.id.rvCategoryChannels)
        rvCategoryChannels.layoutManager = GridLayoutManager(this, 2)
        rvCategoryChannels.setHasFixedSize(true)
        rvCategoryChannels.itemAnimator = null
        rvCategoryChannels.setItemViewCacheSize(20)

        playerView = findViewById(R.id.playerView)
        videoPictureOverlay = findViewById(R.id.videoPictureOverlay)
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
        btnOsdFavorite = findViewById(R.id.btnOsdFavorite)
        btnOsdPip = findViewById(R.id.btnOsdPip)
        btnOsdAspect = findViewById(R.id.btnOsdAspect)
        btnOsdQuality = findViewById(R.id.btnOsdQuality)

        floatingPipContainer = findViewById(R.id.floatingPipContainer)
        pipPlayerView = findViewById(R.id.pipPlayerView)
        tvPipTitle = findViewById(R.id.tvPipTitle)
        btnPipExpand = findViewById(R.id.btnPipExpand)
        btnPipClose = findViewById(R.id.btnPipClose)

        quickChannelOverlay = findViewById(R.id.quickChannelOverlay)
        btnQuickPrevCat = findViewById(R.id.btnQuickPrevCat)
        btnQuickNextCat = findViewById(R.id.btnQuickNextCat)
        btnQuickClose = findViewById(R.id.btnQuickClose)
        tvQuickCategoryTitle = findViewById(R.id.tvQuickCategoryTitle)
        rvQuickChannels = findViewById(R.id.rvQuickChannels)
        rvQuickChannels.layoutManager = LinearLayoutManager(this)
        rvQuickChannels.setHasFixedSize(true)
        rvQuickChannels.itemAnimator = null
        rvQuickChannels.setItemViewCacheSize(16)

        programInfoOverlay = findViewById(R.id.programInfoOverlay)
        tvInfoShowTitle = findViewById(R.id.tvInfoShowTitle)
        tvInfoStreamSpecs = findViewById(R.id.tvInfoStreamSpecs)
        tvInfoShowDescription = findViewById(R.id.tvInfoShowDescription)

        btnCloseSettings = findViewById(R.id.btnCloseSettings)
        btnSetHiddenCategories = findViewById(R.id.btnSetHiddenCategories)
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
        btnSettingsMain.setOnClickListener {
            switchScreen(Screen.SETTINGS)
            btnSetHiddenCategories.requestFocus()
        }
        btnCloseSettings.setOnClickListener { showCategoriesScreen() }

        btnSetHiddenCategories.setOnClickListener { showHiddenCategoriesDialog() }
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

        btnSetRefreshPlaylist.setOnClickListener { loadActivePlaylistAndEpg(forceRefresh = true); showCategoriesScreen() }
        btnSetRefreshEpg.setOnClickListener { loadActivePlaylistAndEpg(forceRefresh = true); showCategoriesScreen() }

        btnSearchMain.setOnClickListener { showCinemaCatalogSearchDialog() }
        btnLayoutCategories.setOnClickListener { showCategoriesLayoutDialog() }
        btnLayoutChannels.setOnClickListener { showChannelsLayoutDialog() }
        btnSearchCategory.setOnClickListener { showCinemaCatalogSearchDialog() }

        // ФИЛЬТРЫ ПО ГОДУ ВЫПУСКА, ЖАНРУ И СТРАНЕ

        btnSetPlaylists.setOnClickListener { showPlaylistManagerDialog() }
        btnSetEpg.setOnClickListener { showEpgSelectorDialog() }
        btnSetVideoAudio.setOnClickListener { showVideoAudioMenuDialog() }
        btnSetBuffer.setOnClickListener { showBufferDialog() }

        btnBackFromCategory.setOnClickListener { showCategoriesScreen() }

        tabLiveProgram.setOnClickListener { selectPlayerTab(archive = false) }
        tabArchiveProgram.setOnClickListener { selectPlayerTab(archive = true) }

        btnOsdFullscreen.setOnClickListener {
            if (isFullscreenMode) {
                enterPreviewMode()
            } else {
                enterFullscreenMode()
            }
        }

        osdSeekBar.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN) {
                when (keyCode) {
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER -> {
                        togglePlayPause()
                        return@setOnKeyListener true
                    }
                    KeyEvent.KEYCODE_DPAD_LEFT -> {
                        if (activeArchiveProgram != null) {
                            seekArchiveBySeconds(-60L)
                            return@setOnKeyListener true
                        }
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        if (activeArchiveProgram != null) {
                            seekArchiveBySeconds(60L)
                            return@setOnKeyListener true
                        }
                    }
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

        btnPipExpand.setOnKeyListener { _, keyCode, event ->
            if (event.action == KeyEvent.ACTION_DOWN && keyCode == KeyEvent.KEYCODE_DPAD_LEFT) {
                returnFocusFromPipToMainScreen()
                return@setOnKeyListener true
            }
            false
        }

        btnOsdAspect.setOnClickListener { showAspectRatioDialog() }
        btnOsdQuality.setOnClickListener { showVideoAudioMenuDialog() }

        btnQuickPrevCat.setOnClickListener {
            if (categories.isNotEmpty()) {
                quickBrowseCategoryIndex = (quickBrowseCategoryIndex - 1 + categories.size) % categories.size
                renderQuickCategoryList()
            }
            resetQuickChannelsAutoHideTimer()
        }

        btnQuickNextCat.setOnClickListener {
            if (categories.isNotEmpty()) {
                quickBrowseCategoryIndex = (quickBrowseCategoryIndex + 1) % categories.size
                renderQuickCategoryList()
            }
            resetQuickChannelsAutoHideTimer()
        }

        btnQuickClose.setOnClickListener {
            closeQuickChannelList()
        }

        rvQuickChannels.addOnScrollListener(object : RecyclerView.OnScrollListener() {
            override fun onScrollStateChanged(recyclerView: RecyclerView, newState: Int) {
                if (quickChannelOverlay.visibility == View.VISIBLE) {
                    resetQuickChannelsAutoHideTimer()
                }
            }
        })

        programInfoOverlay.setOnClickListener {
            programInfoOverlay.visibility = View.GONE
            playerView.requestFocus()
        }

        val playerGestureDetector = GestureDetector(this, object : GestureDetector.SimpleOnGestureListener() {
            override fun onDown(e: MotionEvent): Boolean = true

            override fun onSingleTapConfirmed(e: MotionEvent): Boolean {
                if (programInfoOverlay.visibility == View.VISIBLE) {
                    programInfoOverlay.visibility = View.GONE
                    playerView.requestFocus()
                    return true
                }
                if (quickChannelOverlay.visibility == View.VISIBLE) {
                    closeQuickChannelList()
                    return true
                }
                if (isFullscreenMode) {
                    if (playerControlBar.visibility == View.VISIBLE) {
                        animateControlBarOut()
                    } else {
                        animateControlBarIn()
                    }
                } else {
                    enterFullscreenMode()
                }
                return true
            }

            override fun onFling(
                e1: MotionEvent?,
                e2: MotionEvent,
                velocityX: Float,
                velocityY: Float
            ): Boolean {
                if (!isFullscreenMode || e1 == null) return false
                val diffX = e2.x - e1.x
                val diffY = e2.y - e1.y
                val swipeThreshold = dpToPxFloat(60f)
                val velocityThreshold = 100f

                if (abs(diffX) > abs(diffY)) {
                    if (abs(diffX) > swipeThreshold && abs(velocityX) > velocityThreshold) {
                        if (programInfoOverlay.visibility == View.VISIBLE) {
                            programInfoOverlay.visibility = View.GONE
                        }
                        if (quickChannelOverlay.visibility == View.VISIBLE) {
                            closeQuickChannelList()
                        }
                        if (playerControlBar.visibility == View.VISIBLE) {
                            animateControlBarOut()
                        }
                        if (diffX < 0) {
                            // Свайп влево -> следующий канал
                            switchChannelInCategoryInstant(1)
                        } else {
                            // Свайп вправо -> предыдущий канал
                            switchChannelInCategoryInstant(-1)
                        }
                        return true
                    }
                } else {
                    if (abs(diffY) > swipeThreshold && abs(velocityY) > velocityThreshold) {
                        if (diffY > 0) {
                            // Свайп вниз -> информация о канале и трансляции (или закрыть меню каналов, если открыто)
                            if (quickChannelOverlay.visibility == View.VISIBLE) {
                                closeQuickChannelList()
                            } else {
                                openProgramInfoCard()
                            }
                            return true
                        } else {
                            // Свайп вверх -> боковое меню выбора каналов (или закрыть карточку инфо, если открыта)
                            if (programInfoOverlay.visibility == View.VISIBLE) {
                                programInfoOverlay.visibility = View.GONE
                                playerView.requestFocus()
                            } else {
                                openQuickChannelList()
                            }
                            return true
                        }
                    }
                }
                return false
            }
        })

        val audioManager = getSystemService(Context.AUDIO_SERVICE) as AudioManager
        var isTwoFingerGesture = false
        var twoFingerLastY = 0f

        playerView.setOnTouchListener { _, event ->
            if (isFullscreenMode) {
                when (event.actionMasked) {
                    MotionEvent.ACTION_POINTER_DOWN -> {
                        if (event.pointerCount == 2) {
                            isTwoFingerGesture = true
                            twoFingerLastY = (event.getY(0) + event.getY(1)) / 2f
                            return@setOnTouchListener true
                        }
                    }
                    MotionEvent.ACTION_MOVE -> {
                        if (isTwoFingerGesture && event.pointerCount >= 2) {
                            val currentY = (event.getY(0) + event.getY(1)) / 2f
                            val deltaY = currentY - twoFingerLastY
                            val stepThreshold = dpToPxFloat(28f)
                            if (abs(deltaY) >= stepThreshold) {
                                val maxVol = audioManager.getStreamMaxVolume(AudioManager.STREAM_MUSIC).coerceAtLeast(1)
                                val curVol = audioManager.getStreamVolume(AudioManager.STREAM_MUSIC)
                                val newVol = if (deltaY < 0) {
                                    (curVol + 1).coerceAtMost(maxVol)
                                } else {
                                    (curVol - 1).coerceAtLeast(0)
                                }
                                audioManager.setStreamVolume(AudioManager.STREAM_MUSIC, newVol, 0)
                                val pct = ((newVol * 100f) / maxVol).toInt().coerceIn(0, 100)
                                val icon = if (newVol == 0) "🔇" else "🔊"
                                showMiniZapNotification("$icon Громкость", "$pct%")
                                twoFingerLastY = currentY
                            }
                            return@setOnTouchListener true
                        }
                    }
                    MotionEvent.ACTION_POINTER_UP -> {
                        if (isTwoFingerGesture && event.pointerCount <= 2) {
                            return@setOnTouchListener true
                        }
                    }
                    MotionEvent.ACTION_UP, MotionEvent.ACTION_CANCEL -> {
                        if (isTwoFingerGesture) {
                            isTwoFingerGesture = false
                            return@setOnTouchListener true
                        }
                    }
                }
            }
            if (isTwoFingerGesture) {
                return@setOnTouchListener true
            }
            playerGestureDetector.onTouchEvent(event)
        }
    }

    private fun showCinemaCatalogSearchDialog() {
        val layout = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(40, 20, 40, 10)
        }
        val etQuery = EditText(this).apply {
            hint = "Введите название канала..."
            isFocusable = true
        }
        layout.addView(etQuery)

        AlertDialog.Builder(this)
            .setTitle("Поиск канала")
            .setView(layout)
            .setPositiveButton("Найти") { _, _ ->
                val q = etQuery.text.toString().trim().lowercase(Locale.getDefault())
                if (q.isNotEmpty()) {
                    val found = allChannels.filter { ch ->
                        ch.name.lowercase(Locale.getDefault()).contains(q)
                    }
                    openCategory("Поиск: $q", found)
                }
            }
            .setNegativeButton("Отмена", null)
            .show()
    }

    private fun resetGlassModalAutoHideTimer() {
        handler.removeCallbacks(hideGlassModalRunnable)
        handler.removeCallbacks(sleepTimerRunnable)
        if (glassModalOverlay.visibility == View.VISIBLE && currentScreen == Screen.PLAYER) {
            handler.postDelayed(hideGlassModalRunnable, 10000L)
        }
    }

    private fun showGlassModal(title: String, items: List<String>, onSelect: (Int) -> Unit) {
        if (glassModalOverlay.visibility != View.VISIBLE) {
            modalReturnFocusView = currentFocus
        }
        handler.removeCallbacks(hideFullControlsRunnable)
        handler.removeCallbacks(autoHidePreviewRunnable)

        tvGlassModalTitle.text = title
        val theme = themePresets.getOrElse(themePresetIndex) { themePresets[0] }
        val ambilight = neonGlowPresets.getOrElse(neonGlowIndex) { neonGlowPresets[1] }
        glassModalCard.background = buildBorderlessGlassPanel(theme)
        applyAmbilightGlow(glassModalCard, ambilight)

        rvGlassModalOptions.adapter = object : RecyclerView.Adapter<RecyclerView.ViewHolder>() {
            override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecyclerView.ViewHolder {
                val v = LayoutInflater.from(parent.context).inflate(R.layout.item_glass_option, parent, false)
                return object : RecyclerView.ViewHolder(v) {}
            }

            override fun onBindViewHolder(holder: RecyclerView.ViewHolder, position: Int) {
                val tv = holder.itemView as TextView
                tv.text = items[position]
                tv.background = buildBorderlessGlassTile(theme, radiusDp = 12f)
                tv.setOnFocusChangeListener { _, hasFocus ->
                    tv.setTextColor(if (hasFocus) Color.parseColor("#0F172A") else Color.WHITE)
                    if (hasFocus) resetGlassModalAutoHideTimer()
                }
                tv.setOnClickListener {
                    closeGlassModal()
                    onSelect(position)
                }
            }

            override fun getItemCount(): Int = items.size
        }

        glassModalOverlay.visibility = View.VISIBLE
        rvGlassModalOptions.post { rvGlassModalOptions.requestFocus() }
        resetGlassModalAutoHideTimer()
    }

    private fun closeGlassModal() {
        handler.removeCallbacks(hideGlassModalRunnable)
        glassModalOverlay.visibility = View.GONE
        val target = modalReturnFocusView
        modalReturnFocusView = null
        if (target != null && target.isShown) {
            target.post { target.requestFocus() }
        } else if (currentScreen == Screen.PLAYER) {
            playerView.post { playerView.requestFocus() }
        }
        if (currentScreen == Screen.PLAYER) {
            if (isFullscreenMode && playerControlBar.visibility == View.VISIBLE) {
                handler.removeCallbacks(hideFullControlsRunnable)
                handler.postDelayed(hideFullControlsRunnable, 6000L)
            } else if (!isFullscreenMode) {
                resetPreviewAutoHideTimer()
            }
        }
    }

    private fun applyCategoryFilters() {
        rvCategoryChannels.adapter = ChannelAdapter(
            channels = currentCategoryChannels,
            liveLookup = { getLiveProgram(it) },
            logoLookup = { resolveChannelLogo(it) }
        ) { selectedChannel ->
            if (isInAppPipActive) {
                closeInAppPip(stopPlayback = false)
            }
            openPlayerPreview(selectedChannel)
        }
        rvCategoryChannels.post { rvCategoryChannels.requestFocus() }
    }

    private fun returnFocusFromPipToMainScreen() {
        when (currentScreen) {
            Screen.CATEGORIES -> rvCategories.requestFocus()
            Screen.CATEGORY_CHANNELS -> rvCategoryChannels.requestFocus()
            Screen.SETTINGS -> btnSetColorTheme.requestFocus()
            Screen.PLAYER -> playerView.requestFocus()
        }
    }

    private fun isFullVodArchiveStream(totalSec: Long): Boolean {
        return player.isCurrentMediaItemSeekable &&
            !player.isCurrentMediaItemLive &&
            player.duration >= totalSec * 800L
    }

    private fun currentArchivePosSeconds(totalSec: Long): Long {
        if (isFullVodArchiveStream(totalSec)) {
            return (player.currentPosition / 1000L).coerceIn(0L, totalSec)
        }
        val elapsedSec = (player.currentPosition / 1000L).coerceAtLeast(0L)
        return (archiveOffsetSeconds + elapsedSec).coerceIn(0L, totalSec)
    }

    private fun togglePlayPause() {
        if (player.isPlaying) {
            player.pause()
            showMiniZapNotification("⏸ Пауза", activeChannel?.name ?: "")
        } else {
            player.play()
            showMiniZapNotification("▶ Воспроизведение", activeChannel?.name ?: "")
        }
    }

    private fun seekArchiveBySeconds(deltaSec: Long) {
        val ch = activeChannel ?: return
        val prog = activeArchiveProgram ?: return
        val totalSec = ((prog.endMs - prog.startMs) / 1000L).coerceAtLeast(60L)

        if (isFullVodArchiveStream(totalSec)) {
            val targetMs = (player.currentPosition + deltaSec * 1000L).coerceIn(0L, player.duration)
            player.seekTo(targetMs)
            archiveOffsetSeconds = (targetMs / 1000L).coerceIn(0L, totalSec)
        } else {
            val curPos = currentArchivePosSeconds(totalSec)
            val newOffset = (curPos + deltaSec).coerceIn(0L, (totalSec - 15L).coerceAtLeast(0L))
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

        if (isFullVodArchiveStream(totalSec)) {
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
        val posSec = currentArchivePosSeconds(totalSec)
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

    private fun showLayoutModeDialog() {
        val modeNames = listOf("2 колонки", "3 столбца", "4 столбца", "Список")
        val catLabel = modeNames.getOrElse(catLayoutModeIndex) { "2 колонки" }
        val chanLabel = modeNames.getOrElse(chanLayoutModeIndex) { "2 колонки" }
        val options = listOf(
            "Вид меню категорий: $catLabel",
            "Вид меню каналов: $chanLabel"
        )
        showGlassModal("Режим отображения плиток", options) { which ->
            if (which == 0) {
                showCategoriesLayoutDialog()
            } else {
                showChannelsLayoutDialog()
            }
        }
    }

    private fun showCategoriesLayoutDialog() {
        val modes = listOf(
            "2 колонки",
            "Крупная плитка (3 столбца)",
            "Средняя плитка (4 столбца)",
            "Список (1 колонка)"
        )
        val labeled = modes.mapIndexed { idx, name ->
            (if (idx == catLayoutModeIndex) "✓ " else "   ") + name
        }
        showGlassModal("Вид меню категорий", labeled) { which ->
            catLayoutModeIndex = which
            getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE).edit()
                .putInt("cat_layout_mode_idx", catLayoutModeIndex)
                .apply()
            applyLayoutManagers()
            rvCategories.adapter = CategoryAdapter(categories) { name, list ->
                openCategory(name, list)
            }
        }
    }

    private fun showChannelsLayoutDialog() {
        val modes = listOf(
            "2 колонки",
            "Крупная плитка (3 столбца)",
            "Средняя плитка (4 столбца)",
            "Список (1 колонка)"
        )
        val labeled = modes.mapIndexed { idx, name ->
            (if (idx == chanLayoutModeIndex) "✓ " else "   ") + name
        }
        showGlassModal("Вид меню каналов", labeled) { which ->
            chanLayoutModeIndex = which
            getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE).edit()
                .putInt("chan_layout_mode_idx", chanLayoutModeIndex)
                .apply()
            applyLayoutManagers()
            if (currentCategoryChannels.isNotEmpty()) {
                applyCategoryFilters()
            }
        }
    }

    private fun applyLayoutManagers() {
        val modeNames = listOf("2 кол.", "3 ст.", "4 ст.", "Список")
        val catName = modeNames.getOrElse(catLayoutModeIndex) { "2 кол." }
        val chanName = modeNames.getOrElse(chanLayoutModeIndex) { "2 кол." }

        rvCategories.layoutManager = when (catLayoutModeIndex) {
            1 -> GridLayoutManager(this, 3)
            2 -> GridLayoutManager(this, 4)
            3 -> LinearLayoutManager(this)
            else -> GridLayoutManager(this, 2)
        }
        rvCategories.setHasFixedSize(true)
        rvCategories.itemAnimator = null
        rvCategories.setItemViewCacheSize(16)

        rvCategoryChannels.layoutManager = when (chanLayoutModeIndex) {
            1 -> GridLayoutManager(this, 3)
            2 -> GridLayoutManager(this, 4)
            3 -> LinearLayoutManager(this)
            else -> GridLayoutManager(this, 2)
        }
        rvCategoryChannels.setHasFixedSize(true)
        rvCategoryChannels.itemAnimator = null
        rvCategoryChannels.setItemViewCacheSize(20)
    }

    private fun showColorThemeDialog() {
        val names = themePresets.mapIndexed { idx, t ->
            (if (idx == themePresetIndex) "✓ " else "   ") + t.name
        }
        showGlassModal("Цветовая тема интерфейса", names) { which ->
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
        }
    }

    private fun showNeonGlowDialog() {
        val names = neonGlowPresets.mapIndexed { idx, g ->
            (if (idx == neonGlowIndex) "✓ " else "   ") + g.name
        }
        showGlassModal("Свечение Ambilight под окнами", names) { which ->
            neonGlowIndex = which
            getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE).edit()
                .putInt("neon_glow_idx", neonGlowIndex)
                .apply()
            applyThemeAndNeonVisuals()
        }
    }

    private fun applyThemeAndNeonVisuals() {
        val theme = themePresets.getOrElse(themePresetIndex) { themePresets[0] }
        val ambilight = neonGlowPresets.getOrElse(neonGlowIndex) { neonGlowPresets[1] }

        btnSetColorTheme.text = "Цветовая тема интерфейса: ${theme.name}"
        btnSetNeonGlow.text = "Свечение Ambilight под окнами: ${ambilight.name}"

        tvAppLogoTitle.setTextColor(theme.accentColor)
        tvOpacityPercent.setTextColor(theme.accentColor)
        osdSeekBar.progressTintList = ColorStateList.valueOf(theme.accentColor)
        osdSeekBar.thumb = buildThemedSeekbarThumb(theme.accentColor)

        val panels = listOf(
            topBarCategories,
            topBarChannels,
            topBarSettings,
            playerControlBar,
            playerRightPanel,
            quickChannelOverlay,
            programInfoOverlay,
            miniZapBanner,
            floatingPipContainer,
            glassModalCard
        )
        for (panel in panels) {
            panel.background = buildBorderlessGlassPanel(theme)
            applyAmbilightGlow(panel, ambilight)
        }

        val settingsRows = listOf(
            opacitySliderCard,
            btnSetHiddenCategories,
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

    private fun buildThemedSeekbarThumb(accentColor: Int): StateListDrawable {
        val focusedSize = dpToPx(24)
        val normalSize = dpToPx(18)
        val focusedDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setSize(focusedSize, focusedSize)
            setColor(Color.WHITE)
            setStroke(dpToPx(3), accentColor)
        }
        val normalDrawable = GradientDrawable().apply {
            shape = GradientDrawable.OVAL
            setSize(normalSize, normalSize)
            setColor(accentColor)
            setStroke(dpToPx(2), Color.WHITE)
        }
        return StateListDrawable().apply {
            addState(intArrayOf(android.R.attr.state_focused), focusedDrawable)
            addState(intArrayOf(android.R.attr.state_pressed), focusedDrawable)
            addState(StateSet.WILD_CARD, normalDrawable)
        }
    }

    private fun buildBorderlessGlassPanel(theme: UiThemePreset): GradientDrawable {
        return GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(theme.bgStartColor, theme.bgEndColor)
        ).apply {
            cornerRadius = dpToPx(20).toFloat()
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
        glassModalCard.alpha = alphaFloat
        topBarCategories.alpha = alphaFloat
        topBarChannels.alpha = alphaFloat
        // Не ставим alpha < 1f на весь RecyclerView, чтобы не создавать тяжёлый offscreen-буфер на ТВ с 2 ГБ ОЗУ
        rvCategories.alpha = 1.0f
        rvCategoryChannels.alpha = 1.0f
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
            osdSeekBar.requestFocus()
        } else {
            osdSeekBar.requestFocus()
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
                        if (isFullscreenMode && glassModalOverlay.visibility != View.VISIBLE) playerView.requestFocus()
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
        handler.removeCallbacks(hideQuickChannelsRunnable)
        handler.removeCallbacks(hideGlassModalRunnable)
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

        playerView.player = null
        player.clearVideoSurface()

        tvPipTitle.text = ch.name
        pipPlayerView.resizeMode = playerView.resizeMode
        (pipPlayerView.videoSurfaceView as? android.view.SurfaceView)?.setZOrderMediaOverlay(true)
        floatingPipContainer.visibility = View.VISIBLE

        showCategoriesScreen()
        floatingPipContainer.post {
            if (isInAppPipActive) {
                pipPlayerView.player = player
                if (!player.isPlaying) {
                    player.play()
                }
            }
        }
        Toast.makeText(this, "Мини-окно активно. Нажмите ▶ вправо с правой колонки, чтобы управлять окном", Toast.LENGTH_SHORT).show()
    }

    private fun expandFromInAppPip() {
        isInAppPipActive = false
        pipPlayerView.player = null
        player.clearVideoSurface()
        floatingPipContainer.visibility = View.GONE
        activeChannel?.let { ch ->
            switchScreen(Screen.PLAYER)
            updateOsdInfo(ch, activeArchiveProgram)
            enterFullscreenMode()
            playerView.post {
                playerView.player = player
                if (!player.isPlaying) {
                    player.play()
                }
            }
        }
    }

    private fun closeInAppPip(stopPlayback: Boolean) {
        isInAppPipActive = false
        pipPlayerView.player = null
        player.clearVideoSurface()
        floatingPipContainer.visibility = View.GONE
        if (stopPlayback) {
            player.stop()
        }
        playerView.player = player
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

        val decoderNames = arrayOf("Аппаратный (Авто)", "Аппаратный + Туннель", "Программный")
        val decoderLabel = decoderNames.getOrElse(decoderMode) { "Аппаратный (Авто)" }

        val sharpNames = arrayOf("Мягкая", "Стандарт", "Высокая", "Максимум")
        val sharpLabel = sharpNames.getOrElse(sharpnessLevel) { "Стандарт" }

        val sleepLabel = if (sleepTimerMinutes > 0 && sleepTimerEndMs > System.currentTimeMillis()) {
            val leftMin = ((sleepTimerEndMs - System.currentTimeMillis()) / 60000L).coerceAtLeast(1L)
            "Через $leftMin мин"
        } else {
            "Выкл"
        }

        val mainItems = listOf(
            "1. Масштаб экрана: ${aspectNames[resizeModeIndex]}",
            "2. Качество видео: $qualityLabel",
            "3. Аудиодорожка (сейчас: $currentAudio)",
            "4. Субтитры",
            "5. Декодер видео: $decoderLabel",
            "6. Яркость и резкость ($brightnessPercent% / $sharpLabel)",
            "7. Таймер сна: $sleepLabel"
        )

        showGlassModal("Настройки видео и аудио", mainItems) { which ->
            when (which) {
                0 -> showAspectRatioDialog()
                1 -> showVideoQualityDialog()
                2 -> showAudioTracksDialog()
                3 -> showSubtitlesDialog()
                4 -> showDecoderModeDialog()
                5 -> showBrightnessSharpnessMenu()
                6 -> showSleepTimerDialog()
            }
        }
    }

    private fun showDecoderModeDialog() {
        val modes = listOf(
            "Аппаратный (Авто — рекомендуется)",
            "Аппаратный + Туннелирование (4K / 50fps)",
            "Программный (для проблемных каналов)"
        )
        val labeled = modes.mapIndexed { idx, name ->
            (if (idx == decoderMode) "✓ " else "   ") + name
        }
        showGlassModal("Режим декодера видео", labeled) { which ->
            decoderMode = which
            getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
                .edit().putInt("decoder_mode", decoderMode).apply()
            initPlayerWithBuffer(bufferSeconds)
            activeChannel?.let { ch ->
                val url = activeArchiveProgram?.let { prog ->
                    Parsers.buildArchiveUrl(ch, prog, archiveOffsetSeconds)
                } ?: ch.url
                playStream(url)
            }
            Toast.makeText(this, "Декодер: ${modes[which].substringBefore(" (")}", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showBrightnessSharpnessMenu() {
        val sharpNames = arrayOf("Мягкая (сглаживание)", "Стандарт (ТВ)", "Высокая чёткость", "Максимум (Усиленный контраст)")
        val options = listOf(
            "Яркость картинки: $brightnessPercent%",
            "Резкость и чёткость: ${sharpNames.getOrElse(sharpnessLevel) { "Стандарт (ТВ)" }}",
            "Сбросить (100% / Стандарт)"
        )
        showGlassModal("Яркость и резкость", options) { which ->
            when (which) {
                0 -> showBrightnessDialog()
                1 -> showSharpnessDialog()
                2 -> {
                    brightnessPercent = 100
                    sharpnessLevel = 1
                    getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE).edit()
                        .putInt("brightness_pct", brightnessPercent)
                        .putInt("sharpness_lvl", sharpnessLevel)
                        .apply()
                    applyPictureSettings()
                    Toast.makeText(this, "Настройки картинки сброшены", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun showBrightnessDialog() {
        val values = intArrayOf(30, 50, 70, 85, 100, 115)
        val labels = values.map { v ->
            val mark = if (v == brightnessPercent) "✓ " else "   "
            val note = when (v) {
                100 -> " (Стандарт)"
                115 -> " (Повышенная)"
                else -> ""
            }
            "${mark}Яркость $v%$note"
        }
        showGlassModal("Выберите яркость", labels) { which ->
            brightnessPercent = values[which]
            getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
                .edit().putInt("brightness_pct", brightnessPercent).apply()
            applyPictureSettings()
        }
    }

    private fun showSharpnessDialog() {
        val names = listOf(
            "Мягкая (для SD-каналов)",
            "Стандарт (Нативная чёткость ТВ)",
            "Высокая чёткость (Чёткие контуры)",
            "Максимум (Кинотеатральный контраст)"
        )
        val labeled = names.mapIndexed { idx, name ->
            (if (idx == sharpnessLevel) "✓ " else "   ") + name
        }
        showGlassModal("Выберите резкость", labeled) { which ->
            sharpnessLevel = which
            getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
                .edit().putInt("sharpness_lvl", sharpnessLevel).apply()
            applyPictureSettings()
        }
    }

    private fun applyPictureSettings() {
        // 1. Системная яркость окна (для экранов с управлением подсветкой)
        val lp = window.attributes
        lp.screenBrightness = if (brightnessPercent >= 100) {
            1.0f
        } else {
            (brightnessPercent / 100f).coerceIn(0.15f, 1.0f)
        }
        window.attributes = lp

        // 2. Программный слой яркости/контраста поверх видео (работает на любых Android TV по HDMI)
        val overlayColor = when {
            brightnessPercent < 100 -> {
                val dimAlpha = (((100 - brightnessPercent) * 1.9f).toInt()).coerceIn(0, 175)
                Color.argb(dimAlpha, 0, 0, 0)
            }
            brightnessPercent > 100 -> {
                Color.argb(24, 255, 255, 255)
            }
            else -> {
                when (sharpnessLevel) {
                    0 -> Color.argb(18, 22, 26, 36)
                    2 -> Color.argb(14, 0, 0, 0)
                    3 -> Color.argb(26, 0, 0, 0)
                    else -> Color.TRANSPARENT
                }
            }
        }
        videoPictureOverlay.setBackgroundColor(overlayColor)

        if (::player.isInitialized) {
            player.videoScalingMode = if (sharpnessLevel >= 2) {
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT_WITH_CROPPING
            } else {
                C.VIDEO_SCALING_MODE_SCALE_TO_FIT
            }
        }
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
        val paramsBuilder = player.trackSelectionParameters
            .buildUpon()
            .setMaxVideoSize(maxW, selectedMaxHeight)
            .setForceHighestSupportedBitrate(forceMaxBitrate)
            .setMaxVideoBitrate(Int.MAX_VALUE)

        val ts = player.trackSelector
        if (ts is DefaultTrackSelector) {
            ts.setParameters(
                ts.buildUponParameters()
                    .setMaxVideoSize(maxW, selectedMaxHeight)
                    .setForceHighestSupportedBitrate(forceMaxBitrate)
                    .setTunnelingEnabled(decoderMode == 1)
                    .build()
            )
        } else {
            player.trackSelectionParameters = paramsBuilder.build()
        }
    }

    private fun showSubtitlesDialog() {
        val labels = mutableListOf<String>()
        val overrides = mutableListOf<TrackSelectionOverride?>()
        var activeIdx = 0

        labels.add("Выключить субтитры")
        overrides.add(null)

        val textGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_TEXT }
        for (group in textGroups) {
            for (tIdx in 0 until group.length) {
                val fmt = group.getTrackFormat(tIdx)
                val langCode = fmt.language ?: "und"
                val langTitle = when (langCode.lowercase(Locale.US)) {
                    "ru", "rus" -> "Русский"
                    "en", "eng" -> "Английский"
                    "uk", "ukr" -> "Украинский"
                    "de", "deu", "ger" -> "Немецкий"
                    "fr", "fra", "fre" -> "Французский"
                    else -> langCode.uppercase(Locale.US)
                }
                val extra = fmt.label?.takeIf { it.isNotBlank() }?.let { " — $it" } ?: ""
                labels.add("Субтитры ${labels.size}: $langTitle$extra")
                overrides.add(TrackSelectionOverride(group.mediaTrackGroup, tIdx))
                if (group.isTrackSelected(tIdx)) {
                    activeIdx = labels.size - 1
                }
            }
        }

        val labeled = labels.mapIndexed { idx, name ->
            (if (idx == activeIdx) "✓ " else "   ") + name
        }

        showGlassModal("Выбор субтитров", labeled) { which ->
            val builder = player.trackSelectionParameters.buildUpon()
            builder.clearOverridesOfType(C.TRACK_TYPE_TEXT)
            val ov = overrides[which]
            if (ov == null) {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, true)
                Toast.makeText(this, "Субтитры выключены", Toast.LENGTH_SHORT).show()
            } else {
                builder.setTrackTypeDisabled(C.TRACK_TYPE_TEXT, false)
                builder.addOverride(ov)
                Toast.makeText(this, "Включены: ${labels[which]}", Toast.LENGTH_SHORT).show()
            }
            player.trackSelectionParameters = builder.build()
        }
    }

    private fun showSleepTimerDialog() {
        val values = intArrayOf(0, 15, 30, 60, 90)
        val names = listOf("Выключить таймер", "Через 15 минут", "Через 30 минут", "Через 60 минут", "Через 90 минут")
        val labeled = names.mapIndexed { idx, name ->
            (if (values[idx] == sleepTimerMinutes) "✓ " else "   ") + name
        }
        showGlassModal("Таймер сна", labeled) { which ->
            handler.removeCallbacks(sleepTimerRunnable)
            sleepTimerMinutes = values[which]
            if (sleepTimerMinutes > 0) {
                val delayMs = sleepTimerMinutes * 60_000L
                sleepTimerEndMs = System.currentTimeMillis() + delayMs
                handler.postDelayed(sleepTimerRunnable, delayMs)
                Toast.makeText(this, "Таймер сна установлен на $sleepTimerMinutes мин", Toast.LENGTH_SHORT).show()
            } else {
                sleepTimerEndMs = 0L
                Toast.makeText(this, "Таймер сна выключен", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun showHiddenCategoriesDialog() {
        val allGroupNames = allChannels.map { it.category }.distinct()
        if (allGroupNames.isEmpty()) {
            Toast.makeText(this, "Сначала загрузите плейлист", Toast.LENGTH_SHORT).show()
            return
        }
        val items = mutableListOf("Показать все категории (сброс)")
        allGroupNames.forEach { cat ->
            val hidden = hiddenCategories.contains(cat)
            items.add((if (hidden) "🚫 Скрыта: " else "👁 ") + cat)
        }
        showGlassModal("Скрытие категорий", items) { which ->
            if (which == 0) {
                hiddenCategories.clear()
                Toast.makeText(this, "Все категории показаны", Toast.LENGTH_SHORT).show()
            } else {
                val cat = allGroupNames[which - 1]
                if (hiddenCategories.contains(cat)) {
                    hiddenCategories.remove(cat)
                } else {
                    hiddenCategories.add(cat)
                }
            }
            saveHiddenCategories()
            rebuildCategories()
            updateHiddenCategoriesButtonLabel()
            if (which > 0) {
                showHiddenCategoriesDialog()
            }
        }
    }

    private fun saveHiddenCategories() {
        val arr = JSONArray()
        hiddenCategories.forEach { arr.put(it) }
        getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
            .edit().putString("hidden_categories", arr.toString()).apply()
    }

    private fun updateHiddenCategoriesButtonLabel() {
        btnSetHiddenCategories.text = if (hiddenCategories.isEmpty()) {
            "Скрыть ненужные категории"
        } else {
            "Скрытые категории (${hiddenCategories.size})"
        }
    }

    private fun recordRecentChannel(channel: Channel) {
        val prev = recentChannelUrls.firstOrNull()
        if (prev != null && prev != channel.url) {
            previousChannelUrl = prev
        }
        recentChannelUrls.remove(channel.url)
        recentChannelUrls.add(0, channel.url)
        while (recentChannelUrls.size > 15) {
            recentChannelUrls.removeAt(recentChannelUrls.size - 1)
        }
        val arr = JSONArray()
        recentChannelUrls.forEach { arr.put(it) }
        getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
            .edit().putString("recent_channels", arr.toString()).apply()
        rebuildCategories()
    }

    private fun switchToPreviousChannel() {
        if (previousChannelUrl.isBlank()) {
            Toast.makeText(this, "Нет предыдущего канала", Toast.LENGTH_SHORT).show()
            return
        }
        val prevCh = allChannels.firstOrNull { it.url == previousChannelUrl } ?: return
        activeArchiveProgram = null
        archiveOffsetSeconds = 0L
        activeChannel = prevCh
        saveLastWatchedState(prevCh, isWatching = true)
        playStream(prevCh.url)
        updateOsdInfo(prevCh, null)
        val live = getLiveProgram(prevCh)
        showMiniZapNotification(prevCh.name, live?.title ?: "Возврат на предыдущий канал")
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

    private fun resetQuickChannelsAutoHideTimer() {
        handler.removeCallbacks(hideQuickChannelsRunnable)
        if (quickChannelOverlay.visibility == View.VISIBLE) {
            handler.postDelayed(hideQuickChannelsRunnable, 6000L)
        }
    }

    private fun closeQuickChannelList() {
        handler.removeCallbacks(hideQuickChannelsRunnable)
        quickChannelOverlay.visibility = View.GONE
        playerView.requestFocus()
    }

    private fun openQuickChannelList() {
        playerControlBar.visibility = View.GONE
        miniZapBanner.visibility = View.GONE
        programInfoOverlay.visibility = View.GONE
        quickChannelOverlay.visibility = View.VISIBLE

        quickBrowseCategoryIndex = categories.indexOfFirst { it.first == currentCategoryName }
            .takeIf { it >= 0 } ?: 0
        renderQuickCategoryList()
        resetQuickChannelsAutoHideTimer()
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
            forceListStyle = true
        ) { chosenChannel ->
            currentCategoryName = catTitle
            currentCategoryChannels = list
            activeChannel = chosenChannel
            activeArchiveProgram = null
            archiveOffsetSeconds = 0L
            playStream(chosenChannel.url)
            updateOsdInfo(chosenChannel, null)
            closeQuickChannelList()
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
        val metaInfo = listOfNotNull(
            ch?.year?.takeIf { it.isNotBlank() }?.let { "Год: $it" },
            ch?.genre?.takeIf { it.isNotBlank() }?.let { "Жанр: $it" },
            ch?.country?.takeIf { it.isNotBlank() }?.let { "Страна: $it" }
        ).joinToString(" • ")

        tvInfoStreamSpecs.text = if (metaInfo.isNotEmpty()) {
            "$metaInfo\nВидео: $vRes, аудио: $aLang ($aCh)"
        } else {
            "Видео: $vRes, аудио: $aLang ($aCh)"
        }

        val desc = prog?.description?.takeIf { it.isNotBlank() }
            ?: ch?.description?.takeIf { it.isNotBlank() }
            ?: "Описание передачи отсутствует в программе EPG для данного канала."
        tvInfoShowDescription.text = desc
    }

    private fun switchScreen(s: Screen) {
        currentScreen = s
        if (s != Screen.PLAYER) {
            handler.removeCallbacks(autoHidePreviewRunnable)
        }
        screenCategories.visibility = if (s == Screen.CATEGORIES) View.VISIBLE else View.GONE
        screenCategoryChannels.visibility = if (s == Screen.CATEGORY_CHANNELS) View.VISIBLE else View.GONE
        screenPlayer.visibility = if (s == Screen.PLAYER) View.VISIBLE else View.GONE
        screenSettings.visibility = if (s == Screen.SETTINGS) View.VISIBLE else View.GONE

        if (isInAppPipActive && s != Screen.PLAYER) {
            floatingPipContainer.visibility = View.VISIBLE
        }
    }

    private fun showCategoriesScreen() {
        updateTopPlaylistButtonTitle()
        switchScreen(Screen.CATEGORIES)
        rvCategories.adapter = CategoryAdapter(categories) { name, list ->
            openCategory(name, list)
        }
        rvCategories.post {
            if (categories.isNotEmpty()) rvCategories.requestFocus() else btnSettingsMain.requestFocus()
        }
    }

    private fun openCategory(name: String, list: List<Channel>) {
        currentCategoryName = name
        currentCategoryChannels = list
        tvCategoryHeaderTitle.text = name
        switchScreen(Screen.CATEGORY_CHANNELS)
        applyCategoryFilters()
    }

    private fun saveLastWatchedState(channel: Channel?, isWatching: Boolean) {
        if (isWatching && channel != null) {
            recordRecentChannel(channel)
        }
        val prefs = getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
        prefs.edit()
            .putBoolean("was_watching_channel", isWatching)
            .putString("last_channel_url", channel?.url ?: "")
            .putString("last_category_name", if (isWatching) currentCategoryName else "")
            .apply()
    }

    override fun onPause() {
        super.onPause()
        if (currentScreen == Screen.PLAYER && activeChannel != null) {
            saveLastWatchedState(activeChannel, isWatching = true)
            if (::player.isInitialized) {
                wasPlayingBeforePause = player.isPlaying
                if (!isInAppPipActive) {
                    player.pause()
                }
            }
        }
    }

    override fun onResume() {
        super.onResume()
        if (currentScreen == Screen.PLAYER && activeChannel != null && ::player.isInitialized) {
            if (wasPlayingBeforePause) {
                player.play()
                }
        }
    }

    private fun showExitConfirmModal() {
        val options = listOf("Да, выйти из AuraTV", "Отмена")
        showGlassModal("Выйти из приложения?", options) { which ->
            if (which == 0) {
                saveLastWatchedState(null, isWatching = false)
                if (::player.isInitialized) {
                    player.stop()
                }
                finish()
            }
        }
    }

    private fun openPlayerPreview(channel: Channel) {
        activeChannel = channel
        activeArchiveProgram = null
        archiveOffsetSeconds = 0L

        pipPlayerView.player = null
        playerView.player = null
        switchScreen(Screen.PLAYER)
        playerView.player = player

        saveLastWatchedState(channel, isWatching = true)
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
        osdSeekBar.isFocusable = true

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
            val metaLine = listOfNotNull(
                channel.year.takeIf { it.isNotBlank() },
                channel.genre.takeIf { it.isNotBlank() },
                channel.country.takeIf { it.isNotBlank() }
            ).joinToString(" • ")
            tvOsdNextShow.text = if (metaLine.isNotEmpty()) metaLine else "Прямой эфир"
            tvOsdTimeRight.text = timeFormat.format(Date())
            osdSeekBar.progress = 100
        }
    }

    private fun playStream(url: String) {
        if (playerView.player == null && !isInAppPipActive) {
            playerView.player = player
        }
        player.setMediaItem(MediaItem.fromUri(url))
        player.prepare()
        player.play()
    }

    private fun applyLoadedPlaylist(parsed: ParsedPlaylist, showCountToast: Boolean) {
        if (parsed.channels.isNotEmpty()) {
            allChannels = parsed.channels
            restoreFavorites()
            rebuildCategories()
            if (shouldAutoResumeLastChannel && pendingResumeChannelUrl.isNotBlank()) {
                shouldAutoResumeLastChannel = false
                val targetCh = allChannels.firstOrNull { it.url == pendingResumeChannelUrl }
                if (targetCh != null) {
                    val catPair = categories.firstOrNull { it.first == pendingResumeCategoryName }
                        ?: categories.firstOrNull { pair -> pair.second.any { it.url == targetCh.url } }
                    if (catPair != null) {
                        currentCategoryName = catPair.first
                        currentCategoryChannels = catPair.second
                    } else {
                        currentCategoryName = targetCh.category
                        currentCategoryChannels = allChannels.filter { it.category == targetCh.category }
                    }
                    openPlayerPreview(targetCh)
                    enterFullscreenMode()
                } else if (currentScreen == Screen.CATEGORIES) {
                    showCategoriesScreen()
                }
            } else if (currentScreen == Screen.CATEGORIES) {
                showCategoriesScreen()
            } else if (currentScreen == Screen.CATEGORY_CHANNELS) {
                val updated = categories.firstOrNull { it.first == currentCategoryName }?.second
                if (updated != null) {
                    currentCategoryChannels = updated
                    applyCategoryFilters()
                }
            }
            if (showCountToast) {
                Toast.makeText(this, "Каналов загружено: ${allChannels.size}", Toast.LENGTH_SHORT).show()
            }
        } else if (showCountToast) {
            Toast.makeText(this, "Плейлист пуст или недоступен", Toast.LENGTH_LONG).show()
        }
    }

    private fun applyLoadedXmltv(xmltv: ParsedXmltv) {
        if (xmltv.programsByKey.isNotEmpty() || xmltv.iconsByKey.isNotEmpty()) {
            epgMap = xmltv.programsByKey
            epgIconsMap = xmltv.iconsByKey
            if (currentScreen == Screen.CATEGORY_CHANNELS) {
                rvCategoryChannels.adapter?.notifyDataSetChanged()
            } else if (currentScreen == Screen.PLAYER) {
                selectPlayerTab(isArchiveTab)
                activeChannel?.let { updateOsdInfo(it, activeArchiveProgram) }
            }
        }
    }

    private fun loadActivePlaylistAndEpg(forceRefresh: Boolean = false) {
        val activePl = playlists.getOrNull(activePlaylistIndex) ?: playlists.first()
        val prefs = getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
        val savedEpgUrl = prefs.getString("epg_url", "") ?: ""

        val plCacheFile = File(filesDir, "playlist_cache_${activePl.url.hashCode()}.m3u")
        val epgCacheFile = File(filesDir, "epg_cache.xml.gz")
        val cacheMaxAgeMs = 12L * 3600L * 1000L
        val nowMs = System.currentTimeMillis()

        if (forceRefresh || !plCacheFile.exists()) {
            Toast.makeText(this, "Обновление: ${activePl.name}...", Toast.LENGTH_SHORT).show()
        }

        thread {
            var detectedEpgUrl: String? = null
            var loadedPlFromCache = false

            // 1. Быстрая загрузка плейлиста из локального файла
            if (!forceRefresh && activePl.rawResId == 0 && plCacheFile.exists() && plCacheFile.length() > 0L) {
                try {
                    val cachedPl = Parsers.parseM3uFromFile(plCacheFile)
                    if (cachedPl.channels.isNotEmpty()) {
                        detectedEpgUrl = cachedPl.detectedEpgUrl
                        loadedPlFromCache = true
                        runOnUiThread { applyLoadedPlaylist(cachedPl, showCountToast = false) }
                    }
                } catch (_: Exception) { }
            }

            // 2. Быстрая загрузка телепрограммы EPG из локального файла
            var loadedEpgFromCache = false
            if (!forceRefresh && epgCacheFile.exists() && epgCacheFile.length() > 0L) {
                try {
                    val cachedXmltv = Parsers.parseXmltvFromFile(epgCacheFile)
                    if (cachedXmltv.programsByKey.isNotEmpty() || cachedXmltv.iconsByKey.isNotEmpty()) {
                        loadedEpgFromCache = true
                        runOnUiThread { applyLoadedXmltv(cachedXmltv) }
                    }
                } catch (_: Exception) { }
            }

            // 3. Скачивание свежего плейлиста только если кэш отсутствует, устарел (>12ч) или нажато «Обновить»
            val needPlNetwork = forceRefresh || !loadedPlFromCache || (nowMs - plCacheFile.lastModified() > cacheMaxAgeMs)
            if (needPlNetwork) {
                try {
                    val parsed = if (activePl.rawResId != 0) {
                        Parsers.parseM3uFromRaw(this, activePl.rawResId)
                    } else {
                        Parsers.downloadAndCacheM3u(activePl.url, plCacheFile)
                    }
                    detectedEpgUrl = parsed.detectedEpgUrl
                    runOnUiThread { applyLoadedPlaylist(parsed, showCountToast = forceRefresh || !loadedPlFromCache) }
                } catch (e: Exception) {
                    if (!loadedPlFromCache) {
                        runOnUiThread {
                            Toast.makeText(this, "Ошибка загрузки плейлиста: ${e.message}", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }

            // 4. Скачивание свежей телепрограммы EPG только если кэш отсутствует, устарел (>12ч) или нажато «Обновить»
            val needEpgNetwork = forceRefresh || !loadedEpgFromCache || (nowMs - epgCacheFile.lastModified() > cacheMaxAgeMs)
            if (needEpgNetwork) {
                val epgCandidates = listOfNotNull(
                    savedEpgUrl.takeIf { it.isNotBlank() },
                    detectedEpgUrl?.takeIf { it.isNotBlank() },
                    EPG_LITE_URL
                ).distinct()

                for (epgUrl in epgCandidates) {
                    try {
                        val xmltv = Parsers.downloadAndCacheXmltv(epgUrl, epgCacheFile)
                        if (xmltv.programsByKey.isNotEmpty() || xmltv.iconsByKey.isNotEmpty()) {
                            runOnUiThread {
                                applyLoadedXmltv(xmltv)
                                if (forceRefresh) {
                                    Toast.makeText(this, "Телепрограмма обновлена и сохранена", Toast.LENGTH_SHORT).show()
                                }
                            }
                            break
                        }
                    } catch (_: Exception) { }
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
        val byUrl = allChannels.associateBy { it.url }
        val recents = recentChannelUrls.mapNotNull { byUrl[it] }
        if (recents.isNotEmpty()) {
            result.add("Недавние" to recents)
        }
        val grouped = allChannels.groupBy { it.category }
        for ((cat, list) in grouped) {
            if (!hiddenCategories.contains(cat)) {
                result.add(cat to list)
            }
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
            if (glassModalOverlay.visibility == View.VISIBLE) {
                resetGlassModalAutoHideTimer()
                if (event.keyCode == KeyEvent.KEYCODE_BACK) {
                    closeGlassModal()
                    return true
                }
                return super.dispatchKeyEvent(event)
            }
            if (isInAppPipActive && floatingPipContainer.visibility == View.VISIBLE && event.keyCode == KeyEvent.KEYCODE_DPAD_RIGHT) {
                val focused = currentFocus
                if (focused != null && focused != btnPipExpand && focused != btnPipClose) {
                    val loc = IntArray(2)
                    focused.getLocationOnScreen(loc)
                    val screenWidth = resources.displayMetrics.widthPixels
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
                    resetQuickChannelsAutoHideTimer()
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
                            if (event.repeatCount == 2) {
                                switchToPreviousChannel()
                                return true
                            }
                            if (event.repeatCount == 0) {
                                animateControlBarIn()
                            }
                            return true
                        }
                        KeyEvent.KEYCODE_LAST_CHANNEL, KeyEvent.KEYCODE_0 -> {
                            switchToPreviousChannel()
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
                    saveLastWatchedState(null, isWatching = false)
                    player.stop()
                    openCategory(currentCategoryName, currentCategoryChannels)
                    return true
                }
                Screen.CATEGORY_CHANNELS -> {
                    showCategoriesScreen()
                    return true
                }
                Screen.SETTINGS -> {
                    showCategoriesScreen()
                    return true
                }
                Screen.CATEGORIES -> {
                    val now = System.currentTimeMillis()
                    if (now - lastBackPressMs <= 2500L) {
                        lastBackPressMs = 0L
                        showExitConfirmModal()
                    } else {
                        lastBackPressMs = now
                        Toast.makeText(this, "Нажмите «Назад» ещё раз для выхода", Toast.LENGTH_SHORT).show()
                    }
                    return true
                }
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
            saveLastWatchedState(nextCh, isWatching = true)
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
            .setNeutralButton("Удалить свой") { _, _ ->
                if (activePlaylistIndex >= 1 && playlists.size > 1) {
                    playlists.removeAt(activePlaylistIndex)
                    activePlaylistIndex = 0
                    savePlaylists()
                    updateTopPlaylistButtonTitle()
                    loadActivePlaylistAndEpg()
                } else {
                    Toast.makeText(this, "Основной плейлист VipLime защищён от удаления", Toast.LENGTH_SHORT).show()
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
            hint = "Название (например: Мой кинотеатр)"
            isFocusable = true
        }
        val etUrl = EditText(this).apply {
            hint = "https://... (ссылка на .m3u или .m3u8)"
            isFocusable = true
        }
        layout.addView(etName)
        layout.addView(etUrl)

        AlertDialog.Builder(this)
            .setTitle("Добавить плейлист / каталог M3U")
            .setView(layout)
            .setPositiveButton("Добавить и загрузить") { _, _ ->
                val url = etUrl.text.toString().trim()
                val name = etName.text.toString().trim().ifEmpty { "Каталог ${playlists.size + 1}" }
                if (url.isNotEmpty()) {
                    playlists.add(PlaylistEntry(name, url, rawResId = 0))
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
        val options = listOf(
            "VipLime Lite (быстрая загрузка EPG)",
            "VipLime Full (полная телепрограмма и описания)",
            "Ввести свою ссылку XMLTV"
        )
        showGlassModal("Источник телепрограммы EPG", options) { which ->
            when (which) {
                0 -> {
                    prefs.edit().putString("epg_url", EPG_LITE_URL).apply()
                    loadActivePlaylistAndEpg(forceRefresh = true)
                }
                1 -> {
                    prefs.edit().putString("epg_url", EPG_FULL_URL).apply()
                    loadActivePlaylistAndEpg(forceRefresh = true)
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
                            loadActivePlaylistAndEpg(forceRefresh = true)
                        }
                        .setNegativeButton("Отмена", null)
                        .show()
                }
            }
        }
    }

    private fun showBufferDialog() {
        val labels = listOf("Минимальный (2 сек)", "Средний (10 сек)", "Большой (25 сек)", "Максимальный (45 сек)")
        val values = intArrayOf(2, 10, 25, 45)
        showGlassModal("Буферизация видео", labels) { which ->
            bufferSeconds = values[which]
            getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
                .edit().putInt("buffer_sec", bufferSeconds).apply()
            btnSetBuffer.text = "Буферизация видео: $bufferSeconds сек"
            initPlayerWithBuffer(bufferSeconds)
            activeChannel?.let { playStream(it.url) }
            Toast.makeText(this, "Буфер установлен: $bufferSeconds сек", Toast.LENGTH_SHORT).show()
        }
    }

    private fun loadSavedPlaylistsAndSettings() {
        val prefs = getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE)
        bufferSeconds = prefs.getInt("buffer_sec", 10)
        selectedMaxHeight = prefs.getInt("max_video_height", 2160)
        forceMaxBitrate = prefs.getBoolean("force_max_bitrate", true)
        val legacyTunnel = prefs.getBoolean("hw_tunneling", false)
        decoderMode = prefs.getInt("decoder_mode", if (legacyTunnel) 1 else 0).coerceIn(0, 2)
        brightnessPercent = prefs.getInt("brightness_pct", 100)
        sharpnessLevel = prefs.getInt("sharpness_lvl", 1)
        applyPictureSettings()
        resizeModeIndex = prefs.getInt("resize_mode_idx", 0)
        glassAlphaPercent = prefs.getInt("glass_alpha_pct", 85).coerceIn(25, 100)
        wallpaperPresetIndex = prefs.getInt("wallpaper_idx", 0)
        customWallpaperUrl = prefs.getString("wallpaper_url", "") ?: ""
        themePresetIndex = prefs.getInt("theme_idx", 0).coerceIn(0, themePresets.size - 1)
        neonGlowIndex = prefs.getInt("neon_glow_idx", 1).coerceIn(0, neonGlowPresets.size - 1)
        val legacyLayout = prefs.getInt("layout_mode_idx", 0).coerceIn(0, 3)
        catLayoutModeIndex = prefs.getInt("cat_layout_mode_idx", legacyLayout).coerceIn(0, 3)
        chanLayoutModeIndex = prefs.getInt("chan_layout_mode_idx", legacyLayout).coerceIn(0, 3)
        applyLayoutManagers()

        recentChannelUrls.clear()
        try {
            val rArr = JSONArray(prefs.getString("recent_channels", "[]"))
            for (i in 0 until rArr.length()) {
                val u = rArr.optString(i)
                if (u.isNotBlank()) recentChannelUrls.add(u)
            }
            if (recentChannelUrls.size >= 2) {
                previousChannelUrl = recentChannelUrls[1]
            }
        } catch (_: Exception) { }

        hiddenCategories.clear()
        try {
            val hArr = JSONArray(prefs.getString("hidden_categories", "[]"))
            for (i in 0 until hArr.length()) {
                val c = hArr.optString(i)
                if (c.isNotBlank()) hiddenCategories.add(c)
            }
        } catch (_: Exception) { }
        updateHiddenCategoriesButtonLabel()

        shouldAutoResumeLastChannel = prefs.getBoolean("was_watching_channel", false)
        pendingResumeChannelUrl = prefs.getString("last_channel_url", "") ?: ""
        pendingResumeCategoryName = prefs.getString("last_category_name", "") ?: ""

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

        val jsonStr = prefs.getString("custom_playlists_json", null)
        if (!jsonStr.isNullOrBlank()) {
            try {
                val arr = JSONArray(jsonStr)
                for (i in 0 until arr.length()) {
                    val obj = arr.getJSONObject(i)
                    playlists.add(PlaylistEntry(obj.getString("name"), obj.getString("url"), rawResId = 0))
                }
            } catch (_: Exception) { }
        }
        if (activePlaylistIndex !in playlists.indices) activePlaylistIndex = 0
        updateTopPlaylistButtonTitle()
    }

    private fun savePlaylists() {
        val arr = JSONArray()
        for (i in 1 until playlists.size) {
            val pl = playlists[i]
            val obj = JSONObject()
            obj.put("name", pl.name)
            obj.put("url", pl.url)
            arr.put(obj)
        }
        getSharedPreferences("luma_viplime_prefs", Context.MODE_PRIVATE).edit()
            .putString("custom_playlists_json", arr.toString())
            .putInt("active_playlist_idx", activePlaylistIndex)
            .apply()
    }

    private fun updateTopPlaylistButtonTitle() {
        val name = playlists.getOrNull(activePlaylistIndex)?.name ?: playlists.first().name
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

        override fun getItemViewType(position: Int): Int = catLayoutModeIndex

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val layoutId = if (viewType == 1 || viewType == 2) R.layout.item_category_tile else R.layout.item_category
            val v = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
            if (viewType == 1) {
                v.layoutParams.height = dpToPx(132)
            } else if (viewType == 2) {
                v.layoutParams.height = dpToPx(106)
            } else if (viewType == 3) {
                v.layoutParams.height = dpToPx(58)
            }
            val theme = themePresets.getOrElse(themePresetIndex) { themePresets[0] }
            v.background = buildBorderlessGlassTile(theme, radiusDp = 16f)
            v.elevation = 0f
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (name, list) = items[position]
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
        private val forceListStyle: Boolean = false,
        private val onClick: (Channel) -> Unit
    ) : RecyclerView.Adapter<ChannelAdapter.VH>() {

        inner class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvBadge: TextView = v.findViewById(R.id.tvChannelBadge)
            val ivLogo: ImageView = v.findViewById(R.id.ivChannelLogo)
            val tvName: TextView = v.findViewById(R.id.tvChannelName)
            val tvProg: TextView = v.findViewById(R.id.tvChannelCurrentProgram)
            val tvFav: TextView = v.findViewById(R.id.tvFavStar)
            val pbProgress: android.widget.ProgressBar = v.findViewById(R.id.pbChannelProgress)
        }

        override fun getItemViewType(position: Int): Int = if (forceListStyle) 3 else chanLayoutModeIndex

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val layoutId = if (viewType == 1 || viewType == 2) R.layout.item_channel_tile else R.layout.item_channel
            val v = LayoutInflater.from(parent.context).inflate(layoutId, parent, false)
            if (viewType == 1) {
                v.layoutParams.height = dpToPx(162)
            } else if (viewType == 2) {
                v.layoutParams.height = dpToPx(134)
            } else if (viewType == 3) {
                v.layoutParams.height = dpToPx(68)
            }
            val theme = themePresets.getOrElse(themePresetIndex) { themePresets[0] }
            v.background = buildBorderlessGlassTile(theme, radiusDp = 16f)
            v.elevation = 0f
            val vh = VH(v)
            vh.pbProgress.progressTintList = ColorStateList.valueOf(theme.accentColor)
            return vh
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val ch = channels[position]
            val now = System.currentTimeMillis()
            val live = liveLookup(ch)
            val logo = logoLookup(ch)

            holder.tvBadge.text = ch.name.take(3).uppercase(Locale.getDefault())
            holder.tvName.text = ch.name
            holder.tvProg.text = live?.title ?: "Прямой эфир"
            holder.tvFav.visibility = if (ch.isFavorite) View.VISIBLE else View.GONE
            holder.pbProgress.progress = live?.progressPercent(now) ?: 0

            if (logo.isNotEmpty()) {
                holder.ivLogo.visibility = View.VISIBLE
                holder.tvBadge.visibility = View.VISIBLE
                holder.ivLogo.load(logo) {
                    size(96, 96)
                    crossfade(false)
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
