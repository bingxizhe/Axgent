package com.stardust.autojs.core.http

import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.OkHttpClient.Builder
import okhttp3.Response
import java.net.SocketTimeoutException
import java.util.concurrent.TimeUnit

/**
 * Created by Stardust on 2018/4/11.
 */
open class MutableOkHttp() {
    var timeout: Long = 30 * 1000
        set(timeout) {
            field = timeout
            muteClient()
        }
    var maxRetries: Int = 3
    private val mRetryInterceptor = Interceptor { chain: Interceptor.Chain ->
        val request = chain.request()
        var response: Response? = null
        for (i in 1..maxRetries) {
            try {
                response?.close()
                response = chain.proceed(request)
                if (response.isSuccessful)
                    return@Interceptor response
            } catch (e: SocketTimeoutException) {
                if (i >= this.maxRetries) {
                    throw e
                }
            }
        }
        return@Interceptor response!!
    }
    private var mOkHttpClient: OkHttpClient = newClient(
        Builder().addInterceptor(mRetryInterceptor)
    )

    fun client(): OkHttpClient {
        return mOkHttpClient
    }

    protected fun newClient(builder: Builder): OkHttpClient {
        builder.readTimeout(this.timeout, TimeUnit.MILLISECONDS)
            .writeTimeout(this.timeout, TimeUnit.MILLISECONDS)
            .connectTimeout(this.timeout, TimeUnit.MILLISECONDS)
        return builder.build()
    }


    @Synchronized
    protected fun muteClient() {
        mOkHttpClient = newClient(mOkHttpClient.newBuilder())
    }

    fun destroy() {
        mOkHttpClient.dispatcher.cancelAll()
    }
}
