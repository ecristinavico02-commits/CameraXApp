package com.example.cameraxapp

import android.content.Intent
import android.os.Bundle
import androidx.appcompat.app.AppCompatActivity
import android.widget.Button
import android.util.Log
import android.widget.Toast
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.GlobalScope
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

class MainActivity : AppCompatActivity() {

    private lateinit var recyclerView: RecyclerView
    private lateinit var recordingsAdapter: RecordingsAdapter
    private val recordingFilesList = mutableListOf<RecordingFile>()

    companion object {
        private const val TAG = "MainActivityRecordings"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContentView(R.layout.activity_main)

        recyclerView = findViewById(R.id.recyclerViewRecordings)
        recyclerView.layoutManager = LinearLayoutManager(this)

        recordingsAdapter = RecordingsAdapter(recordingFilesList) { recordingFile ->
            Toast.makeText(this, "Clicked: ${recordingFile.displayName}", Toast.LENGTH_SHORT).show()
        }

        recyclerView.adapter = recordingsAdapter

        loadRecordings()

        // Botón para abrir la pantalla de la cámara
        val btnCamera: Button = findViewById(R.id.button2) //si se pulsa el boton nos lleva a la segunda pantalla
        btnCamera.setOnClickListener {
            val intent: Intent = Intent(this, pantalla_camara::class.java) //para cambiar a la segunda pantalla
            startActivity(intent)

        }

        //Botón para abrir la pantalla de configuración de color
        val btnColor: Button = findViewById(R.id.button3) //si se pulsa el boton nos lleva a la pantalla de configuracion de color
        btnColor.setOnClickListener {
            val intent: Intent = Intent(
                this,
                ConfColor::class.java
            ) //para cambiar a la pantalla de configuracion de color
            startActivity(intent)
        }
    }

    override fun onResume() {
        super.onResume()
        // Carga nuevos archivos
        loadRecordings()
    }

    private fun loadRecordings() {
        GlobalScope.launch(Dispatchers.IO) {
            val filesDir = applicationContext.filesDir
            val allFiles = filesDir.listFiles()

            val csvFiles = allFiles?.filter {
                it.isFile && it.name.startsWith("recording_") && it.name.endsWith(".csv")
            }?.sortedByDescending { it.lastModified() }

            val newRecordingFilesList = mutableListOf<RecordingFile>()
            csvFiles?.forEach { file ->
                val (displayName, displayTimestamp) = parseFileName(file.name)
                newRecordingFilesList.add(RecordingFile(file, displayName, displayTimestamp))
            }

            withContext(Dispatchers.Main) {
                recordingFilesList.clear()
                recordingFilesList.addAll(newRecordingFilesList)
                recordingsAdapter.updateData(recordingFilesList)
                if (newRecordingFilesList.isEmpty()) {
                    Toast.makeText(applicationContext, "No recordings found.", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    private fun parseFileName(fileName: String): Pair<String, String> {
        val pattern = "recording_(\\d{4}-\\d{2}-\\d{2}_\\d{2}-\\d{2}-\\d{2}).csv"
        val regex = Regex(pattern)
        val matchResult = regex.find(fileName)

        if (matchResult != null) {
            val dateTimeString = matchResult.groupValues[1]
            val inputFormat = SimpleDateFormat("yyyy-MM-dd_HH-mm-ss", Locale.getDefault())
            val outputFormat = SimpleDateFormat("MMM dd, yyyy 'at' HH:mm:ss", Locale.getDefault())
            try {
                val date = inputFormat.parse(dateTimeString)
                if (date != null) {
                    return Pair(fileName, "Created: ${outputFormat.format(date)}")
                }
            } catch (e: ParseException) {
                Log.e(TAG, "Error parsing date from filename: $fileName", e)
            }
        }
        return Pair(fileName, "Timestamp unavailable")
    }
}


