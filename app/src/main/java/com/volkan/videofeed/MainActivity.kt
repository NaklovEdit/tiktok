package com.volkan.videofeed

import android.Manifest
import android.app.Activity
import android.app.AlertDialog
import android.content.ContentUris
import android.content.pm.PackageManager
import android.graphics.Color
import android.graphics.drawable.GradientDrawable
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.EditText
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.TextView
import android.widget.Toast
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.media3.common.AudioAttributes
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.recyclerview.widget.RecyclerView
import androidx.viewpager2.widget.ViewPager2

class MainActivity : Activity() {

    private lateinit var store: Store
    private lateinit var player: ExoPlayer
    private lateinit var pager: ViewPager2
    private lateinit var videoAdapter: VideoAdapter
    private lateinit var heartBtn: TextView
    private lateinit var filterBtn: TextView
    private lateinit var emptyText: TextView

    private var allVideos: List<Video> = emptyList()
    private var loaded = false
    private var mode = "ALL" // "ALL", "FAV" ou "CAT:<nom>"
    private var attached: VideoAdapter.VH? = null
    private var playingId = -1L

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).let {
            it.hide(WindowInsetsCompat.Type.systemBars())
            it.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }

        store = Store(this)

        player = ExoPlayer.Builder(this).build().apply {
            repeatMode = Player.REPEAT_MODE_ONE // boucle comme TikTok
            setAudioAttributes(AudioAttributes.DEFAULT, true)
            setHandleAudioBecomingNoisy(true)
            addListener(object : Player.Listener {
                override fun onRenderedFirstFrame() {
                    attached?.thumb?.visibility = View.GONE
                }

                override fun onPlayerError(error: PlaybackException) {
                    // Vidéo illisible : on passe à la suivante
                    val next = pager.currentItem + 1
                    if (next < videoAdapter.itemCount) pager.setCurrentItem(next, true)
                }
            })
        }

        buildUi()

        if (checkSelfPermission(perm()) == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            requestPermissions(arrayOf(perm()), 1)
        }
    }

    // ---------- UI ----------

    private fun dp(v: Int) = (v * resources.displayMetrics.density).toInt()

    private fun overlay(text: String, size: Float) = TextView(this).apply {
        this.text = text
        textSize = size
        setTextColor(Color.WHITE)
        gravity = Gravity.CENTER
        setShadowLayer(8f, 0f, 0f, Color.BLACK)
        setPadding(dp(14), dp(12), dp(14), dp(12))
    }

    private fun buildUi() {
        val match = ViewGroup.LayoutParams.MATCH_PARENT
        val wrap = ViewGroup.LayoutParams.WRAP_CONTENT
        val root = FrameLayout(this).apply { setBackgroundColor(Color.BLACK) }

        videoAdapter = VideoAdapter { player.playWhenReady = !player.playWhenReady }

        pager = ViewPager2(this).apply {
            orientation = ViewPager2.ORIENTATION_VERTICAL
            adapter = videoAdapter
            offscreenPageLimit = 1
            (getChildAt(0) as RecyclerView).overScrollMode = View.OVER_SCROLL_NEVER
            registerOnPageChangeCallback(object : ViewPager2.OnPageChangeCallback() {
                override fun onPageSelected(position: Int) {
                    player.pause()
                    refreshButtons(position)
                    playAt(position)
                }
            })
        }
        root.addView(pager, FrameLayout.LayoutParams(match, match))

        emptyText = overlay("Aucune vidéo", 18f).apply {
            setTextColor(Color.LTGRAY)
            visibility = View.GONE
        }
        root.addView(emptyText, FrameLayout.LayoutParams(wrap, wrap, Gravity.CENTER))

        filterBtn = overlay("📂 Toutes", 16f).apply {
            background = GradientDrawable().apply {
                setColor(0x66000000)
                cornerRadius = dp(20).toFloat()
            }
            setPadding(dp(16), dp(8), dp(16), dp(8))
            setOnClickListener { showFilterDialog() }
        }
        root.addView(
            filterBtn,
            FrameLayout.LayoutParams(wrap, wrap, Gravity.TOP or Gravity.START).apply {
                topMargin = dp(36); leftMargin = dp(12)
            }
        )

        heartBtn = overlay("🤍", 34f).apply {
            setOnClickListener {
                val v = current() ?: return@setOnClickListener
                store.toggleFavorite(v.id)
                refreshButtons(pager.currentItem)
            }
        }
        val folderBtn = overlay("📁", 30f).apply { setOnClickListener { showCategoryDialog() } }
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            addView(heartBtn)
            addView(folderBtn)
        }
        root.addView(
            column,
            FrameLayout.LayoutParams(wrap, wrap, Gravity.END or Gravity.BOTTOM).apply {
                rightMargin = dp(8); bottomMargin = dp(120)
            }
        )

        setContentView(root)
    }

    private fun current(): Video? = videoAdapter.items.getOrNull(pager.currentItem)

    private fun refreshButtons(position: Int) {
        val v = videoAdapter.items.getOrNull(position)
        heartBtn.text = if (v != null && store.isFavorite(v.id)) "❤️" else "🤍"
    }

    // ---------- Lecture ----------

    private fun playAt(position: Int, tries: Int = 0) {
        val items = videoAdapter.items
        if (position !in items.indices) return

        val rv = pager.getChildAt(0) as RecyclerView
        val holder = rv.findViewHolderForAdapterPosition(position) as? VideoAdapter.VH
        if (holder == null) {
            if (tries < 20) {
                pager.postDelayed({
                    if (pager.currentItem == position) playAt(position, tries + 1)
                }, 30)
            }
            return
        }

        val video = items[position]
        if (attached === holder && playingId == video.id) return

        attached?.let {
            it.playerView.player = null
            it.thumb.visibility = View.VISIBLE
        }
        holder.thumb.visibility = View.VISIBLE
        holder.playerView.player = player
        player.setMediaItem(MediaItem.fromUri(video.uri))
        player.prepare()
        player.playWhenReady = true
        attached = holder
        playingId = video.id
    }

    private fun detachPlayer() {
        attached?.let {
            it.playerView.player = null
            it.thumb.visibility = View.VISIBLE
        }
        attached = null
        playingId = -1L
        player.stop()
        player.clearMediaItems()
    }

    // ---------- Données ----------

    private fun perm() =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_VIDEO
        else Manifest.permission.READ_EXTERNAL_STORAGE

    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) {
            loadVideos()
        } else {
            emptyText.text = "Autorise l'accès aux vidéos\ndans les paramètres de l'appli"
            emptyText.visibility = View.VISIBLE
        }
    }

    private fun queryVideos(): List<Video> {
        val list = ArrayList<Video>()
        val base = MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        contentResolver.query(
            base,
            arrayOf(MediaStore.Video.Media._ID),
            null, null,
            "${MediaStore.Video.Media.DATE_ADDED} DESC"
        )?.use { c ->
            val idCol = c.getColumnIndexOrThrow(MediaStore.Video.Media._ID)
            while (c.moveToNext()) {
                val id = c.getLong(idCol)
                list.add(Video(id, ContentUris.withAppendedId(base, id)))
            }
        }
        return list
    }

    private fun loadVideos() {
        Thread {
            val list = runCatching { queryVideos() }.getOrDefault(emptyList())
            runOnUiThread {
                if (isDestroyed || isFinishing) return@runOnUiThread
                val changed = list.map { it.id } != allVideos.map { it.id }
                allVideos = list
                if (changed || !loaded) {
                    loaded = true
                    applyFilter()
                }
            }
        }.start()
    }

    private fun filtered(): List<Video> = when {
        mode == "FAV" -> allVideos.filter { store.isFavorite(it.id) }
        mode.startsWith("CAT:") -> {
            val ids = store.idsOf(mode.removePrefix("CAT:"))
            allVideos.filter { ids.contains(it.id) }
        }
        else -> allVideos
    }

    private fun label() = when {
        mode == "ALL" -> "Toutes"
        mode == "FAV" -> "Favoris"
        else -> mode.removePrefix("CAT:")
    }

    private fun applyFilter() {
        detachPlayer()
        val list = filtered()
        videoAdapter.items = list
        videoAdapter.notifyDataSetChanged()
        pager.setCurrentItem(0, false)
        emptyText.text = "Aucune vidéo"
        emptyText.visibility = if (list.isEmpty()) View.VISIBLE else View.GONE
        filterBtn.text = "📂 " + label()
        refreshButtons(0)
        if (list.isNotEmpty()) pager.post { playAt(0) }
    }

    private fun setFilter(m: String) {
        mode = m
        applyFilter()
    }

    // ---------- Dialogues ----------

    private fun toast(msg: String) = Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()

    private fun promptNewCategory(onDone: (String) -> Unit) {
        val input = EditText(this).apply {
            hint = "Nom de la catégorie"
            setSingleLine()
        }
        AlertDialog.Builder(this)
            .setTitle("Nouvelle catégorie")
            .setView(input)
            .setPositiveButton("Créer") { _, _ ->
                val name = input.text.toString().trim()
                if (name.isNotEmpty()) {
                    store.addCategory(name)
                    onDone(name)
                }
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showFilterDialog() {
        val cats = store.categoryNames()
        val items = mutableListOf("Toutes les vidéos", "❤️ Favoris")
        items.addAll(cats.map { "📁 $it" })
        items.add("＋ Nouvelle catégorie…")

        val dlg = AlertDialog.Builder(this)
            .setTitle("Afficher  (appui long = supprimer)")
            .setItems(items.toTypedArray()) { _, which ->
                when {
                    which == 0 -> setFilter("ALL")
                    which == 1 -> setFilter("FAV")
                    which == items.size - 1 -> promptNewCategory { }
                    else -> setFilter("CAT:" + cats[which - 2])
                }
            }
            .create()
        dlg.setOnShowListener {
            dlg.listView.setOnItemLongClickListener { _, _, pos, _ ->
                if (pos >= 2 && pos < items.size - 1) {
                    dlg.dismiss()
                    confirmDelete(cats[pos - 2])
                    true
                } else false
            }
        }
        dlg.show()
    }

    private fun confirmDelete(name: String) {
        AlertDialog.Builder(this)
            .setTitle("Supprimer « $name » ?")
            .setMessage("Tes vidéos ne sont pas supprimées, seulement la catégorie.")
            .setPositiveButton("Supprimer") { _, _ ->
                store.deleteCategory(name)
                if (mode == "CAT:$name") setFilter("ALL")
            }
            .setNegativeButton("Annuler", null)
            .show()
    }

    private fun showCategoryDialog() {
        val v = current() ?: return
        val cats = store.categoryNames()
        if (cats.isEmpty()) {
            promptNewCategory { name ->
                store.setInCategory(name, v.id, true)
                toast("Ajoutée à $name")
            }
            return
        }
        val checked = BooleanArray(cats.size) { store.inCategory(cats[it], v.id) }
        AlertDialog.Builder(this)
            .setTitle("Catégories de cette vidéo")
            .setMultiChoiceItems(cats.toTypedArray(), checked) { _, i, isChecked ->
                store.setInCategory(cats[i], v.id, isChecked)
            }
            .setPositiveButton("OK", null)
            .setNeutralButton("＋ Nouvelle") { _, _ ->
                promptNewCategory { name -> store.setInCategory(name, v.id, true) }
            }
            .show()
    }

    // ---------- Cycle de vie (économie de batterie) ----------

    override fun onResume() {
        super.onResume()
        if (attached != null) player.playWhenReady = true
        if (checkSelfPermission(perm()) == PackageManager.PERMISSION_GRANTED && loaded) loadVideos()
    }

    override fun onPause() {
        player.pause()
        super.onPause()
    }

    override fun onDestroy() {
        player.release()
        super.onDestroy()
    }
}
