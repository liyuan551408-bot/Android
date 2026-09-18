package massey.android.myapplication

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.enableEdgeToEdge
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import massey.android.myapplication.ui.theme.MyApplicationTheme
class PhotoActivity : ComponentActivity() {
    override fun onCreate(
        savedInstanceState: Bundle?
    ) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val photoId = intent.getStringExtra("photoId")
        val orientation = intent.getIntExtra("photoOrientation", 0)
        val width = intent.getIntExtra("photoWidth", 0)
        val height = intent.getIntExtra("photoHeight", 0)
        setContent {
            MyApplicationTheme {
                PhotoViewer(
                    photoId = photoId,
                    orientation = orientation,
                    width = width,
                    height = height,
                    contentResolver =
                        contentResolver
                )
            }
        }
    }
}
@Composable
fun PhotoViewer(
    photoId: String?,
    orientation: Int,
    width: Int,
    height: Int,
    contentResolver: ContentResolver
) {
    var bitmap by remember(photoId) {
        mutableStateOf<Bitmap?>(null)
    }
    var scale by remember(photoId) {
        mutableStateOf(1f)
    }
    var offsetX by remember(photoId) {
        mutableStateOf(0f)
    }
    var offsetY by remember(photoId) {
        mutableStateOf(0f)
    }
    LaunchedEffect(
        photoId,
        orientation,
        width,
        height
    ) {
        if (photoId != null) {
            bitmap = withContext(Dispatchers.IO) {
                val uri = Uri.withAppendedPath(
                    MediaStore.Images.Media
                        .EXTERNAL_CONTENT_URI,
                    photoId
                )
                val sampleSize = calculateViewerInSampleSize(width, height)
                val options = BitmapFactory.Options()
                    .apply {
                        inSampleSize = sampleSize
                    }
                val decodedBitmap = contentResolver
                    .openInputStream(uri)
                    ?.use {
                            inputStream ->
                        BitmapFactory.decodeStream(
                            inputStream,
                            null,
                            options
                        )
                    }
                decodedBitmap?.let {
                    rotateBitmap(
                        bitmap = it,
                        orientation =
                            orientation
                    )
                }
            }
        }
    }
    Box(
        modifier =
            Modifier
                .fillMaxSize()
                .background(Color.Black)
    ) {
        bitmap?.let {
                loadedBitmap ->
            Image(
                bitmap = loadedBitmap.asImageBitmap(),
                contentDescription = "Photo $photoId",
                contentScale = ContentScale.Fit,
                modifier = Modifier
                    .fillMaxSize()
                    .pointerInput(
                        photoId,
                        loadedBitmap
                    ) {
                        detectTransformGestures { _, pan, zoom, _ ->
                            scale = (scale * zoom).coerceIn(1f, 5f)
                            if (scale == 1f) {
                                offsetX = 0f
                                offsetY = 0f
                            } else {
                                val containerWidth = size.width.toFloat()
                                val containerHeight = size.height.toFloat()
                                val imageWidth = loadedBitmap.width.toFloat()
                                val imageHeight = loadedBitmap.height.toFloat()
                                val fitScale = minOf(
                                    containerWidth / imageWidth,
                                    containerHeight / imageHeight
                                )
                                val displayedWidth = imageWidth * fitScale
                                val displayedHeight = imageHeight * fitScale
                                val maxX = maxOf(
                                    0f,
                                    (displayedWidth * scale - containerWidth) / 2f
                                )
                                val maxY = maxOf(
                                    0f,
                                    (displayedHeight * scale - containerHeight) / 2f
                                )
                                offsetX = (offsetX + pan.x).coerceIn(-maxX, maxX)
                                offsetY = (offsetY + pan.y).coerceIn(-maxY, maxY)
                            }
                        }
                    }
                    .graphicsLayer {
                        scaleX = scale
                        scaleY = scale
                        translationX = offsetX
                        translationY = offsetY
                    }
            )
        }
    }
}
