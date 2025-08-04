package com.auo.flex_compositor.pNtpTimeHelper

import android.app.AlarmManager
import android.content.Context
import android.os.SystemClock
import android.util.Log
import org.apache.commons.net.ntp.NTPUDPClient
import org.apache.commons.net.ntp.TimeInfo
import java.net.InetAddress


class NtpTimeHelper(context: Context) {
    private var is_running = false
    private var is_exit = false
    private val m_tag = "NtpTimeHelper"
    private val m_context = context
    fun syncSystemTimeWithNtp(ntpHost: String? = null, zone: String = "Asia/Taipei") {
        if(is_running){
            return
        }
        is_exit = false
        is_running = true
        Thread {
            val retryDelay: Long = 6000L // milliseconds
            var retryCount = 0
            val maxRetries: Int = 1000
            val client: NTPUDPClient = NTPUDPClient()
            client.setDefaultTimeout(5000)
            while (retryCount < maxRetries && is_exit == false) {
                retryCount++
                try {
                    val iNtpHost: String
                    if (ntpHost == null) {
                        iNtpHost = "time.google.com"
                    } else {
                        iNtpHost = ntpHost
                    }
                    val inetAddress = InetAddress.getByName(iNtpHost)
                    val timeInfo: TimeInfo = client.getTime(inetAddress)
                    timeInfo.computeDetails()
                    val time: Long = timeInfo.getMessage().getTransmitTimeStamp().getTime()
                    val success = SystemClock.setCurrentTimeMillis(time)
                    val am = m_context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
                    am.setTimeZone(zone)
                    Log.d(m_tag, "Set time result: $time $success")
                    client.close()
                    break
                } catch (e: Exception) {
                    e.printStackTrace()
                }
                Thread.sleep(retryDelay)
            }
            is_running = false
        }.start()
    }

    fun close(){
        is_exit = true
    }
}