package dev.legendsayantan.webrtctest

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.Button
import android.widget.EditText
import android.widget.TextView
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import dev.legendsayantan.webrtctest.ServerlessRTCClient.Companion.sessionDescriptionToJSON;
import java.security.Permission
import java.util.Timer
import kotlin.concurrent.timerTask

class MainActivity : AppCompatActivity() {
    val txt by lazy { findViewById<TextView>(R.id.text) }

    private val rtc by lazy {
        ServerlessRTCClient(this,object : ServerlessRTCClient.IStateChangeListener {
            override fun onStateChanged(state: ServerlessRTCClient.State) {
                println(state.name)
                when (state) {
                    ServerlessRTCClient.State.INITIALIZED -> {
                        txt.text = state.name
                    }
                    ServerlessRTCClient.State.WAITING_FOR_OFFER -> {
                    }
                    ServerlessRTCClient.State.CREATING_OFFER -> {

                    }
                    ServerlessRTCClient.State.CREATING_ANSWER -> {}
                    ServerlessRTCClient.State.WAITING_FOR_ANSWER -> {
                    }
                    ServerlessRTCClient.State.WAITING_TO_CONNECT -> {}
                    ServerlessRTCClient.State.CHAT_ESTABLISHED -> {}
                    ServerlessRTCClient.State.CHAT_ENDED -> {}
                }
            }
        })
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContentView(R.layout.activity_main)
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main)) { v, insets ->
            val systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom)
            insets
        }
        if(checkSelfPermission(android.Manifest.permission.READ_PHONE_STATE)!= PackageManager.PERMISSION_GRANTED){
            requestPermissions(arrayOf(android.Manifest.permission.READ_PHONE_STATE),1)
        }
        rtc.init()
        findViewById<Button>(R.id.buttonMakeOffer).setOnClickListener { createOffer() }
        findViewById<Button>(R.id.buttonWaitOffer).setOnClickListener { rtc.waitForOffer() }
        findViewById<Button>(R.id.buttonTakeOffer).setOnClickListener { acceptOffer() }
        findViewById<Button>(R.id.buttonTakeAnswer).setOnClickListener { acceptAnswer() }

    }

    fun createOffer() {
        rtc.makeOffer{
            sessionDescriptionToJSON(it).toString().let { json->
                runOnUiThread {
                    findViewById<TextView>(R.id.text).apply {
                        text = json
                        setOnClickListener {
                            copyToClipboard(json)
                        }
                        setOnLongClickListener {
                            text = "WAITING"
                            rtc.waitForOffer()
                            true
                        }
                    }
                }
            }

        }
    }

    fun acceptOffer(){
        rtc.processOffer(findViewById<EditText>(R.id.input).text.toString()){
            sessionDescriptionToJSON(it).toString().let { json->
                runOnUiThread {
                    findViewById<TextView>(R.id.text).apply {
                        text = json
                        setOnClickListener {
                            copyToClipboard(json)
                        }
                        setOnLongClickListener {
                            text = "CREATING"
                            createOffer()
                            true
                        }
                    }
                }
            }
        }
    }

    fun acceptAnswer(){
        rtc.processAnswer(findViewById<EditText>(R.id.input).text.toString()) {
            println("CONNECTED")
            rtc.sendMessage("HI")
        }
    }
    fun copyToClipboard(text: String) {
        val clipboard = getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
        val clip = ClipData.newPlainText("SDP", text)
        clipboard.setPrimaryClip(clip)
    }
}