package com.example.data.remote

import okhttp3.OkHttpClient
import org.schabi.newpipe.extractor.downloader.Downloader
import org.schabi.newpipe.extractor.downloader.Request
import org.schabi.newpipe.extractor.downloader.Response
import java.util.concurrent.TimeUnit

class NPDownloader private constructor(private val client: OkHttpClient) : Downloader() {

    override fun execute(request: Request): Response {
        val httpMethod = request.httpMethod()
        val url = request.url()
        val headers = request.headers()
        val dataToSend = request.dataToSend()

        val builder = okhttp3.Request.Builder().url(url)

        headers.forEach { (headerName, headerValueList) ->
            if (headerValueList.isNotEmpty()) {
                builder.header(headerName, headerValueList[0])
            }
        }

        if (savedCookies.isNotBlank() && (url.contains("youtube.com") || url.contains("googlevideo.com"))) {
            builder.header("Cookie", savedCookies)
        }

        val requestBody = dataToSend?.let {
            okhttp3.RequestBody.create(null, it)
        }

        builder.method(httpMethod, requestBody)

        val response = client.newCall(builder.build()).execute()
        val responseCode = response.code
        val responseMessage = response.message
        val responseHeaders = mutableMapOf<String, List<String>>()

        response.headers.names().forEach { name ->
            responseHeaders[name] = response.headers(name)
        }

        val responseBody = response.body?.string() ?: ""

        return Response(responseCode, responseMessage, responseHeaders, responseBody, url)
    }

    companion object {
        @Volatile
        private var instance: NPDownloader? = null
        var savedCookies: String = ""

        fun getInstance(): NPDownloader {
            return instance ?: synchronized(this) {
                instance ?: NPDownloader(
                    OkHttpClient.Builder()
                        .connectTimeout(10, TimeUnit.SECONDS)
                        .readTimeout(15, TimeUnit.SECONDS)
                        .followRedirects(true)
                        .followSslRedirects(true)
                        .build()
                ).also { instance = it }
            }
        }
    }
}
