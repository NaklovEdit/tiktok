package com.volkan.videofeed

import android.graphics.Color
import android.net.Uri
import android.view.View
import android.view.ViewGroup
import android.widget.FrameLayout
import android.widget.ImageView
import androidx.media3.ui.PlayerView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide

data class Video(val id: Long, val uri: Uri)

class VideoAdapter(private val onTap: () -> Unit) : RecyclerView.Adapter<VideoAdapter.VH>() {

    var items: List<Video> = emptyList()

    class VH(
        val root: FrameLayout,
        val playerView: PlayerView,
        val thumb: ImageView
    ) : RecyclerView.ViewHolder(root)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val ctx = parent.context
        val match = ViewGroup.LayoutParams.MATCH_PARENT

        val root = FrameLayout(ctx).apply {
            layoutParams = RecyclerView.LayoutParams(match, match)
            setBackgroundColor(Color.BLACK)
            setOnClickListener { onTap() }
        }
        val playerView = PlayerView(ctx).apply { useController = false }
        val thumb = ImageView(ctx).apply { scaleType = ImageView.ScaleType.FIT_CENTER }

        root.addView(playerView, FrameLayout.LayoutParams(match, match))
        root.addView(thumb, FrameLayout.LayoutParams(match, match))
        return VH(root, playerView, thumb)
    }

    override fun onBindViewHolder(holder: VH, position: Int) {
        holder.playerView.player = null
        holder.thumb.visibility = View.VISIBLE
        Glide.with(holder.thumb)
            .load(items[position].uri)
            .override(360, 640)
            .into(holder.thumb)
    }

    override fun onViewRecycled(holder: VH) {
        holder.playerView.player = null
        Glide.with(holder.thumb).clear(holder.thumb)
    }

    override fun getItemCount() = items.size
}
