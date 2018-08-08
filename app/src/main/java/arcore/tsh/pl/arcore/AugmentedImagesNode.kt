package arcore.tsh.pl.arcore

import android.content.Context
import android.net.Uri
import android.util.Log
import com.google.ar.core.AugmentedImage
import com.google.ar.core.Pose
import com.google.ar.sceneform.AnchorNode
import com.google.ar.sceneform.Node
import com.google.ar.sceneform.math.Vector3
import com.google.ar.sceneform.rendering.ModelRenderable
import java.util.concurrent.CompletableFuture


class AugmentedImagesNode(context: Context, filename: String, name: String) : AnchorNode() {

    private var modelFuture: CompletableFuture<ModelRenderable>? = null

    init {
        if (modelFuture == null) {
            modelFuture = ModelRenderable.builder().setRegistryId(name)
                    .setSource(context, Uri.parse(filename))
                    .build()
        }
    }

    fun setImage(image: AugmentedImage) {
        modelFuture?.let {
            if (it.isDone.not()) {
                CompletableFuture.allOf(it).thenAccept { aVoid: Void ->
                    setImage(image)

                }.exceptionally { throwable ->
                    Log.e("Error", "Exception loading", throwable)
                    null
                }
            }

            anchor = image.createAnchor(image.centerPose)

            val node = Node()

            val pose = Pose.makeTranslation(0.0f, 0.0f, 0.0f)

            node.setParent(this)
            node.name = name
            node.localPosition = Vector3(pose.tx(), pose.ty(), pose.tz())
            node.renderable = it.getNow(null)
        }
    }
}