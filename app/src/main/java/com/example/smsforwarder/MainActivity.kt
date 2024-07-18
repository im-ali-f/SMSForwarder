package com.example.smsforwarder

import android.Manifest
import android.app.Activity
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.telephony.SmsMessage
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import android.app.Service
import android.content.pm.PackageManager
import android.os.IBinder
import android.preference.PreferenceManager
import android.telephony.SmsManager
import android.widget.Toast
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat


class MainActivity : ComponentActivity() {
    private val requestPermissionsLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions()
    ) { permissions ->
        permissions.entries.forEach {
            Log.d("Permissions", "${it.key} = ${it.value}")
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MainScreen()
        }

        checkPermissions()
    }

    private fun checkPermissions() {
        val requiredPermissions = arrayOf(
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_SMS
        )

        val permissionsToRequest = requiredPermissions.filter {
            ContextCompat.checkSelfPermission(this, it) != PackageManager.PERMISSION_GRANTED
        }

        if (permissionsToRequest.isNotEmpty()) {
            requestPermissionsLauncher.launch(permissionsToRequest.toTypedArray())
        }
    }
}

@Composable
fun MainScreen() {
    var forwardNumber by remember { mutableStateOf("") }
    val context = LocalContext.current

    Column(modifier = Modifier.padding(16.dp)) {
        Text(text = "SMS Forwarder")
        OutlinedTextField(
            value = forwardNumber,
            onValueChange = { forwardNumber = it },
            label = { Text("Forward to number") },
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp)
        )
        Button(onClick = {
            if (forwardNumber.isNotEmpty()) {
                saveForwardNumber(context, forwardNumber)
                Toast.makeText(context, "Number saved", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(context, "Please enter a number", Toast.LENGTH_SHORT).show()
            }
        }) {
            Text(text = "Save Number")
        }
    }
}
fun saveForwardNumber(context: Context, number: String) {
    val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(context)
    with(sharedPreferences.edit()) {
        putString("forward_number", number)
        apply()
    }
}

class SMSReceiver : BroadcastReceiver() {

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action.equals("android.provider.Telephony.SMS_RECEIVED")) {
            val bundle: Bundle? = intent.extras
            val pdus = bundle?.get("pdus") as? Array<*>
            if (pdus != null) {
                for (pdu in pdus) {
                    val smsMessage = SmsMessage.createFromPdu(pdu as ByteArray)
                    val sender = smsMessage.displayOriginatingAddress
                    val messageBody = smsMessage.messageBody

                    Log.d("SMSReceiver", "SMS from: $sender, message: $messageBody")

                    // Start the service to forward the SMS
                    val serviceIntent = Intent(context, SMSService::class.java).apply {
                        putExtra("sender", sender)
                        putExtra("messageBody", messageBody)
                    }
                    context.startService(serviceIntent)
                }
            }
        }
    }
}

class SMSService : Service() {

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        val sender = intent?.getStringExtra("sender")
        val messageBody = intent?.getStringExtra("messageBody")

        if (sender != null && messageBody != null) {
            val sharedPreferences = PreferenceManager.getDefaultSharedPreferences(this)
            val targetNumber = sharedPreferences.getString("forward_number", null)

            if (!targetNumber.isNullOrEmpty()) {
                Log.d("SMSService", "Forwarding SMS to $targetNumber with message: $messageBody")
                forwardSMS(targetNumber, messageBody)
            } else {
                Log.d("SMSService", "No target number set")
            }
        } else {
            Log.d("SMSService", "No sender or message body in intent")
        }

        return START_NOT_STICKY
    }

    private fun forwardSMS(targetNumber: String, messageBody: String) {
        try {
            val smsManager = SmsManager.getDefault()
            smsManager.sendTextMessage(targetNumber, null, messageBody, null, null)
            Log.d("SMSService", "SMS forwarded to: $targetNumber, message: $messageBody")
        } catch (e: Exception) {
            Log.e("SMSService", "Failed to forward SMS", e)
        }
    }

    override fun onBind(intent: Intent?): IBinder? {
        return null
    }
}

