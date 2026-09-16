package massey.android.myapplication

import android.content.ContentResolver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Bundle
import android.provider.MediaStore
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import massey.android.myapplication.ui.theme.MyApplicationTheme
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.platform.LocalConfiguration
import androidx.compose.ui.platform.LocalDensity
class PhotoActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val photoId = intent.getStringExtra("photoId")
        setContent {
            MyApplicationTheme {
                Photo(
                    photoId = photoId,
                    contentResolver = contentResolver
                )
            }
        }

    }
}

@Composable
fun Photo(
    photoId: String?,
    contentResolver: ContentResolver,
) {
    var bitmap by remember(photoId) {
        mutableStateOf<Bitmap?>(null)
    }
    var scale by remember{
        mutableStateOf(1f)
    }
    var offsetX by remember {
        mutableStateOf(0f)
    }
    var offsetY by remember {
        mutableStateOf(0f)
    }
    val configuration = LocalConfiguration.current
    val density = LocalDensity.current
    val screenWidth = with(density) {
        configuration.screenWidthDp.dp.toPx()
    }

    val screenHeight = with(density) {
        configuration.screenHeightDp.dp.toPx()
    }


    LaunchedEffect(photoId) {
        bitmap = withContext(Dispatchers.IO) {
            val uri = Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                photoId
            )
            contentResolver
                .openInputStream(uri)
                ?.use { inputStream ->
                    BitmapFactory.decodeStream(
                        inputStream
                    )
                }
        }
    }
    bitmap?.let { loadedBitmap ->
        val imageWidth = loadedBitmap.width.toFloat()
        val imageHeight = loadedBitmap.height.toFloat()
        val fitScale = minOf(
            screenWidth / imageWidth,
            screenHeight / imageHeight
        )

        val displayedWidth = imageWidth * fitScale
        val displayedHeight = imageHeight * fitScale
        Image(
            bitmap = loadedBitmap.asImageBitmap(),
            contentDescription = "Photo $photoId",
            modifier = Modifier.fillMaxSize()
                    .pointerInput(photoId) {
                detectTransformGestures { _, pan, zoom, _ ->
                    scale*=zoom
                    scale = scale.coerceIn(1f, 100f)
                    val maxX = maxOf(
                        0f,
                        (displayedWidth * scale - screenWidth) / 2f
                    )

                    val maxY = maxOf(
                        0f,
                        (displayedHeight * scale - screenHeight) / 2f
                    )

                    offsetX = (offsetX + pan.x)
                        .coerceIn(-maxX, maxX)

                    offsetY = (offsetY + pan.y)
                        .coerceIn(-maxY, maxY)

                }
            }
                .graphicsLayer {
                    scaleX = scale
                    scaleY = scale
                    translationX = offsetX
                    translationY = offsetY
                },
            contentScale = ContentScale.Fit
        )
    }
}

