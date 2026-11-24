package com.simpletv.app.ui

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import coil.dispose
import coil.load
import com.simpletv.app.R

/** A text row: categories, channels, episodes, settings, search results. */
class RowAdapter(
    private val onClick: (Int) -> Unit,
    private val onLongClick: ((Int) -> Unit)? = null,
    private val onFocus: ((Int) -> Unit)? = null,
    private val showIcons: Boolean = false,
) : RecyclerView.Adapter<RowAdapter.VH>() {

    data class Row(
        val title: String,
        val left: String? = null,
        val right: String? = null,
        val icon: String? = null,
        val star: Boolean = false,
        val sub: String? = null,
    )

    var items: List<Row> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    /** The row drawn as "selected" (the open category) while focus is elsewhere. */
    var activated: Int = -1
        set(value) {
            val old = field
            field = value
            if (old in items.indices) notifyItemChanged(old)
            if (value in items.indices) notifyItemChanged(value)
        }

    fun update(position: Int, row: Row) {
        if (position !in items.indices) return
        items = items.toMutableList().also { it[position] = row }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val left: TextView = v.findViewById(R.id.left)
        val icon: ImageView = v.findViewById(R.id.icon)
        val title: TextView = v.findViewById(R.id.title)
        val sub: TextView = v.findViewById(R.id.sub)
        val star: TextView = v.findViewById(R.id.star)
        val right: TextView = v.findViewById(R.id.right)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_row, parent, false)
        val h = VH(v)
        v.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
        val long = onLongClick
        if (long != null) {
            v.setOnLongClickListener {
                val p = h.bindingAdapterPosition
                if (p != RecyclerView.NO_POSITION) long(p)
                true
            }
        }
        v.setOnFocusChangeListener { _, hasFocus ->
            h.title.isSelected = hasFocus // scrolls long names
            val p = h.bindingAdapterPosition
            if (hasFocus && p != RecyclerView.NO_POSITION) onFocus?.invoke(p)
        }
        return h
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val r = items[position]
        h.title.text = r.title
        h.left.text = r.left.orEmpty()
        h.left.visibility = if (r.left == null) View.GONE else View.VISIBLE
        h.sub.text = r.sub.orEmpty()
        h.sub.visibility = if (r.sub.isNullOrEmpty()) View.GONE else View.VISIBLE
        h.right.text = r.right.orEmpty()
        h.star.visibility = if (r.star) View.VISIBLE else View.GONE
        if (showIcons) {
            h.icon.visibility = View.VISIBLE
            if (r.icon.isNullOrBlank()) {
                h.icon.dispose()
                h.icon.setImageDrawable(null)
            } else {
                h.icon.load(r.icon) { crossfade(false) }
            }
        } else {
            h.icon.visibility = View.GONE
        }
        h.itemView.isActivated = position == activated
    }

    override fun getItemCount() = items.size
}

/** A poster in the film and series grids. */
class PosterAdapter(
    private val onClick: (Int) -> Unit,
    private val onLongClick: (Int) -> Unit,
) : RecyclerView.Adapter<PosterAdapter.VH>() {

    data class Poster(val title: String, val image: String?, val star: Boolean, val badge: String?)

    var items: List<Poster> = emptyList()
        @SuppressLint("NotifyDataSetChanged")
        set(value) {
            field = value
            notifyDataSetChanged()
        }

    fun update(position: Int, p: Poster) {
        if (position !in items.indices) return
        items = items.toMutableList().also { it[position] = p }
    }

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val poster: ImageView = v.findViewById(R.id.poster)
        val placeholder: TextView = v.findViewById(R.id.placeholder)
        val star: TextView = v.findViewById(R.id.star)
        val badge: TextView = v.findViewById(R.id.badge)
        val title: TextView = v.findViewById(R.id.title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): VH {
        val v = LayoutInflater.from(parent.context).inflate(R.layout.item_poster, parent, false)
        val h = VH(v)
        v.setOnClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onClick(p)
        }
        v.setOnLongClickListener {
            val p = h.bindingAdapterPosition
            if (p != RecyclerView.NO_POSITION) onLongClick(p)
            true
        }
        return h
    }

    override fun onBindViewHolder(h: VH, position: Int) {
        val p = items[position]
        h.title.text = p.title
        h.placeholder.text = p.title
        h.placeholder.visibility = View.VISIBLE
        if (p.image.isNullOrBlank()) {
            h.poster.dispose()
            h.poster.setImageDrawable(null)
        } else {
            h.poster.load(p.image) {
                crossfade(false)
                listener(onSuccess = { _, _ -> h.placeholder.visibility = View.GONE })
            }
        }
        h.star.visibility = if (p.star) View.VISIBLE else View.GONE
        h.badge.text = p.badge.orEmpty()
        h.badge.visibility = if (p.badge.isNullOrEmpty()) View.GONE else View.VISIBLE
    }

    override fun getItemCount() = items.size
}
