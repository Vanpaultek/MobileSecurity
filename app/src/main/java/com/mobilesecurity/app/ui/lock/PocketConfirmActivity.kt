package com.mobilesecurity.app.ui.lock

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.view.WindowManager
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.mobilesecurity.app.ui.theme.MobileSecurityTheme

class PocketConfirmActivity : FragmentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.setFlags(
            WindowManager.LayoutParams.FLAG_SECURE,
            WindowManager.LayoutParams.FLAG_SECURE
        )
        val target = intent.getStringExtra(EXTRA_NUMBER).orEmpty()

        setContent {
            MobileSecurityTheme {
                Column(
                    modifier = Modifier.fillMaxSize().padding(24.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.Center
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null)
                    Text(
                        "Pocket-dial guard",
                        style = MaterialTheme.typography.headlineMedium,
                        modifier = Modifier.padding(top = 16.dp)
                    )
                    Text(
                        "Your phone sensors suggest it may be in a pocket or face-down. " +
                            "Confirm you really want to call $target.",
                        style = MaterialTheme.typography.bodyMedium,
                        modifier = Modifier.padding(top = 8.dp)
                    )
                    Row(
                        modifier = Modifier.padding(top = 24.dp),
                        horizontalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        OutlinedButton(onClick = { finish() }) { Text("Cancel") }
                        Button(onClick = {
                            val dial = Intent(Intent.ACTION_DIAL, Uri.parse("tel:$target"))
                            dial.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                            startActivity(dial)
                            finish()
                        }) { Text("Place call") }
                    }
                }
            }
        }
    }

    companion object {
        const val EXTRA_NUMBER = "number"

        fun intent(number: String): Intent =
            Intent().setClassName(
                "com.mobilesecurity.app",
                "com.mobilesecurity.app.ui.lock.PocketConfirmActivity"
            ).putExtra(EXTRA_NUMBER, number)
                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    }
}
