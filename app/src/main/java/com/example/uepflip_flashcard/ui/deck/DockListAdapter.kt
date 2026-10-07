package com.example.uepflip_flashcard.ui.deck

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.uepflip_flashcard.R
import com.example.uepflip_flashcard.data.local.entities.DeckEntity
import com.google.android.material.chip.Chip

class DockListAdapter(
    private val onDeckClick: (DeckEntity) -> Unit
) : ListAdapter<DeckEntity, DockListAdapter.DeckViewHolder>(DiffCallback()) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeckViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_deck, parent, false)
        return DeckViewHolder(view)
    }

    override fun onBindViewHolder(holder: DeckViewHolder, position: Int) {
        val deck = getItem(position)
        holder.bind(deck, onDeckClick)
    }

    class DeckViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        private val tvTitle: TextView = view.findViewById(R.id.tvDeckTitle)
        private val tvDesc: TextView = view.findViewById(R.id.tvDeckDescription)
        private val chipCount: Chip = view.findViewById(R.id.cardCountBadge)

        fun bind(deck: DeckEntity, onDeckClick: (DeckEntity) -> Unit) {
            tvTitle.text = deck.title
            tvDesc.text = deck.description
            // Placeholder for card count until deck with card count relation is ready
            chipCount.text = itemView.context.getString(R.string.deck_cards_count, 0)
            itemView.setOnClickListener {
                onDeckClick(deck)
            }
        }
    }

    class DiffCallback : DiffUtil.ItemCallback<DeckEntity>() {
        override fun areItemsTheSame(oldItem: DeckEntity, newItem: DeckEntity): Boolean {
            return oldItem.id == newItem.id
        }

        override fun areContentsTheSame(oldItem: DeckEntity, newItem: DeckEntity): Boolean {
            return oldItem == newItem
        }
    }
}