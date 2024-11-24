import android.app.Activity
import android.content.Context
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.recyclerview.widget.RecyclerView
import com.example.grad_project2.ChatGlobal
import androidx.core.content.ContextCompat
import com.example.grad_project2.R
import com.example.grad_project2.SocketConnection
import org.json.JSONObject


class ChatItemAdapter(
    private val context: Context,
    private val itemList:   MutableList<ChatGlobal>,
    private val socketConnection: SocketConnection) :

    RecyclerView.Adapter<ChatItemAdapter.MyViewHolder>() {
    private val mainHandler = Handler(Looper.getMainLooper())
    // ViewHolder class to hold references to each item view
    class MyViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        val titleTextView: TextView = itemView.findViewById(R.id.chatTitleTextView)
        val descriptionTextView: TextView = itemView.findViewById(R.id.chatSubtitleTextView)
        //    var unreadMessages: Int = 0,
        //    var time: String = "Unknown"
        val unreadMessages : TextView =itemView.findViewById(R.id.unreadBadgeTextView)
        val timeSend: TextView =itemView.findViewById(R.id.timeTextView)
    }

    // Inflate the item layout and create ViewHolder
    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): MyViewHolder {
        val view = LayoutInflater.from(parent.context).inflate(R.layout.item_layout, parent, false)
        return MyViewHolder(view)
    }

    // Bind data to each ViewHolder
    override fun onBindViewHolder(holder: MyViewHolder, position: Int) {
        var item = itemList[position]
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
        val connectSocketImage = holder.itemView.findViewById<ImageView>(R.id.connectSocketImage)
        if (item.isSubscribed) {
            connectSocketImage.setImageResource(R.drawable.signal_disconnected) // Subscribed state
        } else {
            connectSocketImage.setImageResource(R.drawable.podcasts_connect) // Not subscribed state
        }
        //holder.itemView.setOnClickListener {
        connectSocketImage.setOnClickListener{
            if (item.isSubscribed) {
                // Unsubscribe logic
                item.isSubscribed = false
                item.subscriptionJob?.cancel()
                item.subscriptionJob = null
                notifyItemChanged(position)
                Toast.makeText(context, "Unsubscribed from ${item.ip}", Toast.LENGTH_SHORT).show()
                //connectSocketImage.setImageResource(R.drawable.podcasts_connect)
            } else {
                socketConnection.subscribeToSession(
                    item.ip,
                    item.port,
                    connectTimeoutMillis = 5000,
                    onMessageReceived = { message ->
                        // Handle received messages
                        Log.d("SessionMessage", "Received from ${item.ip}: $message")
                        // Example: Show a Toast (Ensure to limit Toasts to prevent spamming)
                        Toast.makeText(context, "Message from ${item.ip}: $message", Toast.LENGTH_SHORT).show()
                        // designed
                        val valueMessage = itemList[position].unreadMessages++;
                        //val jsonObject = JSONObject(message)
                        //var time = jsonObject.getString("timestamp")
                        //Toast.makeText(context, "Time: $time", Toast.LENGTH_SHORT).show()
                        Toast.makeText(context, "Count: $valueMessage", Toast.LENGTH_SHORT).show()
                        //item.time = time
                        //holder.unreadMessages.text = valueMessage.toString()
                        //itemList[position].unreadMessages++;
                        //notifyItemChanged(position)
                        holder.unreadMessages.text = valueMessage.toString()
                        mainHandler.post {
                            Toast.makeText(context, "BUUUUUM",Toast.LENGTH_SHORT).show()
                            notifyItemChanged(holder.adapterPosition)
                            holder.unreadMessages.text ="3"
                            //holder.timeSend.text = time
                        }
                    },
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
