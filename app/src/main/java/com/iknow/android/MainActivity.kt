package com.iknow.android

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.text.TextUtils
import android.view.View
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.iknow.android.features.trim.VideoTrimmerActivity
import com.iknow.android.utils.FileUtils

class MainActivity : AppCompatActivity() {
    // Declare the ActivityResultLauncher
    private var pickMultipleVideosLauncher: ActivityResultLauncher<Intent>? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        // Initialize the ActivityResultLauncher for picking multiple videos
        pickMultipleVideosLauncher = registerForActivityResult<Intent, ActivityResult>(
            ActivityResultContracts.StartActivityForResult()
        ) { result: ActivityResult ->
            if (result.resultCode == RESULT_OK) {
                val data = result.data
                if (data != null && data.clipData != null) {
                    val videoPaths: MutableList<String> = ArrayList()
                    val itemCount = data.clipData!!.itemCount
                    for (i in 0 until itemCount) {
                        val uri = data.clipData!!.getItemAt(i).uri
                        val videoPath = FileUtils.getPath(this@MainActivity, uri)
                        if (!TextUtils.isEmpty(videoPath)) {
                            videoPaths.add(videoPath)
                        }
                    }
                    if (videoPaths.isNotEmpty()) {
                        VideoTrimmerActivity.call(this@MainActivity, videoPaths)
                    } else {
                        Toast.makeText(this@MainActivity, "No valid video selected", Toast.LENGTH_SHORT).show()
                    }
                }
            } else {
                Toast.makeText(this@MainActivity, "Video selection cancelled", Toast.LENGTH_SHORT).show()
            }
        }

        val pickVideoButton = findViewById<Button>(R.id.btnPickVideo)
        pickVideoButton.setOnClickListener { v: View? -> pickVideosFromGallery() }
    }

    private fun pickVideosFromGallery() {
        val requiredPermission = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_VIDEO
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }

        if (ContextCompat.checkSelfPermission(this, requiredPermission) != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(
                this, arrayOf(requiredPermission),
                REQUEST_PERMISSION_MEDIA
            )
        } else {
            val intent = Intent(Intent.ACTION_OPEN_DOCUMENT)
            intent.type = "video/*"
            intent.putExtra(Intent.EXTRA_ALLOW_MULTIPLE, true)
            pickMultipleVideosLauncher!!.launch(intent)
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_PERMISSION_MEDIA) {
            if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                pickVideosFromGallery()
            } else {
                Toast.makeText(this, "Permission denied", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val REQUEST_PERMISSION_MEDIA = 1
    }
}
