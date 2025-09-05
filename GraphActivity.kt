
package com.example.cameraxapp

import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.ImageView
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
//import androidx.compose.animation.with
//import androidx.compose.ui.semantics.error
//import androidx.compose.ui.semantics.text
//import androidx.glance.visibility
import androidx.lifecycle.lifecycleScope
//import androidx.wear.compose.material.placeholder
import com.bumptech.glide.Glide
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.MultipartBody
import okhttp3.RequestBody.Companion.asRequestBody

import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Response
import retrofit2.Retrofit
import retrofit2.converter.gson.GsonConverterFactory
import retrofit2.http.Multipart
import retrofit2.http.POST
import retrofit2.http.Part
import java.io.File
import java.util.concurrent.TimeUnit


data class ProcessedCombinedGraphResponse(
    val message: String?,
    val combined_graph_image_url: String?,
    val error: String?
)

// Retrofit API
interface ApiService {
    @Multipart
    @POST("process_data_and_generate_combined_graph")
    suspend fun uploadCsvAndGetCombinedGraphImage(
        @Part file: MultipartBody.Part
    ): Response<ProcessedCombinedGraphResponse>
}

// Retrofit Client
object RetrofitClient {
    const val BASE_URL = "http://192.168.1.131:5000/" // URL DEL SERVIDOR

    private val loggingInterceptor = HttpLoggingInterceptor().apply {
        level = HttpLoggingInterceptor.Level.BODY
    }
    private val okHttpClient = OkHttpClient.Builder()
        .addInterceptor(loggingInterceptor)
        .connectTimeout(30, TimeUnit.SECONDS)
        .readTimeout(30, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .build()

    val instance: ApiService by lazy {
        val retrofit = Retrofit.Builder()
            .baseUrl(BASE_URL)
            .client(okHttpClient)
            .addConverterFactory(GsonConverterFactory.create())
            .build()
        retrofit.create(ApiService::class.java)
    }
}


class GraphActivity : AppCompatActivity() {

    private lateinit var imageViewCombinedGraph: ImageView
    private lateinit var textViewStatus: TextView
    private lateinit var progressBar: ProgressBar

    companion object {
        private const val TAG = "GraphActivity"
        const val EXTRA_FILE_PATH = "com.example.cameraxapp.EXTRA_FILE_PATH"
//        const val EXTRA_CSV_FILENAME = "com.example.cameraxapp.EXTRA_CSV_FILENAME"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_graph)


        imageViewCombinedGraph = findViewById(R.id.imageViewCombinedGraph)
        textViewStatus = findViewById(R.id.textViewGraphStatus)
        progressBar = findViewById(R.id.progressBarGraphImage)

        val filePath = intent.getStringExtra(EXTRA_FILE_PATH)

        if (filePath != null) {
            val csvFile = File(filePath)
            if (csvFile.exists()) {
                textViewStatus.text = "Processing data and generating graph..."
                processAndLoadCombinedGraph(csvFile)
            } else {
                handleError("Error: Data file not found.")
            }
        } else {
            handleError("Error: No data file specified.")
        }
    }

    private fun processAndLoadCombinedGraph(csvFile: File) {
        progressBar.visibility = View.VISIBLE
        textViewStatus.visibility = View.VISIBLE
        imageViewCombinedGraph.visibility = View.GONE

        lifecycleScope.launch(Dispatchers.IO) {
            try {
                val requestFile = csvFile.asRequestBody("text/csv".toMediaTypeOrNull())
                val body = MultipartBody.Part.createFormData("file", csvFile.name, requestFile)

                Log.d(TAG, "Uploading file: ${csvFile.name}")

                val response = RetrofitClient.instance.uploadCsvAndGetCombinedGraphImage(body)

                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    if (response.isSuccessful && response.body() != null) {
                        val graphResponse = response.body()!!
                        if (graphResponse.error == null) {
                            Log.i(TAG, "Server message: ${graphResponse.message}")
                            loadGraphImage(graphResponse.combined_graph_image_url, imageViewCombinedGraph, "Combined Graph")
                            if (graphResponse.combined_graph_image_url != null) {
                                textViewStatus.visibility = View.GONE
                            } else {
                                textViewStatus.text = "No graph image received from server."
                            }
                        } else {
                            handleError("Server error: ${graphResponse.error}")
                        }
                    } else {
                        val errorBody = response.errorBody()?.string() ?: "Unknown error"
                        handleError("Server request failed: ${response.code()} - $errorBody")
                    }
                }
            } catch (e: Exception) {
                Log.e(TAG, "Error communicating with server: ${e.message}", e)
                withContext(Dispatchers.Main) {
                    progressBar.visibility = View.GONE
                    handleError("Network error: ${e.localizedMessage}")
                }
            }
        }
    }

    private fun loadGraphImage(imageUrl: String?, imageView: ImageView, graphName: String) {
        if (imageUrl.isNullOrEmpty()) {
            Log.w(TAG, "$graphName URL is null or empty.")
            Toast.makeText(this, "$graphName not available.", Toast.LENGTH_SHORT).show()
            imageView.setImageDrawable(null)
            imageView.visibility = View.GONE
            return
        }

        imageView.visibility = View.VISIBLE
        Log.d(TAG, "Loading $graphName from URL: $imageUrl")

        var fullImageUrl = imageUrl
        if (!imageUrl.startsWith("http://") && !imageUrl.startsWith("https://")) {

            val baseUrl = RetrofitClient.BASE_URL
            fullImageUrl = if (baseUrl.endsWith('/') && imageUrl.startsWith('/')) {
                baseUrl + imageUrl.substring(1)
            } else if (!baseUrl.endsWith('/') && !imageUrl.startsWith('/')) {
                "$baseUrl/$imageUrl"
            } else {
                baseUrl + imageUrl
            }
        }
        Log.d(TAG, "Constructed Full Image URL: $fullImageUrl")


        Glide.with(this)
            .load(fullImageUrl)
//            .placeholder(R.drawable.ic_image_placeholder)
//            .error(R.drawable.ic_image_error)
            .into(imageView)
    }

    private fun handleError(errorMessage: String) {
        Log.e(TAG, errorMessage)
        textViewStatus.text = errorMessage
        textViewStatus.visibility = View.VISIBLE
        progressBar.visibility = View.GONE
        imageViewCombinedGraph.visibility = View.GONE
        Toast.makeText(this, errorMessage, Toast.LENGTH_LONG).show()
    }
}




