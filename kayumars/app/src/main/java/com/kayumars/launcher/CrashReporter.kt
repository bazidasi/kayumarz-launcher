package com.kayumars.launcher

import android.app.Activity
import android.app.AlertDialog
import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.os.Build
import java.io.File
import java.io.PrintWriter
import java.io.StringWriter

object CrashReporter {
    private fun file(context: Context) = File(context.filesDir, "last_crash.txt")

    fun install(context: Context) {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, throwable ->
            runCatching {
                val sw = StringWriter()
                throwable.printStackTrace(PrintWriter(sw))
                file(context).writeText(
                    "device=${Build.MANUFACTURER}/${Build.MODEL} sdk=${Build.VERSION.SDK_INT}\n" +
                        "thread=${thread.name}\n$sw"
                )
            }
            previous?.uncaughtException(thread, throwable)
        }
    }

    fun showIfPresent(activity: Activity) {
        val f = file(activity)
        if (!f.exists()) return
        val javaCrash = runCatching { f.readText() }.getOrDefault("")
        // Native aborts (SIGSEGV, JNI errors) never reach the Java handler; the crash buffer keeps them.
        val nativeCrash = runCatching {
            val proc = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-b", "crash", "-t", "300"))
            proc.inputStream.bufferedReader().use { it.readText() }.takeLast(8000)
        }.getOrDefault("")
        val text = (javaCrash + "\n--- logcat crash ---\n" + nativeCrash).trim()
        AlertDialog.Builder(activity)
            .setTitle("Crash log — Copy and send")
            .setMessage(text.ifEmpty { "no crash data captured" })
            .setCancelable(false)
            .setPositiveButton("Copy") { _, _ ->
                val cm = activity.getSystemService(Context.CLIPBOARD_SERVICE) as ClipboardManager
                cm.setPrimaryClip(ClipData.newPlainText("crash", text))
                f.delete()
            }
            .setNegativeButton("Close") { _, _ -> f.delete() }
            .show()
    }
}