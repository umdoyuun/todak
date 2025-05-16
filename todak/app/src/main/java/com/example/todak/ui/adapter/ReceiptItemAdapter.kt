package com.example.todak.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.R
import com.example.todak.data.model.MenuOcrItem

class ReceiptItemAdapter(private val items: List<MenuOcrItem>) :
    RecyclerView.Adapter<ReceiptItemAdapter.ReceiptViewHolder>() {

    class ReceiptViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val titleTextView: TextView = view.findViewById(R.id.tv_menu_title)
        val countTextView: TextView = view.findViewById(R.id.tv_menu_count)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ReceiptViewHolder {
        // item_receipt.xml 대신 직접 레이아웃 생성
        val itemView = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_receipt, parent, false)

        return ReceiptViewHolder(itemView)
    }

    override fun onBindViewHolder(holder: ReceiptViewHolder, position: Int) {
        val item = items[position]
        holder.titleTextView.text = item.title
        holder.countTextView.text = item.count
    }

    override fun getItemCount() = items.size
}