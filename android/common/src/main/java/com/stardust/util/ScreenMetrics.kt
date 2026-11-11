package com.stardust.util

import android.content.Context
import android.content.res.Configuration
import android.util.DisplayMetrics
import android.view.WindowManager

/**
 * Created by Stardust on 2017/4/26.
 */
class ScreenMetrics {
    private var mDesignWidth = 0
    private var mDesignHeight = 0

    constructor(designWidth: Int, designHeight: Int) {
        mDesignWidth = designWidth
        mDesignHeight = designHeight
    }

    constructor()

    fun setDesignWidth(designWidth: Int) {
        mDesignWidth = designWidth
    }

    @JvmOverloads
    fun scaleX(x: Int, width: Int = 0): Int = Companion.scaleX(x, width)

    @JvmOverloads
    fun scaleY(y: Int, height: Int = 0): Int = Companion.scaleY(y, height)


    fun setDesignHeight(designHeight: Int) {
        mDesignHeight = designHeight
    }

    fun setScreenMetrics(width: Int, height: Int) {
        mDesignWidth = width
        mDesignHeight = height
    }

    companion object {
        @JvmStatic
        var deviceScreenHeight: Int = 0
            private set

        @JvmStatic
        var deviceScreenWidth: Int = 0
            private set
        private var initialized = false
        private var deviceScreenDensity: Int = 0

        fun getDeviceScreenDensity() = deviceScreenDensity

        fun initIfNeeded(context: Context) {
            if (initialized && deviceScreenHeight != 0) return
            val metrics = DisplayMetrics()
            val windowManager = context.getSystemService(Context.WINDOW_SERVICE) as WindowManager
            windowManager.getDefaultDisplay().getRealMetrics(metrics)
            val display = windowManager.getDefaultDisplay()
            val metrics1 = context.resources.displayMetrics
            deviceScreenHeight = metrics.heightPixels
            if (deviceScreenHeight == 0) {
                deviceScreenHeight = display.getHeight()
            }
            if (deviceScreenHeight == 0) {
                deviceScreenHeight = metrics1.heightPixels
            }
            deviceScreenWidth = metrics.widthPixels
            if (deviceScreenWidth == 0) {
                deviceScreenWidth = display.getWidth()
            }
            if (deviceScreenWidth == 0) {
                deviceScreenWidth = metrics1.widthPixels
            }
            deviceScreenDensity = metrics.densityDpi

            initialized = true
        }

        fun getOrientationAwareScreenWidth(orientation: Int): Int {
            return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                deviceScreenHeight
            } else {
                deviceScreenWidth
            }
        }

        fun getOrientationAwareScreenHeight(orientation: Int): Int {
            return if (orientation == Configuration.ORIENTATION_LANDSCAPE) {
                deviceScreenWidth
            } else {
                deviceScreenHeight
            }
        }

        @JvmOverloads
        fun scaleX(x: Int, width: Int = 0): Int {
            if (width == 0 || !initialized) return x
            return x * deviceScreenWidth / width
        }

        fun scaleY(y: Int, height: Int = 0): Int {
            if (height == 0 || !initialized) return y
            return y * deviceScreenHeight / height
        }


        @JvmOverloads
        fun rescaleX(x: Int, width: Int = 0): Int {
            if (width == 0 || !initialized) return x
            return x * width / deviceScreenWidth
        }


        @JvmOverloads
        fun rescaleY(y: Int, height: Int = 0): Int {
            if (height == 0 || !initialized) return y
            return y * height / deviceScreenHeight
        }
    }
}
