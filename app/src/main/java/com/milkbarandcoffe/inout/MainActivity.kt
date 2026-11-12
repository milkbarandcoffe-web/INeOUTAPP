package com.milkbarandcoffe.inout

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.content.Intent
import android.nfc.NfcAdapter
import android.nfc.Tag
import android.os.Build
import android.os.Bundle
import android.os.VibrationEffect
import android.os.Vibrator
import android.widget.*
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.NotificationCompat
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

class MainActivity : AppCompatActivity() {

    private lateinit var etChiave: EditText
    private lateinit var etScriptUrl: EditText
    private lateinit var tvStatoTag: TextView
    private var inModalitaAssociazione = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        if (intent.action in listOf(NfcAdapter.ACTION_TAG_DISCOVERED, NfcAdapter.ACTION_NDEF_DISCOVERED)) {
            gestisciToccoNfc(intent)
            return
        }

        setContentView(R.layout.activity_main)
        etChiave = findViewById(R.id.etChiave)
        etScriptUrl = findViewById(R.id.etScriptUrl)
        tvStatoTag = findViewById(R.id.tvStatoTag)

        val prefs = getSharedPreferences("InOutPrefs", Context.MODE_PRIVATE)
        etChiave.setText(prefs.getString("user_key", ""))
        etScriptUrl.setText(prefs.getString("script_url", ""))

        findViewById<Button>(R.id.btnAssociaTag).setOnClickListener {
            inModalitaAssociazione = true
            tvStatoTag.text = "⏳ AVVICINA IL TAG AL TELEFONO..."
        }

        findViewById<Button>(R.id.btnSalva).setOnClickListener {
            prefs.edit()
                .putString("user_key", etChiave.text.toString().trim())
                .putString("script_url", etScriptUrl.text.toString().trim())
                .apply()
            Toast.makeText(this, "Configurazione salvata!", Toast.LENGTH_SHORT).show()
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        gestisciToccoNfc(intent)
    }

    private fun gestisciToccoNfc(intent: Intent) {
        val tag: Tag? = intent.getParcelableExtra(NfcAdapter.EXTRA_TAG) ?: return
        val tagUid = tag.id.joinToString(":") { "%02X".format(it) }
        val prefs = getSharedPreferences("InOutPrefs", Context.MODE_PRIVATE)

        if (inModalitaAssociazione) {
            prefs.edit().putString("saved_tag_uid", tagUid).apply()
            tvStatoTag.text = "✓ Tag Associato: $tagUid"
            inModalitaAssociazione = false
        } else {
            val savedUid = prefs.getString("saved_tag_uid", null)
            if (savedUid != null && savedUid.equals(tagUid, ignoreCase = true)) {
                vibra()
                inviaPresenza()
                finish()
            }
        }
    }

    private fun inviaPresenza() {
        val prefs = getSharedPreferences("InOutPrefs", Context.MODE_PRIVATE)
        val chiave = prefs.getString("user_key", "") ?: return
        val scriptUrl = prefs.getString("script_url", "") ?: return

        CoroutineScope(Dispatchers.IO).launch {
            try {
                val conn = URL(scriptUrl).openConnection() as HttpURLConnection
                conn.requestMethod = "POST"
                conn.setRequestProperty("Content-Type", "text/plain;charset=utf-8")
                conn.doOutput = true
                OutputStreamWriter(conn.outputStream).use { it.write("""{"chiave":"$chiave"}""") }

                if (conn.responseCode in 200..399) {
                    mostraNotifica("✓ INOUT", "Presenza registrata con successo")
                }
            } catch (e: Exception) {
                mostraNotifica("Errore INOUT", "Impossibile inviare presenza")
            }
        }
    }

    private fun mostraNotifica(titolo: String, testo: String) {
        val channelId = "in_out_channel"
        val manager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(channelId, "INOUT", NotificationManager.IMPORTANCE_HIGH))
        }
        val notif = NotificationCompat.Builder(this, channelId)
            .setSmallIcon(android.R.drawable.stat_sys_upload_done)
            .setContentTitle(titolo)
            .setContentText(testo)
            .build()
        manager.notify(1, notif)
    }

    private fun vibra() {
        val v = getSystemService(Context.VIBRATOR_SERVICE) as Vibrator
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            v.vibrate(VibrationEffect.createOneShot(150, VibrationEffect.DEFAULT_AMPLITUDE))
        } else {
            v.vibrate(150)
        }
    }
}