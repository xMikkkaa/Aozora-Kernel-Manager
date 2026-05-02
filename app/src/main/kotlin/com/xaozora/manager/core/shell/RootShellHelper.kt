package com.xaozora.manager.core.shell

import java.io.BufferedReader
import java.io.DataOutputStream
import java.io.File
import java.io.InputStreamReader

object RootShellHelper {

    fun executeCmd(cmd: String): Boolean {
        return try {
            val process = Runtime.getRuntime().exec(arrayOf("su", "-mm", "-c", cmd))
            process.waitFor() == 0
        } catch (e: Exception) {
            e.printStackTrace()
            false
        }
    }

    fun executeCmdAndGetOutput(cmd: String): String {
        return try {
            val process = Runtime.getRuntime().exec("su")
            val os = DataOutputStream(process.outputStream)
            val reader = BufferedReader(InputStreamReader(process.inputStream))

            os.writeBytes("$cmd\n")
            os.writeBytes("exit\n")
            os.flush()

            val output = StringBuilder()
            var line: String?
            while (reader.readLine().also { line = it } != null) {
                output.append(line).append("\n")
            }
            process.waitFor()
            output.toString().trim()
        } catch (e: Exception) {
            e.printStackTrace()
            ""
        }
    }

    fun readSystemFile(path: String): String {
        return executeCmdAndGetOutput("cat $path")
    }

    fun writeSystemFile(path: String, value: String): Boolean {
        return executeCmd("echo '$value' > $path")
    }

    fun checkFileExists(path: String): Boolean {
        val f = File(path)
        if (f.exists()) return true
        return executeCmd("test -f $path")
    }
}