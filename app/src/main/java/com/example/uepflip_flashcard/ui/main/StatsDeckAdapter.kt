package com.example.uepflip_flashcard.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.uepflip_flashcard.R
import com.example.uepflip_flashcard.data.model.Deck
import com.google.android.material.progressindicator.LinearProgressIndicator

/** "Progress by deck" rows on the Statistics screen. */
class StatsDeckAdapter(
    private val onOpen: (Deck) -> Unit
) : ListAdapter<Deck, StatsDeckAdapter.VH>(DiffCallback()) {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val mono: TextView = v.findViewById(R.id.monogram)
        val name: TextView = v.findViewById(R.id.deck_name)
        val count: TextView = v.findViewById(R.id.deck_count)
        val review: TextView = v.findViewById(R.id.deck_review)
        val progress: LinearProgressIndicator = v.findViewById(R.id.deck_progress)
        val pct: TextView = v.findViewById(R.id.deck_pct)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_stats_deck, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val d = getItem(position)
        val ctx = h.itemView.context
        h.mono.text = d.mono
        h.name.text = d.name
        h.count.text = ctx.resources.getQuantityString(R.plurals.deck_cards_count, d.count, d.count)
        if (d.review != null) {
            h.review.visibility = View.VISIBLE
            h.review.text = ctx.getString(R.string.next_review_label, d.review)
        } else {
            h.review.visibility = View.GONE
        }
        h.progress.setProgressCompat(d.mastery, true)
        h.pct.text = ctx.getString(R.string.stat_percent, d.mastery)
        h.itemView.setOnClickListener { onOpen(d) }
    }

    class DiffCallback : DiffUtil.ItemCallback<Deck>() {
        override fun areItemsTheSame(oldItem: Deck, newItem: Deck) = oldItem.id == newItem.id
        override fun areContentsTheSame(oldItem: Deck, newItem: Deck) = oldItem == newItem
    }
}
