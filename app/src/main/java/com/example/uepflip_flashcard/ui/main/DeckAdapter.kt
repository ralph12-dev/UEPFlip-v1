package com.example.uepflip_flashcard.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.uepflip_flashcard.R
import com.example.uepflip_flashcard.data.model.Deck
import com.google.android.material.progressindicator.LinearProgressIndicator

class DeckAdapter(
    private val showStudy: Boolean,
    private val onStudy: (Deck) -> Unit,
    private val onLongClick: ((Deck) -> Unit)? = null
) : ListAdapter<Deck, DeckAdapter.VH>(DiffCallback()) {

    class VH(v: View) : RecyclerView.ViewHolder(v) {
        val mono: TextView = v.findViewById(R.id.monogram)
        val name: TextView = v.findViewById(R.id.deck_name)
        val count: TextView = v.findViewById(R.id.deck_count)
        val review: TextView = v.findViewById(R.id.deck_review)
        val progress: LinearProgressIndicator = v.findViewById(R.id.deck_progress)
        val pct: TextView = v.findViewById(R.id.deck_pct)
        val chevron: ImageView = v.findViewById(R.id.chevron)
        val study: Button = v.findViewById(R.id.btn_study)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int) =
        VH(LayoutInflater.from(parent.context).inflate(R.layout.item_deck, parent, false))

    override fun onBindViewHolder(h: VH, position: Int) {
        val d = getItem(position)
        h.mono.text = d.mono
        h.name.text = d.name
        h.count.text = h.itemView.context.resources.getQuantityString(
            R.plurals.deck_cards_count,
            d.count,
            d.count
        )
        if (d.review != null) {
            h.review.visibility = View.VISIBLE
            h.review.text = h.itemView.context.getString(R.string.next_review_label, d.review)
        } else {
            h.review.visibility = View.GONE
        }
        h.progress.progress = d.mastery
        h.pct.text = h.itemView.context.getString(R.string.stat_percent, d.mastery)

        h.study.visibility = if (showStudy) View.VISIBLE else View.GONE
        h.study.setOnClickListener { onStudy(d) }
        h.itemView.setOnClickListener { onStudy(d) }
        h.itemView.setOnLongClickListener {
            onLongClick?.let { handler ->
                handler(d)
                true
            } ?: false
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<Deck>() {
        override fun areItemsTheSame(oldItem: Deck, newItem: Deck) = oldItem.id == newItem.id

        override fun areContentsTheSame(oldItem: Deck, newItem: Deck) = oldItem == newItem
    }
}
