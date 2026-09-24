package com.wingspan.app.ui.snapshots

import android.content.Context
import android.content.Intent
import androidx.core.content.FileProvider
import com.wingspan.app.data.FiringSnapshot

object SnapshotShare {
    fun share(context: Context, snapshot: FiringSnapshot, summary: String) {
        val uri = FileProvider.getUriForFile(
            context,
            context.packageName + ".fileprovider",
            snapshot.imageFile,
        )
        val intent = Intent(Intent.ACTION_SEND).apply {
            type = "image/png"
            putExtra(Intent.EXTRA_STREAM, uri)
            putExtra(Intent.EXTRA_TEXT, summary)
            putExtra(Intent.EXTRA_SUBJECT, "Wingspan firing position")
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(intent, "Share snapshot"))
    }
}
