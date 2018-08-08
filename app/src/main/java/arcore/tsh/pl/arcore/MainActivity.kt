package arcore.tsh.pl.arcore

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.PixelFormat
import android.opengl.GLES20
import android.opengl.GLSurfaceView
import android.os.Bundle
import android.util.Log
import android.view.View
import android.widget.Toast
import arcore.tsh.pl.arcore.helpers.CameraPermissionHelper
import arcore.tsh.pl.arcore.helpers.FullScreenHelper
import arcore.tsh.pl.arcore.helpers.SnackbarHelper
import arcore.tsh.pl.arcore.renderers.VideoRenderer
import com.google.ar.core.*
import com.google.ar.core.exceptions.*
import kotlinx.android.synthetic.main.activity_main.*
import java.io.IOException
import javax.microedition.khronos.egl.EGLConfig
import javax.microedition.khronos.opengles.GL10


class MainActivity : BaseActivity(), GLSurfaceView.Renderer {

    private val TAG = MainActivity::class.java.simpleName

    private var installRequested: Boolean = false

    private lateinit var session: Session

    private var shouldConfigureSession = false

    private val messageSnackbarHelper = SnackbarHelper()

    private val videoRenderer = VideoRenderer()
    private var movieAnchor: Anchor? = null
    private val anchorMatrix = FloatArray(16)

    private lateinit var frame: Frame
    private lateinit var camera: Camera

    private val projmtx = FloatArray(16)
    private val viewmtx = FloatArray(16)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        surfaceviewGl.setZOrderOnTop(true)
        surfaceviewGl.preserveEGLContextOnPause = true
        surfaceviewGl.setEGLContextClientVersion(2)
        surfaceviewGl.holder.setFormat(PixelFormat.RGBA_8888);
        surfaceviewGl.setEGLConfigChooser(8, 8, 8, 8, 16, 0)
        surfaceviewGl.setRenderer(this)
        surfaceviewGl.renderMode = GLSurfaceView.RENDERMODE_CONTINUOUSLY

        installRequested = false

        initializeSceneView()
    }

    override fun onDrawFrame(gl: GL10?) {
        GLES20.glClear(GLES20.GL_COLOR_BUFFER_BIT or GLES20.GL_DEPTH_BUFFER_BIT)

        camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f)
        camera.getViewMatrix(viewmtx, 0)

        if (movieAnchor != null) {
            if (!videoRenderer.isStarted) {
                videoRenderer.play("planet.mp4", this)
            }
            movieAnchor!!.pose.toMatrix(anchorMatrix, 0)
            videoRenderer.update(anchorMatrix)
            videoRenderer.draw(viewmtx, projmtx)

        }

    }

    override fun onSurfaceChanged(gl: GL10?, width: Int, height: Int) {
        GLES20.glViewport(0, 0, width, height)
    }

    override fun onSurfaceCreated(gl: GL10?, config: EGLConfig?) {
        videoRenderer.createOnGlThread()
    }

    override fun onResume() {
        super.onResume()

        var exception: Exception? = null
        var message: String? = null
        try {
            when (ArCoreApk.getInstance().requestInstall(this, !installRequested)) {
                ArCoreApk.InstallStatus.INSTALL_REQUESTED -> {
                    installRequested = true
                    return
                }
                ArCoreApk.InstallStatus.INSTALLED -> {
                }
            }


            if (!CameraPermissionHelper.hasCameraPermission(this)) {
                CameraPermissionHelper.requestCameraPermission(this)
                return
            }

            session = Session(this)
        } catch (e: UnavailableArcoreNotInstalledException) {
            message = "Please install ARCore"
            exception = e
        } catch (e: UnavailableUserDeclinedInstallationException) {
            message = "Please install ARCore"
            exception = e
        } catch (e: UnavailableApkTooOldException) {
            message = "Please update ARCore"
            exception = e
        } catch (e: UnavailableSdkTooOldException) {
            message = "Please update this app"
            exception = e
        } catch (e: Exception) {
            message = "This device does not support AR"
            exception = e
        }

        if (message != null) {
            messageSnackbarHelper.showError(this, message)
            Log.e(TAG, "Exception creating session", exception)
            return
        }

        shouldConfigureSession = true


        if (shouldConfigureSession) {
            configureSession()
            shouldConfigureSession = false
            surfaceview.setupSession(session)
        }

        try {
            session.resume()
            surfaceview.resume()
        } catch (e: CameraNotAvailableException) {
            messageSnackbarHelper.showError(this, "Camera not available. Please restart the app.")
            return
        }

    }

    public override fun onPause() {
        super.onPause()
        surfaceview.pause()
        session.pause()

    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<String>, results: IntArray) {
        if (!CameraPermissionHelper.hasCameraPermission(this)) {
            Toast.makeText(
                    this, "Camera permissions are needed to run this application", Toast.LENGTH_LONG)
                    .show()
            if (!CameraPermissionHelper.shouldShowRequestPermissionRationale(this)) {
                CameraPermissionHelper.launchPermissionSettings(this)
            }
            finish()
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        FullScreenHelper.setFullScreenOnWindowFocusChanged(this, hasFocus)
    }

    private fun initializeSceneView() {
        surfaceview.scene.setOnUpdateListener { this.onUpdateFrame() }
    }

    private fun onUpdateFrame() {
        frame = surfaceview.arFrame
        camera = frame.camera
        val updatedAugmentedImages = frame.getUpdatedTrackables(AugmentedImage::class.java)

        camera.getProjectionMatrix(projmtx, 0, 0.1f, 100.0f)
        camera.getViewMatrix(viewmtx, 0)

        for (augmentedImage in updatedAugmentedImages) {
            if (augmentedImage.trackingState == TrackingState.TRACKING) {
                imageViewFit!!.visibility = View.GONE
                if (augmentedImage.name == "earth") {
                    val node = AugmentedImagesNode(this, "earth.sfb", "earth")
                    node.setImage(augmentedImage)
                    surfaceview.scene.addChild(node)

                    node.setOnTapListener { _, _ ->
                        surfaceviewGl.visibility = View.VISIBLE

                        val translation = FloatArray(3)
                        val rotation = FloatArray(4)
                        augmentedImage.centerPose.getTranslation(translation, 0)
                        frame.androidSensorPose.getRotationQuaternion(rotation, 0)

                        val rotatedPose = Pose(translation, rotation)
                        rotatedPose.toMatrix(anchorMatrix, 0)

                        movieAnchor = augmentedImage.createAnchor(rotatedPose)

                    }

                }

            }
        }
    }

    override fun onBackPressed() {
        if (surfaceviewGl.isShown)
            surfaceviewGl.visibility = View.INVISIBLE
        else
            super.onBackPressed()

    }

    private fun configureSession() {
        val config = Config(session)
        if (setupAugmentedImageDb(config).not()) {
            messageSnackbarHelper.showError(this, "Could not setup augmented image database")
        }
        config.updateMode = Config.UpdateMode.LATEST_CAMERA_IMAGE
        session.configure(config)
    }

    private fun setupAugmentedImageDb(config: Config): Boolean {
        val augmentedImageDatabase = AugmentedImageDatabase(session)
        val augmentedEarthImageBitmap = loadAugmentedEarthImage() ?: return false

        augmentedImageDatabase.addImage("earth", augmentedEarthImageBitmap)

        config.augmentedImageDatabase = augmentedImageDatabase
        return true
    }

    private fun loadAugmentedEarthImage(): Bitmap? {
        try {
            assets.open("earth.jpg").use { `is` -> return BitmapFactory.decodeStream(`is`) }
        } catch (e: IOException) {
            Log.e(TAG, "IO exception loading augmented image bitmap.", e)
        }

        return null
    }
}
