package com.aufait.alpha

import android.Manifest
import android.database.Cursor
import android.net.Uri
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.provider.OpenableColumns
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.compose.setContent
import androidx.activity.viewModels
import androidx.core.content.ContextCompat
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewmodel.CreationExtras
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions
import com.aufait.alpha.data.AlphaChatContainer
import com.aufait.alpha.ui.AlphaApp

class MainActivity : ComponentActivity() {
    private val notificationPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op for alpha */ }
    private val cameraPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) launchQrScan()
    }
    private val bluetoothConnectPermissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { /* no-op for alpha */ }
    private val qrScanLauncher = registerForActivityResult(ScanContract()) { result ->
        val content = result.contents ?: return@registerForActivityResult
        viewModel.importContactFromQr(content)
    }
    private val attachmentPickerLauncher = registerForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        uri ?: return@registerForActivityResult
        handleAttachmentPicked(uri)
    }

    private val viewModel by viewModels<ChatViewModel> {
        object : ViewModelProvider.Factory {
            override fun <T : ViewModel> create(modelClass: Class<T>, extras: CreationExtras): T {
                @Suppress("UNCHECKED_CAST")
                return ChatViewModel(AlphaChatContainer(applicationContext)) as T
            }
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestNotificationPermissionIfNeeded()
        requestBluetoothPermissionIfNeeded()
        setContent {
            AlphaApp(
                viewModel = viewModel,
                onScanContactQrRequest = ::requestContactQrScan,
                onPickAttachmentRequest = ::openAttachmentPicker
            )
        }
    }

    override fun onStart() {
        super.onStart()
        viewModel.onConversationForegroundChanged(true)
    }

    override fun onStop() {
        viewModel.onConversationForegroundChanged(false)
        super.onStop()
    }

    private fun requestNotificationPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.POST_NOTIFICATIONS
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
    }

    private fun requestContactQrScan() {
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            launchQrScan()
        } else {
            cameraPermissionLauncher.launch(Manifest.permission.CAMERA)
        }
    }

    private fun requestBluetoothPermissionIfNeeded() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.S) return
        val granted = ContextCompat.checkSelfPermission(
            this,
            Manifest.permission.BLUETOOTH_CONNECT
        ) == PackageManager.PERMISSION_GRANTED
        if (!granted) {
            bluetoothConnectPermissionLauncher.launch(Manifest.permission.BLUETOOTH_CONNECT)
        }
    }

    private fun launchQrScan() {
        val options = ScanOptions()
            .setDesiredBarcodeFormats(ScanOptions.QR_CODE)
            .setPrompt("Scanner un QR de contact Aufait")
            .setBeepEnabled(true)
            .setOrientationLocked(false)
        qrScanLauncher.launch(options)
    }

    private fun openAttachmentPicker() {
        attachmentPickerLauncher.launch(arrayOf("*/*"))
    }

    private fun handleAttachmentPicked(uri: Uri) {
        runCatching {
            contentResolver.takePersistableUriPermission(
                uri,
                android.content.Intent.FLAG_GRANT_READ_URI_PERMISSION
            )
        }

        val meta = queryAttachmentMeta(uri)
        viewModel.onAttachmentPicked(
            uriString = uri.toString(),
            displayName = meta.displayName ?: "fichier",
            mimeType = contentResolver.getType(uri),
            sizeBytes = meta.sizeBytes
        )
    }

    private fun queryAttachmentMeta(uri: Uri): AttachmentMeta {
        var name: String? = null
        var size: Long? = null
        val cursor: Cursor? = contentResolver.query(uri, null, null, null, null)
        cursor?.use {
            val nameIndex = it.getColumnIndex(OpenableColumns.DISPLAY_NAME)
            val sizeIndex = it.getColumnIndex(OpenableColumns.SIZE)
            if (it.moveToFirst()) {
                if (nameIndex >= 0) name = it.getString(nameIndex)
                if (sizeIndex >= 0 && !it.isNull(sizeIndex)) size = it.getLong(sizeIndex)
            }
        }
        return AttachmentMeta(name, size)
    }

    private data class AttachmentMeta(
        val displayName: String?,
        val sizeBytes: Long?
    )
}
