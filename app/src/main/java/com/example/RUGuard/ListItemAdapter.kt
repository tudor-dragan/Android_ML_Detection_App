package com.example.RUGuard

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ListItemAdapter(private val context: Context, private val dataset: List<ApplicationModel>, private val checkedArray: BooleanArray
) : RecyclerView.Adapter<ListItemAdapter.ViewHolder>(){

    class ViewHolder(private val view: View, private val checkedArray: BooleanArray) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.app_title)
        val imageView: androidx.appcompat.widget.AppCompatImageView = view.findViewById(R.id.app_icon)
        // set the on click of the checkbox to toggle the boolean value in the checkedArray
        val checkBox: androidx.appcompat.widget.AppCompatCheckBox = view.findViewById(R.id.app_checkbox)

        init {
            checkBox.setOnClickListener {
                checkedArray[adapterPosition] = checkBox.isChecked
            }
        }

    }



    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context).inflate(R.layout.list_item_v2, parent, false)
        return ViewHolder(adapterLayout, checkedArray)
    }

    override fun getItemCount(): Int {
        return dataset.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataset[position]
        holder.textView.text = item.appName
        holder.imageView.setImageDrawable(item.icon)
        holder.checkBox.isChecked = checkedArray[position]
    }
}