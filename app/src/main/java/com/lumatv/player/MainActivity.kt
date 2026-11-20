package com.lumatv.player

import android.app.AlertDialog
import android.app.PictureInPictureParams
import android.content.Context
import android.content.res.Configuration
import android.graphics.Color
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Rational
import android.view.KeyEvent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
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

    private lateinit var screenCategories: LinearLayout
    private lateinit var screenCategoryChannels: LinearLayout
    private lateinit var screenPlayer: FrameLayout
    private lateinit var screenSettings: LinearLayout

    // Экран 1: Категории
    private lateinit var btnPlaylistSelectorTop: Button
    private lateinit var btnSearchMain: Button
    private lateinit var btnRefreshMain: Button
    private lateinit var btnSettingsMain: Button
    private lateinit var rvCategories: RecyclerView

    // Экран 2: Каналы категории
    private lateinit var btnBackFromCategory: Button
    private lateinit var tvCategoryHeaderTitle: TextView
    private lateinit var btnSearchCategory: Button
    private lateinit var rvCategoryChannels: RecyclerView

    // Экран 3: Плеер (превью + полный экран + оверлеи)
    private lateinit var leftVideoAndControlsColumn: LinearLayout
    private lateinit var playerView: PlayerView
    private lateinit var tvClockOverlay: TextView
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
    private lateinit var osdProgressBar: ProgressBar

    private lateinit var btnOsdFullscreen: Button
    private lateinit var btnOsdPlayPause: Button
    private lateinit var btnOsdQuickChannels: Button
    private lateinit var btnOsdFavorite: Button
    private lateinit var btnOsdPip: Button
    private lateinit var btnOsdAspect: Button
    private lateinit var btnOsdQuality: Button
    private lateinit var btnOsdInfo: Button

    // Оверлей быстрого списка каналов (Фото 5)
    private lateinit var quickChannelOverlay: LinearLayout
    private lateinit var tvQuickCategoryTitle: TextView
    private lateinit var rvQuickChannels: RecyclerView

    // Оверлей информации о передаче (Фото 6)
    private lateinit var programInfoOverlay: LinearLayout
    private lateinit var tvInfoShowTitle: TextView
    private lateinit var tvInfoStreamSpecs: TextView
    private lateinit var tvInfoShowDescription: TextView

    // Экран 4: Настройки
    private lateinit var btnCloseSettings: Button
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
    private var isArchiveTab = false
    private var isFullscreenMode = false
    private var resizeModeIndex = 0
    private var bufferSeconds = 10

    private val timeFormat = SimpleDateFormat("HH:mm", Locale.getDefault())
    private val dateTimeFormat = SimpleDateFormat("dd MMM / HH:mm", Locale.getDefault())
    private val handler = Handler(Looper.getMainLooper())

    private val hideFullControlsRunnable = Runnable {
        if (isFullscreenMode && quickChannelOverlay.visibility != View.VISIBLE && programInfoOverlay.visibility != View.VISIBLE) {
            playerControlBar.visibility = View.GONE
            playerView.requestFocus()
        }
    }

    private val clockRunnable = object : Runnable {
        override fun run() {
            tvClockOverlay.text = timeFormat.format(Date())
            handler.postDelayed(this, 10000L)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        bindViews()
        loadSavedPlaylistsAndSettings()
        initPlayerWithBuffer(bufferSeconds)
        setupListeners()

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
        playerView.player = player
    }

    private fun bindViews() {
        screenCategories = findViewById(R.id.screenCategories)
        screenCategoryChannels = findViewById(R.id.screenCategoryChannels)
        screenPlayer = findViewById(R.id.screenPlayer)
        screenSettings = findViewById(R.id.screenSettings)

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

        leftVideoAndControlsColumn = findViewById(R.id.leftVideoAndControlsColumn)
        playerView = findViewById(R.id.playerView)
        tvClockOverlay = findViewById(R.id.tvClockOverlay)
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
        osdProgressBar = findViewById(R.id.osdProgressBar)

        btnOsdFullscreen = findViewById(R.id.btnOsdFullscreen)
        btnOsdPlayPause = findViewById(R.id.btnOsdPlayPause)
        btnOsdQuickChannels = findViewById(R.id.btnOsdQuickChannels)
        btnOsdFavorite = findViewById(R.id.btnOsdFavorite)
        btnOsdPip = findViewById(R.id.btnOsdPip)
        btnOsdAspect = findViewById(R.id.btnOsdAspect)
        btnOsdQuality = findViewById(R.id.btnOsdQuality)
        btnOsdInfo = findViewById(R.id.btnOsdInfo)

        quickChannelOverlay = findViewById(R.id.quickChannelOverlay)
        tvQuickCategoryTitle = findViewById(R.id.tvQuickCategoryTitle)
        rvQuickChannels = findViewById(R.id.rvQuickChannels)
        rvQuickChannels.layoutManager = LinearLayoutManager(this)

        programInfoOverlay = findViewById(R.id.programInfoOverlay)
        tvInfoShowTitle = findViewById(R.id.tvInfoShowTitle)
        tvInfoStreamSpecs = findViewById(R.id.tvInfoStreamSpecs)
        tvInfoShowDescription = findViewById(R.id.tvInfoShowDescription)

        btnCloseSettings = findViewById(R.id.btnCloseSettings)
        btnSetPlaylists = findViewById(R.id.btnSetPlaylists)
        btnSetEpg = findViewById(R.id.btnSetEpg)
        btnSetVideoAudio = findViewById(R.id.btnSetVideoAudio)
        btnSetBuffer = findViewById(R.id.btnSetBuffer)
        btnSetRefreshPlaylist = findViewById(R.id.btnSetRefreshPlaylist)
        btnSetRefreshEpg = findViewById(R.id.btnSetRefreshEpg)
    }

    private fun setupListeners() {
        btnPlaylistSelectorTop.setOnClickListener { showPlaylistManagerDialog() }
        btnSettingsMain.setOnClickListener { switchScreen(Screen.SETTINGS); btnSetPlaylists.requestFocus() }
        btnCloseSettings.setOnClickListener { showCategoriesScreen() }

        btnRefreshMain.setOnClickListener { loadActivePlaylistAndEpg() }
        btnSetRefreshPlaylist.setOnClickListener { loadActivePlaylistAndEpg(); showCategoriesScreen() }
        btnSetRefreshEpg.setOnClickListener { loadActivePlaylistAndEpg(); showCategoriesScreen() }

        btnSearchMain.setOnClickListener { showSearchDialog() }
        btnSearchCategory.setOnClickListener { showSearchDialog() }

        btnSetPlaylists.setOnClickListener { showPlaylistManagerDialog() }
        btnSetEpg.setOnClickListener { showEpgSelectorDialog() }
        btnSetVideoAudio.setOnClickListener { showTracksAndAspectDialog() }
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

        btnOsdPip.setOnClickListener {
            enterPictureInPicture()
        }

        btnOsdAspect.setOnClickListener { cycleAspectRatio() }
        btnOsdQuality.setOnClickListener { showTracksAndAspectDialog() }

        btnOsdInfo.setOnClickListener {
            if (!isFullscreenMode) enterFullscreenMode()
            openProgramInfoCard()
        }

        playerView.setOnClickListener {
            if (isFullscreenMode) {
                if (playerControlBar.visibility == View.VISIBLE) {
                    playerControlBar.visibility = View.GONE
                } else {
                    showFullscreenControlBar()
                }
            } else {
                enterFullscreenMode()
            }
        }
    }

    // РЕЖИМ ПРЕВЬЮ (ФОТО 3): ВИДЕО СЛЕВА + УПРАВЛЕНИЕ ПОД ВИДЕО + СПРАВА EPG/АРХИВ
    private fun enterPreviewMode() {
        isFullscreenMode = false
        handler.removeCallbacks(hideFullControlsRunnable)
        quickChannelOverlay.visibility = View.GONE
        programInfoOverlay.visibility = View.GONE

        playerRightPanel.visibility = View.VISIBLE
        playerControlBar.visibility = View.VISIBLE
        btnOsdFullscreen.text = "⛶"

        btnOsdFullscreen.post { btnOsdFullscreen.requestFocus() }
    }

    // ПОЛНОЭКРАННЫЙ РЕЖИМ (ФОТО 4)
    private fun enterFullscreenMode() {
        isFullscreenMode = true
        quickChannelOverlay.visibility = View.GONE
        programInfoOverlay.visibility = View.GONE

        playerRightPanel.visibility = View.GONE
        playerControlBar.visibility = View.GONE
        btnOsdFullscreen.text = "❐"
        playerView.requestFocus()
    }

    private fun showFullscreenControlBar() {
        playerControlBar.visibility = View.VISIBLE
        btnOsdPlayPause.requestFocus()
        handler.removeCallbacks(hideFullControlsRunnable)
        handler.postDelayed(hideFullControlsRunnable, 6000L)
    }

    // БЫСТРЫЙ СПИСОК КАНАЛОВ КАТЕГОРИИ ПО КНОПКЕ ВНИЗ В ПОЛНОМ ЭКРАНЕ (ФОТО 5)
    private fun openQuickChannelList() {
        playerControlBar.visibility = View.GONE
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
            logoLookup = { resolveChannelLogo(it) }
        ) { chosenChannel ->
            // Канал переключается ТОЛЬКО по нажатию ОК
            currentCategoryName = catTitle
            currentCategoryChannels = list
            activeChannel = chosenChannel
            playStream(chosenChannel.url)
            updateOsdInfo(chosenChannel, null)
            quickChannelOverlay.visibility = View.GONE
            playerView.requestFocus()
        }
        rvQuickChannels.post { rvQuickChannels.requestFocus() }
    }

    // КАРТОЧКА ИНФОРМАЦИИ О ПЕРЕДАЧЕ ПО КНОПКЕ ВВЕРХ В ПОЛНОМ ЭКРАНЕ (ФОТО 6)
    private fun openProgramInfoCard() {
        playerControlBar.visibility = View.GONE
        quickChannelOverlay.visibility = View.GONE
        programInfoOverlay.visibility = View.VISIBLE

        val ch = activeChannel
        val live = ch?.let { getLiveProgram(it) }

        tvInfoShowTitle.text = live?.title ?: (ch?.name ?: "Прямой эфир")

        val vFmt = player.videoFormat
        val aFmt = player.audioFormat
        val vRes = if (vFmt != null && vFmt.width > 0) "${vFmt.width} × ${vFmt.height}" else "Авто"
        val aLang = aFmt?.language ?: "Русский"
        val aCh = if (aFmt != null && aFmt.channelCount > 1) "Стерео" else "Моно"
        tvInfoStreamSpecs.text = "Видео: $vRes, аудио: $aLang ($aCh)"

        val desc = live?.description?.takeIf { it.isNotBlank() }
            ?: "Описание передачи отсутствует в телепрограмме EPG для данного канала."
        tvInfoShowDescription.text = desc
    }

    // КАРТИНКА В КАРТИНКЕ (PiP)
    private fun enterPictureInPicture() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            try {
                quickChannelOverlay.visibility = View.GONE
                programInfoOverlay.visibility = View.GONE
                playerControlBar.visibility = View.GONE
                playerRightPanel.visibility = View.GONE

                val params = PictureInPictureParams.Builder()
                    .setAspectRatio(Rational(16, 9))
                    .build()
                enterPictureInPictureMode(params)
            } catch (e: Exception) {
                Toast.makeText(this, "Режим PiP не поддерживается прошивкой ТВ", Toast.LENGTH_SHORT).show()
            }
        } else {
            Toast.makeText(this, "PiP доступен начиная с Android 8.0", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onPictureInPictureModeChanged(isInPictureInPictureMode: Boolean, newConfig: Configuration) {
        super.onPictureInPictureModeChanged(isInPictureInPictureMode, newConfig)
        if (!isInPictureInPictureMode && currentScreen == Screen.PLAYER) {
            if (isFullscreenMode) {
                enterFullscreenMode()
            } else {
                enterPreviewMode()
            }
        }
    }

    private fun switchScreen(s: Screen) {
        currentScreen = s
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

        rvCategoryChannels.adapter = ChannelAdapter(
            channels = list,
            liveLookup = { getLiveProgram(it) },
            logoLookup = { resolveChannelLogo(it) }
        ) { selectedChannel ->
            openPlayerPreview(selectedChannel)
        }
        rvCategoryChannels.post { rvCategoryChannels.requestFocus() }
    }

    private fun openPlayerPreview(channel: Channel) {
        activeChannel = channel
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
                playStream(channel.url)
                updateOsdInfo(channel, null)
            }
        } else {
            val maxAgeMs = (channel.catchupDays.takeIf { it > 0 } ?: 5) * 24L * 3600L * 1000L
            val past = allProgs.filter { it.isPast(now) && it.startMs >= now - maxAgeMs }
                .sortedByDescending { it.startMs }
                .take(70)
            rvPlayerEpg.adapter = EpgAdapter(past, isArchive = true) { archiveProg ->
                val url = Parsers.buildArchiveUrl(channel, archiveProg)
                playStream(url)
                updateOsdInfo(channel, archiveProg)
            }
        }
    }

    private fun updateOsdInfo(channel: Channel, archiveProg: EpgProgram?) {
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
            tvOsdNextShow.text = "АРХИВ • ${dateTimeFormat.format(Date(archiveProg.startMs))}"
            tvOsdTimeRight.text = "${timeFormat.format(Date(archiveProg.startMs))}\n${archiveProg.durationMinutes()} мин."
            osdProgressBar.progress = 50
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
            osdProgressBar.progress = cur.progressPercent(now)
        } else {
            tvOsdCurrentShow.text = channel.name
            tvOsdNextShow.text = "Прямой эфир"
            tvOsdTimeRight.text = timeFormat.format(Date())
            osdProgressBar.progress = 100
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
                    } else if (currentScreen == Screen.PLAYER) {
                        selectPlayerTab(isArchiveTab)
                        activeChannel?.let { updateOsdInfo(it, null) }
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
        if (event.action == KeyEvent.ACTION_DOWN && currentScreen == Screen.PLAYER) {
            // 1. Если открыт оверлей информации о передаче (Фото 6)
            if (programInfoOverlay.visibility == View.VISIBLE) {
                if (event.keyCode == KeyEvent.KEYCODE_BACK || event.keyCode == KeyEvent.KEYCODE_DPAD_CENTER || event.keyCode == KeyEvent.KEYCODE_ENTER) {
                    programInfoOverlay.visibility = View.GONE
                    playerView.requestFocus()
                    return true
                }
            }

            // 2. Если открыт быстрый список каналов по кнопке Вниз (Фото 5)
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

            // 3. Полноэкранный режим без открытых оверлеев
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
                        switchChannelInCategory(-1)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_RIGHT -> {
                        switchChannelInCategory(1)
                        return true
                    }
                    KeyEvent.KEYCODE_DPAD_CENTER, KeyEvent.KEYCODE_ENTER, KeyEvent.KEYCODE_MENU -> {
                        showFullscreenControlBar()
                        return true
                    }
                }
            }

            // 4. Перезапуск таймера автоскрытия нижней панели в полном экране
            if (isFullscreenMode && playerControlBar.visibility == View.VISIBLE) {
                handler.removeCallbacks(hideFullControlsRunnable)
                handler.postDelayed(hideFullControlsRunnable, 6000L)
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
                        // В полноэкранном режиме кнопка Назад возвращает на превью канала с телепрограммой
                        enterPreviewMode()
                        return true
                    }
                    // Из превью кнопка Назад возвращает в выбор каналов категории
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
                Screen.CATEGORIES -> { }
            }
        }
        return super.onKeyDown(keyCode, event)
    }

    private fun switchChannelInCategory(offset: Int) {
        val cur = activeChannel ?: return
        if (currentCategoryChannels.isEmpty()) return
        val idx = currentCategoryChannels.indexOfFirst { it.url == cur.url }
        if (idx >= 0) {
            val nextIdx = (idx + offset + currentCategoryChannels.size) % currentCategoryChannels.size
            val nextCh = currentCategoryChannels[nextIdx]
            activeChannel = nextCh
            playStream(nextCh.url)
            updateOsdInfo(nextCh, null)
            showFullscreenControlBar()
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

    private fun showTracksAndAspectDialog() {
        val options = mutableListOf(
            "Масштаб экрана: По размеру / Растянуть / Зум",
            "Качество видео: Авто (максимальное)",
            "Качество видео: Экономия трафика (до 720p)"
        )

        val audioLabels = mutableListOf<String>()
        val audioGroups = player.currentTracks.groups.filter { it.type == C.TRACK_TYPE_AUDIO }
        for (gIdx in audioGroups.indices) {
            val group = audioGroups[gIdx]
            for (tIdx in 0 until group.length) {
                val fmt = group.getTrackFormat(tIdx)
                val lang = fmt.language ?: "Дорожка ${audioLabels.size + 1}"
                audioLabels.add("Аудиодорожка: $lang (${fmt.label ?: "Stereo"})")
            }
        }
        options.addAll(audioLabels)

        AlertDialog.Builder(this)
            .setTitle("Настройки видео и аудио")
            .setItems(options.toTypedArray()) { _, which ->
                when (which) {
                    0 -> cycleAspectRatio()
                    1 -> {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .clearVideoSizeConstraints()
                            .build()
                        Toast.makeText(this, "Качество видео: Авто", Toast.LENGTH_SHORT).show()
                    }
                    2 -> {
                        player.trackSelectionParameters = player.trackSelectionParameters
                            .buildUpon()
                            .setMaxVideoSize(1280, 720)
                            .build()
                        Toast.makeText(this, "Ограничение качества: до 720p", Toast.LENGTH_SHORT).show()
                    }
                    else -> {
                        val audioChoice = which - 3
                        var counter = 0
                        for (group in audioGroups) {
                            for (tIdx in 0 until group.length) {
                                if (counter == audioChoice) {
                                    player.trackSelectionParameters = player.trackSelectionParameters
                                        .buildUpon()
                                        .setOverrideForType(TrackSelectionOverride(group.mediaTrackGroup, tIdx))
                                        .build()
                                    Toast.makeText(this, "Звуковая дорожка переключена", Toast.LENGTH_SHORT).show()
                                }
                                counter++
                            }
                        }
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

    private fun cycleAspectRatio() {
        val modes = listOf(
            AspectRatioFrameLayout.RESIZE_MODE_FIT,
            AspectRatioFrameLayout.RESIZE_MODE_FILL,
            AspectRatioFrameLayout.RESIZE_MODE_ZOOM
        )
        val names = listOf("По размеру", "На весь экран", "Увеличение (Zoom)")
        resizeModeIndex = (resizeModeIndex + 1) % modes.size
        playerView.resizeMode = modes[resizeModeIndex]
        Toast.makeText(this, "Масштаб: ${names[resizeModeIndex]}", Toast.LENGTH_SHORT).show()
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
        btnSetBuffer.text = "Буферизация видео: $bufferSeconds сек"
        activePlaylistIndex = prefs.getInt("active_playlist_idx", 0)

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
        player.release()
    }

    class CategoryAdapter(
        private val items: List<Pair<String, List<Channel>>>,
        private val onClick: (String, List<Channel>) -> Unit
    ) : RecyclerView.Adapter<CategoryAdapter.VH>() {

        class VH(v: View) : RecyclerView.ViewHolder(v) {
            val tvName: TextView = v.findViewById(R.id.tvCategoryName)
            val tvCount: TextView = v.findViewById(R.id.tvCategoryCount)
        }

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
            val v = LayoutInflater.from(parent.context).inflate(R.layout.item_category, parent, false)
            return VH(v)
        }

        override fun onBindViewHolder(holder: VH, position: Int) {
            val (name, list) = items[position]
            holder.tvName.text = name
            holder.tvCount.text = list.size.toString()

            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                val c = if (hasFocus) Color.parseColor("#14234D") else Color.WHITE
                val sub = if (hasFocus) Color.parseColor("#14234D") else Color.parseColor("#D6E2FF")
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
                val titleC = if (hasFocus) Color.parseColor("#14234D") else Color.WHITE
                val subC = if (hasFocus) Color.parseColor("#253E80") else Color.parseColor("#CBD8F8")
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

            holder.tvTitle.text = p.title
            holder.tvTime.text = if (!isArchive && p.isLive(now)) {
                "Сейчас / $startStr — $endStr"
            } else {
                "$startStr — $endStr"
            }
            holder.tvDur.text = "${p.durationMinutes()} минут"

            holder.itemView.setOnFocusChangeListener { _, hasFocus ->
                val titleC = if (hasFocus) Color.parseColor("#14234D") else Color.WHITE
                val subC = if (hasFocus) Color.parseColor("#253E80") else Color.parseColor("#CBD8F8")
                holder.tvTitle.setTextColor(titleC)
                holder.tvTime.setTextColor(subC)
                holder.tvDur.setTextColor(subC)
            }
            holder.itemView.setOnClickListener { onClick(p) }
        }

        override fun getItemCount(): Int = programs.size
    }
}
