package com.example.grad_project2.activity

import android.os.Bundle
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import androidx.fragment.app.commit
import com.example.grad_project2.R
import com.example.grad_project2.fragment.ChatFragment
import com.example.grad_project2.fragment.ChatListFragment

class ChatSessionsActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_chat_sessions)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if (savedInstanceState == null) {
            supportFragmentManager.commit {
                replace(R.id.fragment_container, ChatListFragment())
            }
        }
    }

    fun navigateToChatFragment(endpointId: String, peerName: String) {
        val chatFragment = ChatFragment.newInstance(endpointId, peerName)
        supportFragmentManager.commit {
            replace(R.id.fragment_container, chatFragment)
            addToBackStack(null)
        }
    }
}