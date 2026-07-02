package com.accident.detector

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.telephony.SmsManager
import android.util.Log

class AlertManager(private val context: Context) {

    // Call this when accident confirmed and user didn't respond
    fun triggerEmergency(latitude: Double, longitude: Double) {
        val contacts = getEmergencyContacts()
        val locationLink = "https://maps.google.com/?q=$latitude,$longitude"
        val message = """
            🚨 ACCIDENT ALERT!
            Your contact may have been in an accident.
            Last known location:
            $locationLink
            Please check on them immediately!
        """.trimIndent()

        // Send SMS to all contacts
        contacts.forEach { number ->
            sendSMS(number, message)
        }

        // Call first contact (family)
        if (contacts.isNotEmpty()) {
            makeCall(contacts[0])
        }

        Log.d("AlertManager", "Emergency triggered! Location: $latitude, $longitude")
    }

    private fun sendSMS(phoneNumber: String, message: String) {
        try {
            val smsManager = SmsManager.getDefault()
            // Split if message is too long
            val parts = smsManager.divideMessage(message)
            smsManager.sendMultipartTextMessage(
                phoneNumber, null, parts, null, null
            )
            Log.d("AlertManager", "SMS sent to $phoneNumber")
        } catch (e: Exception) {
            Log.e("AlertManager", "SMS failed to $phoneNumber: ${e.message}")
        }
    }

    private fun makeCall(phoneNumber: String) {
        try {
            val intent = Intent(Intent.ACTION_CALL).apply {
                data = Uri.parse("tel:$phoneNumber")
                flags = Intent.FLAG_ACTIVITY_NEW_TASK
            }
            context.startActivity(intent)
        } catch (e: Exception) {
            Log.e("AlertManager", "Call failed: ${e.message}")
        }
    }

    // Get saved emergency contacts from SharedPreferences
    private fun getEmergencyContacts(): List<String> {
        val prefs = context.getSharedPreferences("contacts", Context.MODE_PRIVATE)
        val contacts = mutableListOf<String>()

        val family    = prefs.getString("family", "") ?: ""
        val police    = prefs.getString("police", "100") ?: "100"
        val ambulance = prefs.getString("ambulance", "108") ?: "108"

        if (family.isNotEmpty())    contacts.add(family)
        if (police.isNotEmpty())    contacts.add(police)
        if (ambulance.isNotEmpty()) contacts.add(ambulance)

        return contacts
    }

    // Save contacts
    fun saveContacts(family: String, police: String, ambulance: String) {
        val prefs = context.getSharedPreferences("contacts", Context.MODE_PRIVATE)
        prefs.edit().apply {
            putString("family",    family)
            putString("police",    police)
            putString("ambulance", ambulance)
            apply()
        }
    }
}