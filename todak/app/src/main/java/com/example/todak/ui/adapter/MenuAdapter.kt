package com.example.todak.ui.adapter

import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import com.bumptech.glide.Glide
import com.example.todak.R
import com.example.todak.data.model.MenuResponse


// 메뉴 어댑터
class MenuAdapter(
    private val menus: List<MenuResponse>,
    private val onItemClick: (MenuResponse) -> Unit = {} // 기본값을 빈 람다로 설정하여 옵션으로 만듦
) : RecyclerView.Adapter<MenuAdapter.MenuViewHolder>() {

    class MenuViewHolder(view: View) : RecyclerView.ViewHolder(view) {
        val imgMenu: ImageView = view.findViewById(R.id.img_menu)
        val tvMenuName: TextView = view.findViewById(R.id.tv_menu_name)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MenuViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.item_menu, parent, false)
        return MenuViewHolder(view)
    }

    override fun onBindViewHolder(holder: MenuViewHolder, position: Int) {
        val menu = menus[position]

        holder.tvMenuName.text = menu.title

        // Glide를 사용하여 이미지 로드
        Glide.with(holder.itemView.context)
            .load(menu.title_img) // menu.imageUrl은 MenuResponse의 이미지 URL 필드입니다.
            .error(R.drawable.img_shop_sample) // 로딩 실패 시 표시할 이미지
            .into(holder.imgMenu)

        // 아이템 클릭 리스너 설정
        holder.itemView.setOnClickListener {
            onItemClick(menu)
        }
    }

    override fun getItemCount() = menus.size
}