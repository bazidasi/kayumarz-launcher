package com.kayumars.launcher.ui

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import com.kayumars.launcher.R
import com.kayumars.launcher.bridge.SearchResult

class ResultsAdapter(
    private val iconCache: IconCache,
    private val onAppSelected: (SearchResult) -> Unit
) : RecyclerView.Adapter<ResultsAdapter.ViewHolder>() {

    private var items: List<SearchResult> = emptyList()

    init {
        setHasStableIds(true)
    }

    fun submitList(newItems: List<SearchResult>) {
        val old = items
        val diffResult = DiffUtil.calculateDiff(object : DiffUtil.Callback() {
            override fun getOldListSize(): Int = old.size
            override fun getNewListSize(): Int = newItems.size

            override fun areItemsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                return old[oldItemPosition].pkg == newItems[newItemPosition].pkg
            }

            override fun areContentsTheSame(oldItemPosition: Int, newItemPosition: Int): Boolean {
                val o = old[oldItemPosition]
                val n = newItems[newItemPosition]
                return o.label == n.label && o.score == n.score
            }
        })

        items = newItems
        diffResult.dispatchUpdatesTo(this)
    }

    override fun getItemId(position: Int): Long {
        return items[position].pkg.hashCode().toLong()
    }

    override fun getItemCount(): Int = items.size

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_spotlight_row, parent, false)
        return ViewHolder(view)
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = items[position]
        holder.bind(item, iconCache, onAppSelected)
    }

    class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val iconView: ImageView = itemView.findViewById(R.id.spotlight_row_icon)
        private val labelView: TextView = itemView.findViewById(R.id.spotlight_row_title)
        private val scoreView: TextView = itemView.findViewById(R.id.spotlight_row_subtitle)

        fun bind(
            item: SearchResult,
            iconCache: IconCache,
            onSelect: (SearchResult) -> Unit
        ) {
            labelView.text = item.label
            scoreView.text = item.pkg

            // Load icon asynchronously without blocking scroll
            iconView.setImageDrawable(null)
            iconCache.loadIconAsync(item.pkg) { bitmap ->
                iconView.setImageBitmap(bitmap)
            }

            itemView.setOnClickListener {
                onSelect(item)
            }
        }
    }
}
