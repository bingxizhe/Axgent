package com.stardust.autojs.runtime.api

import com.hzy.libp7zip.P7ZipApi
import com.stardust.autojs.annotation.ScriptInterface
import com.stardust.autojs.runtime.exception.ScriptException
import com.stardust.pio.PFiles.isDir
import com.stardust.pio.PFiles.isFile

class SevenZip {

    @ScriptInterface
    fun cmdExec(cmdStr: String?): Int {
        try {
            return P7ZipApi.executeCommand(cmdStr)
        } catch (e: Exception) {
            throw ScriptException(e)
        }
    }

    @ScriptInterface
    fun A(type: String, destFilePath: String, srcPath: String): Int {
        val typeOption = "-t${type.trim()}"
        val cmdStr: String = if (isFile(srcPath)) {
            "7z a -y $typeOption -ms=off -mx=1 -mmt $destFilePath $srcPath"
        } else if (isDir(srcPath)) {
            "7z a -y $typeOption -ms=off -mx=1 -mmt -r $destFilePath $srcPath"
        }else {
            throw ScriptException("the srcPath must be a file path or dir path")
        }
        try {
            return P7ZipApi.executeCommand(cmdStr)
        } catch (e: Exception) {
            throw ScriptException(e)
        }
    }

    @ScriptInterface
    fun A(type: String, destFilePath: String, srcPath: String, password: String): Int {
        val typeOption = "-t${type.trim()}"
        var cmdStr = "7z"
        if (isFile(srcPath)) {
            cmdStr = "7z a -y $typeOption -ms=off -mx=1 -mmt -p$password $destFilePath $srcPath"
        } else if (isDir(srcPath)) {
            cmdStr = "7z a -y $typeOption -ms=off -mx=1 -mmt -r -p$password $destFilePath $srcPath"
        }
        try {
            return P7ZipApi.executeCommand(cmdStr)
        } catch (e: Exception) {
            throw ScriptException(e)
        }
    }

    @ScriptInterface
    fun X(filePath: String, dirPath1: String): Int {
        check(isFile(filePath)) { "the filePath must be a file path" }
        val cmdStr = "7z x -y -aos -o$dirPath1 $filePath"
        try {
            return P7ZipApi.executeCommand(cmdStr)
        } catch (e: Exception) {
            throw ScriptException(e)
        }
    }

    @ScriptInterface
    fun X(filePath: String, dirPath: String, password: String): Int {
        check(isFile(filePath)) { "the filePath must be a file path" }
        var cmdStr: String
        if (password.isEmpty()) {
            return X(filePath, dirPath)
        } else {
            cmdStr = "7z x -y -aos -p$password -o$dirPath $filePath"
        }
        try {
            return P7ZipApi.executeCommand(cmdStr)
        } catch (e: Exception) {
            throw ScriptException(e)
        }
    }
}