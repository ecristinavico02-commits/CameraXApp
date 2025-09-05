package com.example.cameraxapp

import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.TextView
import androidx.recyclerview.widget.RecyclerView
import java.io.File
import java.text.ParseException
import java.text.SimpleDateFormat
import java.util.Locale

data class RecordingFile(
    val file: File,
    val displayName: String,
    val displayTimestamp: String
)

class RecordingsAdapter(
    private var recordingFiles: List<RecordingFile>,
    private val onItemClick: (RecordingFile) -> Unit
) : RecyclerView.Adapter<RecordingsAdapter.RecordingViewHolder>() {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): RecordingViewHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.list_item_recording, parent, false)
        return RecordingViewHolder(view)
    }

    override fun onBindViewHolder(holder: RecordingViewHolder, position: Int) {
        val recordingFile = recordingFiles[position]
        holder.bind(recordingFile)
        holder.itemView.setOnClickListener {
            val context = holder.itemView.context
            val intent = Intent(context, GraphActivity::class.java).apply {
                putExtra(GraphActivity.EXTRA_FILE_PATH, recordingFile.file.absolutePath)
            }
            context.startActivity(intent)
        }
    }

    override fun getItemCount(): Int = recordingFiles.size

    fun updateData(newRecordingFiles: List<RecordingFile>) {
        this.recordingFiles = newRecordingFiles
        notifyDataSetChanged()
    }

    inner class RecordingViewHolder(itemView: View) : RecyclerView.ViewHolder(itemView) {
        private val nameTextView: TextView = itemView.findViewById(R.id.textViewRecordingName)
        private val timestampTextView: TextView = itemView.findViewById(R.id.textViewRecordingTimestamp)

        fun bind(recordingFile: RecordingFile) {
            nameTextView.text = recordingFile.displayName
            timestampTextView.text = recordingFile.displayTimestamp
            itemView.setOnClickListener {
                onItemClick(recordingFile)
            }
        }
    }
}
