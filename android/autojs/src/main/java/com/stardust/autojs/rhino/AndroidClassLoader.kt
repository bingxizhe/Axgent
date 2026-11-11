package com.stardust.autojs.rhino

import android.util.Log
import com.android.dx.command.dexer.Main
import com.stardust.io.Zip
import com.stardust.pio.PFiles.deleteFilesOfDir
import com.stardust.util.MD5
import dalvik.system.DexClassLoader
import org.mozilla.javascript.DefiningClassLoader
import java.io.File
import java.io.FileNotFoundException
import java.io.IOException
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Created by Stardust on 2017/4/5.
 */

/**
 * Create a new instance with the given parent classloader and cache dierctory
 *
 * @param parent the parent
 * @param dir    the cache directory
 */
class AndroidClassLoader(private val parent: ClassLoader, private val mCacheDir: File) :
    DefiningClassLoader() {
    private val classLoaderCache = mutableMapOf<String, DexClassLoader>()


    init {
        if (mCacheDir.exists()) {
            deleteFilesOfDir(mCacheDir)
        } else {
            mCacheDir.mkdirs()
        }
    }

    /**
     * {@inheritDoc}
     */
    override fun defineClass(name: String, data: ByteArray): Class<*> {
        Log.d(LOG_TAG, "defineClass: name = " + name + " data.length = " + data.size)
        val classFile = generateTempFile(name, false).absolutePath + ".jar"
        try {
            File(classFile).apply {
                ZipOutputStream(outputStream()).use {
                    it.putNextEntry(ZipEntry(name.replace('.', '/') + ".class"))
                    it.write(data)
                    it.closeEntry()
                }
            }
            generateTempFile(classFile).let {
                compileClassUseDx(File(classFile), it)
                it.setWritable(false, false)
                return DexClassLoader(it.path, null, null, parent)
                    .loadClass(name)
            }
        } catch (e: IOException) {
            throw FatalLoadingException(e)
        } catch (e: ClassNotFoundException) {
            throw FatalLoadingException(e)
        } finally {
            File(classFile).delete()
        }
    }

    @Throws(IOException::class)
    private fun generateTempFile(name: String, create: Boolean = false): File {
        val file =
            File(mCacheDir, (name.hashCode() + System.currentTimeMillis()).toString() + ".jar")
        if (create) {
            if (!file.exists()) {
                file.createNewFile()
            }
        } else {
            file.delete()
        }
        return file
    }

    @Throws(IOException::class)
    fun loadJar(jar: File) {
        Log.d(LOG_TAG, "loadJar: jar = $jar")
        if (!jar.exists() || !jar.canRead()) {
            throw FileNotFoundException("File does not exist or readable: " + jar.path)
        }
        val name = generateDexFileName(jar)
        val dexOut = File(mCacheDir, "$name.jar")
        if (dexOut.isFile) {
            loadDex(dexOut)
            return
        }
        compileClassUseDx(jar, dexOut)
        check(dexOut.isFile) { "Failed to compile $jar" }
        loadDex(dexOut)
    }

    private fun generateDexFileName(jar: File): String {
        val message = jar.path + "_" + jar.lastModified()
        return MD5.md5(message)
    }

    @Throws(FileNotFoundException::class)
    fun loadDex(file: File): DexClassLoader {
        Log.d(LOG_TAG, "loadDex: $file")
        check(file.exists()) { FileNotFoundException(file.path) }
        val id = generateDexFileName(file)
        return classLoaderCache.getOrPut(id) {
            val dexFile = File(mCacheDir, id)
            if (!dexFile.isFile) {
                if (file.isFile) {
                    file.copyTo(dexFile)
                } else ZipOutputStream(dexFile.outputStream()).use {
                    Zip.zipFileList(file.listFiles()?.toList() ?: emptyList(), it)
                }
            }
            dexFile.setWritable(false, false)
            DexClassLoader(dexFile.path, mCacheDir.path, null, parent)
        }
    }

    /**
     * Does nothing
     *
     * @param aClass ignored
     */
    override fun linkClass(aClass: Class<*>?) {
        resolveClass(aClass)
    }

    /**
     * Try to load a class. This will search all defined classes, all loaded jars and the parent class loader.
     *
     * @param name    the name of the class to load
     * @param resolve ignored
     * @return the class
     * @throws ClassNotFoundException if the class could not be found in any of the locations
     */
    @Throws(ClassNotFoundException::class)
    override fun loadClass(name: String, resolve: Boolean): Class<*>? {
        return findLoadedClass(name) ?: run {
            for (dex in classLoaderCache.values) {
                val `class` = dex.loadClass(name)
                if (`class` != null) return `class`
            }
            parent.loadClass(name)
        }
    }

    /**
     * Might be thrown in any Rhino method that loads bytecode if the loading failed
     */
    class FatalLoadingException internal constructor(t: Throwable?) :
        RuntimeException("Failed to define class", t)

    companion object {
        private const val LOG_TAG = "AndroidClassLoader"

        fun compileClassUseDx(classFile: File, outFile: File) {
            val arguments = Main.Arguments()
            arguments.fileNames = arrayOf<String>(classFile.path)
            arguments.outName = outFile.path
            arguments.jarOutput = true
            Main.run(arguments)
            Log.i(LOG_TAG, "Dx build dex file: $classFile to $outFile")
        }

        /*
        fun compileClassUseD8(classFile: File, outFile: File) {
            val command = D8Command.builder().apply {
                setOutput(outFile.toPath(), OutputMode.ClassFile)
                androidPlatformBuild = true
                mode = CompilationMode.RELEASE
                addClasspathFiles(classFile.toPath())
            }
            val arguments = arrayOf(
                "--release",
                "--lib", "/system/framework/framework.jar",
                "--min-api", "${com.stardust.autojs.BuildConfig.MIN_SDK_VERSION}",
                "--output", outFile.path,
                classFile.path
            )
            D8.main(arguments)
            Log.i(LOG_TAG, "D8 build dex file: $classFile to $outFile")
        }
        */
    }
}
