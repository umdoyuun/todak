package com.example.todak.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.R
import com.example.todak.data.model.Center

class CenterAdapter(private var centers: List<Center>) : RecyclerView.Adapter<CenterAdapter.CenterViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): CenterViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_center, parent, false)
        return CenterViewHolder(view)
    }

    override fun onBindViewHolder(holder: CenterViewHolder, position: Int) {
        holder.bind(centers[position])
    }

    override fun getItemCount(): Int = centers.size

    fun updateCenters(newCenters: List<Center>) {
        centers = newCenters
        notifyDataSetChanged()
    }

    class CenterViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val tvCenterName: TextView = itemView.findViewById(R.id.tv_center_name)

        fun bind(center: Center) {
            tvCenterName.text = center.centerName
        }
    }
}