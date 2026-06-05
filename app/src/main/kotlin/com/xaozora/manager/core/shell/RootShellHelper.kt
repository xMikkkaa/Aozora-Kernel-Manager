package com.xaozora.manager.core.shell

import java.io.File

object RootShellHelper {
    init {
        System.loadLibrary("native")
    }

    external fun executeCmd(cmd: String): Boolean
    external fun executeCmdAndGetOutput(cmd: String): String
    external fun readSystemFile(path: String): String
    external fun writeSystemFile(path: String, value: String): Boolean
    external fun checkFileExists(path: String): Boolean
}