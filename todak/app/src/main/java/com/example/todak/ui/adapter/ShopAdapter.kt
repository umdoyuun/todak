package com.example.todak.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.example.todak.R

class ShopAdapter(
    private val shops: List<Shop>,
    private val onItemClick: (Shop) -> Unit
) : RecyclerView.Adapter<ShopAdapter.ShopViewHolder>() {

    class ShopViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgShop: ImageView = view.findViewById(R.id.img_shop)
        val tvShopName: TextView = view.findViewById(R.id.tv_shop_name)
        val tvShopDesc: TextView = view.findViewById(R.id.tv_shop_desc)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ShopViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_shop, parent, false)
        return ShopViewHolder(view)
    }

    override fun onBindViewHolder(holder: ShopViewHolder, position: Int) {
        val shop = shops[position]

        holder.tvShopName.text = shop.name

        // 이미지 리소스 설정 (실제로는 Glide나 Picasso 등을 사용하여 URL에서 이미지 로드)
        holder.imgShop.setImageResource(shop.imageResId)

        // 아이템 클릭 리스너 설정
        holder.itemView.setOnClickListener {
            onItemClick(shop)
        }
    }

    override fun getItemCount() = shops.size
}

// 가게 정보를 담는 데이터 클래스
data class Shop(
    val id: String,
    val name: String,
    val imageResId: Int // 나중에 API 연동 시 imageUrl 등으로 변경 가능
)