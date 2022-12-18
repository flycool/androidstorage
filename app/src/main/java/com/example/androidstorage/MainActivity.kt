package com.example.androidstorage

import android.Manifest
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.net.Uri
import android.os.Build
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.provider.MediaStore
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.IntentSenderRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.result.launch
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import androidx.recyclerview.widget.RecyclerView
import androidx.recyclerview.widget.StaggeredGridLayoutManager
import com.example.androidstorage.databinding.ActivityMainBinding
import com.example.androidstorage.external.savePhotoToExternalStorage
import com.example.androidstorage.external.SharedPhotoAdapter
import com.example.androidstorage.external.deletePhotoFromExternalStorage
import com.example.androidstorage.external.loadPhotosFromExternalStorage
import com.example.androidstorage.internal.InternalStoragePhotoAdapter
import com.example.androidstorage.internal.deletePhotoFromInternalStorage
import com.example.androidstorage.internal.loadPhotosFromInternalStorage
import com.example.androidstorage.internal.savePhotoToInternalStorage
import kotlinx.coroutines.launch
import java.util.UUID

class MainActivity : AppCompatActivity() {

    private lateinit var binding: ActivityMainBinding
    private lateinit var internalAdapter: InternalStoragePhotoAdapter
    private lateinit var externalAdapter: SharedPhotoAdapter

    private var readPermissionGranted = false
    private var writePermissionGranted = false
    private lateinit var permissionLauncher: ActivityResultLauncher<Array<String>>
    private lateinit var intentSenderLauncher: ActivityResultLauncher<IntentSenderRequest>

    private lateinit var contentObserver: ContentObserver
    private var deleteImageUri: Uri? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        //internal
        internalAdapter = InternalStoragePhotoAdapter {
            lifecycleScope.launch {
                val done = deletePhotoFromInternalStorage(it.name)
                if (done) {
                    loadInternalStorageRecyclerView()
                    Toast.makeText(this@MainActivity, "Photo delete successfully", Toast.LENGTH_SHORT).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed tod delete Photo", Toast.LENGTH_SHORT).show()
                }
            }
        }

        setupInternalRecyclerView()
        loadInternalStorageRecyclerView()

        //external
        externalAdapter = SharedPhotoAdapter {
            lifecycleScope.launch{
                deletePhotoFromExternalStorage(it.contentUri, intentSenderLauncher)
                deleteImageUri = it.contentUri
            }
        }

        setupExternalRecyclerView()
        loadExternalStorageRecyclerView()
        initContentObserver()

        permissionLauncher =
            registerForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { pemissions ->
                readPermissionGranted =
                    pemissions[Manifest.permission.READ_EXTERNAL_STORAGE] ?: readPermissionGranted
                writePermissionGranted =
                    pemissions[Manifest.permission.WRITE_EXTERNAL_STORAGE] ?: writePermissionGranted

                if (readPermissionGranted) {
                    loadExternalStorageRecyclerView()
                } else {
                    Toast.makeText(this, "Can't read file without permission", Toast.LENGTH_SHORT)
                        .show()
                }
            }

        intentSenderLauncher = registerForActivityResult(ActivityResultContracts.StartIntentSenderForResult()) {
            if (it.resultCode == RESULT_OK) {
                if (Build.VERSION.SDK_INT == Build.VERSION_CODES.Q) {
                    lifecycleScope.launch {
                        deletePhotoFromExternalStorage(deleteImageUri ?: return@launch, intentSenderLauncher)
                    }
                }
                Toast.makeText(this@MainActivity, "delete photo successfully", Toast.LENGTH_SHORT).show()
            } else {
                Toast.makeText(this@MainActivity, "can't delete photo", Toast.LENGTH_SHORT).show()
            }
        }

        updateOrRequestPermissions()

        val takePhoto = registerForActivityResult(ActivityResultContracts.TakePicturePreview()) {
            val isPrivate = binding.switchPrivate.isChecked
            lifecycleScope.launch {
                val isSavedSuccessfully = when {
                    isPrivate -> savePhotoToInternalStorage(UUID.randomUUID().toString(), it!!)
                    writePermissionGranted -> savePhotoToExternalStorage(
                        UUID.randomUUID().toString(), it!!
                    )
                    else -> false
                }
                if (isPrivate) {
                    loadInternalStorageRecyclerView()
                }
                if (isSavedSuccessfully) {
                    loadInternalStorageRecyclerView()
                    Toast.makeText(
                        this@MainActivity,
                        "Photo saved successfully",
                        Toast.LENGTH_SHORT
                    ).show()
                } else {
                    Toast.makeText(this@MainActivity, "Failed to save Photo", Toast.LENGTH_SHORT)
                        .show()
                }
            }
        }

        binding.btnTakePhoto.setOnClickListener {
            takePhoto.launch()
        }
    }

    //internal
    private fun setupInternalRecyclerView() = binding.rvPrivatePhotos.apply {
        adapter = internalAdapter
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private fun loadInternalStorageRecyclerView() {
        lifecycleScope.launch {
            val photos = loadPhotosFromInternalStorage()
            internalAdapter.submitList(photos)
        }
    }

    //external
    private fun setupExternalRecyclerView() = binding.rvPublicPhotos.apply {
        adapter = externalAdapter
        layoutManager = StaggeredGridLayoutManager(3, RecyclerView.VERTICAL)
    }

    private fun loadExternalStorageRecyclerView() {
        lifecycleScope.launch {
            val photos = loadPhotosFromExternalStorage()
            externalAdapter.submitList(photos)
        }
    }

    private fun initContentObserver() {
        contentObserver = object : ContentObserver(null) {
            override fun onChange(selfChange: Boolean) {
                if (readPermissionGranted) {
                    loadExternalStorageRecyclerView()
                }
            }
        }
        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            contentObserver
        )
    }

    //permission
    private fun updateOrRequestPermissions() {
        val hasReadPermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.READ_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        val hasWritePermission =
            ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) ==
                    PackageManager.PERMISSION_GRANTED
        val minSdk29 = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q

        readPermissionGranted = hasReadPermission
        writePermissionGranted = hasWritePermission || minSdk29

        val permissionToRequest = mutableListOf<String>()
        if (!readPermissionGranted) {
            permissionToRequest.add(Manifest.permission.READ_EXTERNAL_STORAGE)
        }
        if (!writePermissionGranted) {
            permissionToRequest.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
        }
        if (permissionToRequest.isNotEmpty()) {
            permissionLauncher.launch(permissionToRequest.toTypedArray())
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        contentResolver.unregisterContentObserver(contentObserver)
    }

}