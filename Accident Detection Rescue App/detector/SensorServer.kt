package com.accident.detector

import fi.iki.elonen.NanoHTTPD
import org.json.JSONObject

class SensorServer(
    port: Int,
    private val onDataReceived: (ax: Float, ay: Float, az: Float,
                                 gx: Float, gy: Float, gz: Float) -> Unit
) : NanoHTTPD(port) {

    override fun serve(session: IHTTPSession): Response {
        return try {
            if (session.method == Method.POST) {

                // Read POST body
                val contentLength = session.headers["content-length"]?.toInt() ?: 0
                val buffer = ByteArray(contentLength)
                session.inputStream.read(buffer, 0, contentLength)
                val body = String(buffer)

                // Parse JSON from ESP32
                // Expected: {"ax":0.12,"ay":0.03,"az":1.01,"gx":2.1,"gy":0.8,"gz":0.4}
                val json = JSONObject(body)
                val ax = json.getDouble("ax").toFloat()
                val ay = json.getDouble("ay").toFloat()
                val az = json.getDouble("az").toFloat()
                val gx = json.getDouble("gx").toFloat()
                val gy = json.getDouble("gy").toFloat()
                val gz = json.getDouble("gz").toFloat()

                // Send to main app logic
                onDataReceived(ax, ay, az, gx, gy, gz)

                newFixedLengthResponse(
                    Response.Status.OK,
                    "application/json",
                    """{"status":"ok"}"""
                )
            } else {
                newFixedLengthResponse(
                    Response.Status.OK,
                    "text/plain",
                    "AccidentDetector server running"
                )
            }
        } catch (e: Exception) {
            e.printStackTrace()
            newFixedLengthResponse(
                Response.Status.INTERNAL_ERROR,
                "application/json",
                """{"status":"error","message":"${e.message}"}"""
            )
        }
    }
}