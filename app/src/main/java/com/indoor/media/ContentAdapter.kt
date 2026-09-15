package com.indoor.media

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ContentAdapter(
    private val onDelete: (String) -> Unit
) : RecyclerView.Adapter<ContentAdapter.ViewHolder>() {

    private val items = mutableListOf<MediaItem>()

    fun submitList(newItems: List<MediaItem>) {
        items.clear()
        items.addAll(newItems)
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = TextView(parent.context).apply {
            textSize = 18f
            setPadding(32, 24, 32, 24)
        }
        return ViewHolder(view, onDelete)
    }

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        holder.bind(items[position])
    }

    class ViewHolder(
        private val view: View,
        private val onDelete: (String) -> Unit
    ) : RecyclerView.ViewHolder(view) {

        fun bind(item: MediaItem) {
            val text = view as TextView
            val typeLabel = when (item.type) {
                MediaItem.MediaType.VIDEO -> "VIDEO"
                MediaItem.MediaType.IMAGE -> "IMAGEM"
                MediaItem.MediaType.AUDIO -> "AUDIO"
            }
            text.text = "${item.fileName}  [$typeLabel]  ${item.durationSeconds}s"
            view.setOnClickListener {
                onDelete(item.fileName)
            }
        }
    }
}