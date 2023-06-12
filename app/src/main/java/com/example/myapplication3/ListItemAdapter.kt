package com.example.myapplication3

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView

class ListItemAdapter(private val context: Context, private val dataset: List<ApplicationListModel>) : RecyclerView.Adapter<ListItemAdapter.ViewHolder>(){
    fun getDataset(): List<ApplicationListModel> {
        return dataset
    }

    class ViewHolder(private val view: View) : RecyclerView.ViewHolder(view) {
        val textView: TextView = view.findViewById(R.id.app_title)
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): ViewHolder {
        val adapterLayout = LayoutInflater.from(parent.context).inflate(R.layout.list_item, parent, false)
        return ViewHolder(adapterLayout)
    }

    override fun getItemCount(): Int {
        return dataset.size
    }

    override fun onBindViewHolder(holder: ViewHolder, position: Int) {
        val item = dataset[position]
        holder.textView.text = item.packageName
    }
}