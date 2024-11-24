import android.content.Context
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.grad_project2.ChatGlobal
import androidx.core.content.ContextCompat
import com.example.grad_project2.R
import com.example.grad_project2.SocketConnection



class ChatItemAdapter(
    private val context: Context,
    private val itemList: List<ChatGlobal>,
    private val socketConnection: SocketConnection) :
    RecyclerView.Adapter<ChatItemAdapter.MyViewHolder>() {

    // ViewHolder class to hold references to each item view
    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.chatTitleTextView)
        val descriptionTextView: TextView = itemView.findViewById(R.id.chatSubtitleTextView)
    }

    // Inflate the item layout and create ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)
        return MyViewHolder(view)
    }

    // Bind data to each ViewHolder
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        val item = itemList[position]
        holder.titleTextView.text = item.ip
        holder.descriptionTextView.text = item.port.toString()
        if (item.isSubscribed) {
            holder.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.gradient_background))
            val onlineIndicator = holder.itemView.findViewById<View>(R.id.onlineIndicator)
            onlineIndicator.setBackground(ContextCompat.getDrawable(context, R.drawable.circle_background_green))
        } else {
            holder.itemView.setBackground(ContextCompat.getDrawable(context, R.drawable.not_clicked_session))
            val onlineIndicator = holder.itemView.findViewById<View>(R.id.onlineIndicator)
            onlineIndicator.setBackground(ContextCompat.getDrawable(context, R.drawable.circle_bacground))
        }

        holder.itemView.setOnClickListener {
            if (item.isSubscribed) {
                // Unsubscribe logic
                item.isSubscribed = false
                item.subscriptionJob?.cancel()
                item.subscriptionJob = null
                notifyItemChanged(position)
                Toast.makeText(context, "Unsubscribed from ${item.ip}", Toast.LENGTH_SHORT).show()
            } else {
                socketConnection.subscribeToSession(
                    item.ip,
                    item.port,
                    onMessageReceived = { message ->
                        // Handle received messages
                        Log.d("SessionMessage", "Received from ${item.ip}: $message")
                        // Example: Show a Toast (Ensure to limit Toasts to prevent spamming)
                        Toast.makeText(context, "Message from ${item.ip}: $message", Toast.LENGTH_SHORT).show()
                    },
                    connectTimeoutMillis = 5000,
                    onSubscriptionSuccess = {
                        // Update subscription state upon successful connection
                        item.isSubscribed = true
                        notifyItemChanged(position)
                        Toast.makeText(context, "Subscribed to ${item.ip}", Toast.LENGTH_SHORT).show()
                    },
                    onSubscriptionFailed = { exception ->
                        // Handle subscription failure
                        Toast.makeText(context, "Failed to subscribe to ${item.ip}: ${exception.message}", Toast.LENGTH_SHORT).show()
                    }
                )?.let { job ->
                    // Store the subscription job
                    item.subscriptionJob = job
                }
            }
        }
    }

    // Return the total number of items
    override fun getItemCount(): Int = itemList.size

}
