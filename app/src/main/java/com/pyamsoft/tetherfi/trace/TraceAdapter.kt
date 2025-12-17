package com.pyamsoft.tetherfi.trace

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.pyamsoft.tetherfi.R

class TraceAdapter : RecyclerView.Adapter<TraceAdapter.ViewHolder>() {

  private val lines = mutableListOf<String>()

  fun addLine(line: String) {
    lines.add(line)
    notifyItemInserted(lines.size - 1)
  }

  override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
    val v = LayoutInflater.from(parent.context).inflate(R.layout.item_trace_line, parent, false)
    return ViewHolder(v)
  }

  override fun onBindViewHolder(holder: ViewHolder, position: Int) {
    holder.bind(lines[position])
  }

  override fun getItemCount(): Int = lines.size

  class ViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
    private val text: TextView = itemView.findViewById(R.id.trace_text)
    fun bind(s: String) { text.text = s }
  }
}
