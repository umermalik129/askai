package com.technatix.askai

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.ItemTouchHelper
import androidx.recyclerview.widget.RecyclerView
import java.util.Collections

/** Prompt rows with a drag handle for reordering. [onOrderChanged] fires after a drop. */
class PromptAdapter(
    private val onEdit: (Int) -> Unit,
    private val onDelete: (Int) -> Unit,
    private val onOrderChanged: (List<Prompt>) -> Unit
) : RecyclerView.Adapter<PromptAdapter.Holder>() {

    private val items = mutableListOf<Prompt>()
    private var touchHelper: ItemTouchHelper? = null

    fun submit(list: List<Prompt>) {
        items.clear()
        items.addAll(list)
        @Suppress("NotifyDataSetChanged")
        notifyDataSetChanged()
    }

    fun attachTo(recycler: RecyclerView) {
        val helper = ItemTouchHelper(object : ItemTouchHelper.SimpleCallback(
            ItemTouchHelper.UP or ItemTouchHelper.DOWN, 0
        ) {
            override fun isLongPressDragEnabled() = true

            override fun onMove(
                rv: RecyclerView, from: RecyclerView.ViewHolder, to: RecyclerView.ViewHolder
            ): Boolean {
                val a = from.bindingAdapterPosition
                val b = to.bindingAdapterPosition
                if (a == RecyclerView.NO_POSITION || b == RecyclerView.NO_POSITION) return false
                Collections.swap(items, a, b)
                notifyItemMoved(a, b)
                return true
            }

            override fun onSwiped(viewHolder: RecyclerView.ViewHolder, direction: Int) = Unit

            override fun onSelectedChanged(viewHolder: RecyclerView.ViewHolder?, actionState: Int) {
                super.onSelectedChanged(viewHolder, actionState)
                if (actionState == ItemTouchHelper.ACTION_STATE_DRAG) viewHolder?.itemView?.alpha = 0.7f
            }

            override fun clearView(rv: RecyclerView, viewHolder: RecyclerView.ViewHolder) {
                super.clearView(rv, viewHolder)
                viewHolder.itemView.alpha = 1f
                onOrderChanged(items.toList())
            }
        })
        helper.attachToRecyclerView(recycler)
        touchHelper = helper
        recycler.adapter = this
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(LayoutInflater.from(parent.context).inflate(R.layout.row_prompt, parent, false))

    override fun getItemCount() = items.size

    @SuppressLint("ClickableViewAccessibility")
    override fun onBindViewHolder(holder: Holder, position: Int) {
        val p = items[position]
        holder.label.text = p.label
        holder.preview.text = p.text
        holder.itemView.setOnClickListener { onEdit(holder.bindingAdapterPosition) }
        holder.delete.setOnClickListener { onDelete(holder.bindingAdapterPosition) }
        holder.handle.setOnTouchListener { _, e ->
            if (e.actionMasked == MotionEvent.ACTION_DOWN) touchHelper?.startDrag(holder)
            false
        }
    }

    class Holder(v: View) : RecyclerView.ViewHolder(v) {
        val handle: View = v.findViewById(R.id.handle)
        val label: TextView = v.findViewById(R.id.label)
        val preview: TextView = v.findViewById(R.id.preview)
        val delete: View = v.findViewById(R.id.delete)
    }
}
