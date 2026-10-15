package com.example.uepflip_flashcard.ui.main

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Button
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.uepflip_flashcard.R

class MsgAdapter(
    private val onViewMore: () -> Unit,
    private val onContinue: () -> Unit
) : RecyclerView.Adapter<MsgAdapter.ViewHolder>() {

    private var messages: List<Msg> = emptyList()

    fun submit(list: List<Msg>) {
        messages = list
        notifyDataSetChanged()
    }

    class ViewHolder(v: View) : RecyclerView.ViewHolder(v)

    override fun getItemViewType(position: Int) = messages[position].type

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val layout = when (viewType) {
            Msg.USER -> R.layout.item_chat_user
            Msg.ACCURACY -> R.layout.item_chat_accuracy
            else -> R.layout.item_chat_bot
        }
        return ViewHolder(LayoutInflater.from(parent.context).inflate(layout, parent, false))
    }

    override fun getItemCount() = messages.size

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val message = messages[position]
        val v = holder.itemView

        if (message.type == Msg.ACCURACY) {
            val score = message.score
            v.findViewById<TextView>(R.id.accuracy_note).text = score?.note.orEmpty()
            v.findViewById<TextView>(R.id.accuracy_pct).text =
                score?.let { v.context.getString(R.string.stat_percent, it.percent) }.orEmpty()
            v.findViewById<Button>(R.id.btn_more).setOnClickListener { onViewMore() }
            v.findViewById<Button>(R.id.btn_continue).setOnClickListener { onContinue() }
        } else {
            v.findViewById<TextView>(R.id.bubble).text = message.text
        }
    }
}
