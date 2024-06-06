package com.mindbox.example

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.AsyncListDiffer
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.RecyclerView
import cloud.mindbox.mobile_sdk.pushes.MindboxRemoteMessage
import com.bumptech.glide.Glide
import com.mindbox.example.databinding.ItemNotificationBinding

class NotificationAdapter(private val onItemClick:(MindboxRemoteMessage) -> Unit): RecyclerView.Adapter<NotificationAdapter.NotificationViewHolder>() {

    private val differ = AsyncListDiffer(this, MindboxRemoteMessageItemCallback())

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): NotificationViewHolder {
        return NotificationViewHolder(
            ItemNotificationBinding.inflate(
                LayoutInflater.from(parent.context),
                parent,
                false
            )
        )
    }

    override fun getItemCount(): Int {
        return differ.currentList.size
    }

    override fun onBindViewHolder(holder: NotificationViewHolder, position: Int) {
        holder.bind(differ.currentList[position])
    }

    fun updateNotifications(newList: List<MindboxRemoteMessage>) {
        differ.submitList(newList)
    }
    inner class NotificationViewHolder(private val binding: ItemNotificationBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(item: MindboxRemoteMessage) {
            binding.root.setOnClickListener {
                onItemClick(item)
            }
            binding.tvTitle.text = item.title
            binding.tvDescription.text = item.description
            binding.tvPushLink.text = item.pushLink
            binding.tvUniqueKey.text = item.uniqueKey
            Glide.with(itemView).load(item.imageUrl).into(binding.ivImage)
        }
    }

    class MindboxRemoteMessageItemCallback : DiffUtil.ItemCallback<MindboxRemoteMessage>() {
        override fun areItemsTheSame(
            oldItem: MindboxRemoteMessage,
            newItem: MindboxRemoteMessage
        ): Boolean {
            return oldItem.uniqueKey == newItem.uniqueKey
        }

        override fun areContentsTheSame(
            oldItem: MindboxRemoteMessage,
            newItem: MindboxRemoteMessage
        ): Boolean {
            return oldItem == newItem
        }
    }

}