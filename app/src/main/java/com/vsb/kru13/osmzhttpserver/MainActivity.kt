package com.vsb.kru13.osmzhttpserver

import android.Manifest
import android.annotation.SuppressLint
import android.graphics.Bitmap
import android.os.Bundle
import android.util.Log
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.UseCaseGroup
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import com.karumi.dexter.Dexter
import com.karumi.dexter.MultiplePermissionsReport
import com.karumi.dexter.PermissionToken
import com.karumi.dexter.listener.PermissionRequest
import com.karumi.dexter.listener.multi.MultiplePermissionsListener
import com.vsb.kru13.osmzhttpserver.requestParser.HttpRequestParser
import com.vsb.kru13.osmzhttpserver.webResourceLoader.WebResourceLoader
import kotlinx.android.synthetic.main.activity_main.*
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import java.io.ByteArrayOutputStream
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors


@SuppressLint("UnsafeExperimentalUsageError")
class MainActivity : AppCompatActivity(), VideoSource {
    private val cameraExecutor: ExecutorService by lazy { Executors.newSingleThreadExecutor() }
    private var framesCount = 0
    private val frameListeners = mutableListOf<((frame: ByteArray) -> Unit)>()

    override fun subscribeToVideo(onFrame: (frame: ByteArray) -> Unit) {
        frameListeners.add(onFrame)
    }


    private val imageAnalyzer by lazy {
        ImageAnalysis.Builder()
                .build()
                .also {
                    it.setAnalyzer(
                            cameraExecutor,
                            ImageAnalysis.Analyzer { imageProxy ->
                                val image = imageProxy.image ?: return@Analyzer
                                val imageProcessor = ProcessImageUtils.getInstance(
                                        context = this,
                                        image = image
                                )
                                val bitmap = imageProcessor.yuv420ToBitmap(image)
                                imageProxy.close()
                                framesCount++
                                bitmap?.let {
                                    setConvertedPreview(bitmap)
                                    val stream = ByteArrayOutputStream()
                                    bitmap.compress(Bitmap.CompressFormat.JPEG, 100, stream)
                                    val byteArray: ByteArray = stream.toByteArray()
                                    frameListeners.forEach {
                                        it(byteArray)
                                    }
                                }
                            }
                    )
                }
    }


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        Dexter.withActivity(this)
                .withPermissions(Manifest.permission.CAMERA, Manifest.permission.READ_EXTERNAL_STORAGE)
                .withListener(object : MultiplePermissionsListener {
                    override fun onPermissionsChecked(report: MultiplePermissionsReport?) {
                        report?.grantedPermissionResponses?.forEach {
                            println("СУКА ${it.permissionName}")
                            when (it.permissionName) {
                                Manifest.permission.CAMERA -> {
                                    startCamera()
                                }
                                Manifest.permission.READ_EXTERNAL_STORAGE -> {
                                    initViews()
                                }
                            }
                        }
                    }

                    override fun onPermissionRationaleShouldBeShown(permissions: MutableList<PermissionRequest>?, token: PermissionToken?) {
                        token?.continuePermissionRequest();
                    }

                }).check()
    }

    private fun initViews() {
        val webResourceLoader = WebResourceLoader(
                videoSource = this
        )

        val server = SocketServer(
                requestParser = HttpRequestParser(
                        webResourceLoader = webResourceLoader
                )
        )

        start_button.setOnClickListener {
            webResourceLoader.rootDirectoryPath = path.text.toString()
            server.startServer()

        }

        server.stateListener = object : SocketServer.OnStateChangedListener {
            override fun onStarted() {
                CoroutineScope(Dispatchers.Main).launch {
                    start_button.setOnClickListener {
                        webResourceLoader.rootDirectoryPath = path.text.toString()
                        server.close()
                    }
                    start_button.setText(R.string.disconnect)
                    connections_count.text = "0"
                }
            }

            override fun onStopped() {
                CoroutineScope(Dispatchers.Main).launch {
                    start_button.setOnClickListener {
                        server.startServer()
                    }
                    start_button.setText(R.string.connect)
                    connections_count.text = "0"
                }
            }

            override fun onConnectionCountChanged(connectionsCount: Int) {
                CoroutineScope(Dispatchers.Main).launch {
                    connections_count.text = connectionsCount.toString()
                }
            }

        }
    }

    fun setConvertedPreview(bitmap: Bitmap) {
        CoroutineScope(Dispatchers.Main).launch {
            convert_preview.setImageBitmap(bitmap)
        }
    }

    @SuppressLint("UnsafeExperimentalUsageError")
    private fun startCamera() {
        CoroutineScope(Dispatchers.Main).launch {
            while (true) {
                delay(1000)
                fps_status.setText(getString(R.string.fps_status, framesCount))
                framesCount = 0
            }
        }
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener(
                Runnable {
                    // Used to bind the lifecycle of cameras to the lifecycle owner
                    val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
                    val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                    try {
                        // Unbind use cases before rebinding
                        cameraProvider.unbindAll()
                        cameraProvider.bindToLifecycle(
                                this as LifecycleOwner,
                                cameraSelector,
                                UseCaseGroup.Builder()
                                        .addUseCase(imageAnalyzer)
                                        .build()
                        )
                    } catch (t: Throwable) {
                        Log.e("CAMERA", "Use case binding failed", t)
                    }

                },
                ContextCompat.getMainExecutor(this)
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        cameraExecutor.shutdown()
    }
}

interface VideoSource {
    fun subscribeToVideo(onFrame: (frame: ByteArray) -> Unit)
}
