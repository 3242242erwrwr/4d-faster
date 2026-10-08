package com.example.faster4g

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.telephony.SmsMessage
import android.widget.Toast

class SmsReceiver : BroadcastReceiver() {

    companion object {
        var lastIncomingOrder: String = "Hozircha oflayn zakazlar yo'q"
    }

    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action == "android.provider.Telephony.SMS_RECEIVED") {
            val bundle = intent.extras
            if (bundle != null) {
                val pdus = bundle.get("pdus") as? Array<*>
                val format = bundle.getString("format")
                if (pdus != null) {
                    for (pdu in pdus) {
                        val smsMessage = SmsMessage.createFromPdu(pdu as ByteArray, format)
                        val messageBody = smsMessage.messageBody

                        if (messageBody.contains("FASTER4G_ORDER")) {
                            lastIncomingOrder = messageBody
                            Toast.makeText(context, "🚖 Yangi Oflayn Zakaz (SMS): $messageBody", Toast.LENGTH_LONG).show()
                        }
                    }
                }
            }
        }
    }
}
