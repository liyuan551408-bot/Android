package massey.android.myapplication
import android.Manifest
import android.content.ContentResolver
import android.content.Intent
import android.content.pm.PackageManager
import android.database.ContentObserver
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.MediaStore
import android.util.Log
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Scaffold
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import massey.android.myapplication.model.Photo
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.lifecycle.lifecycleScope
import massey.android.myapplication.ui.theme.MyApplicationTheme
import androidx.lifecycle.viewmodel.compose.viewModel
import kotlinx.coroutines.launch
import massey.android.myapplication.viewmodel.PhotoGalleryViewModel

class MainActivity : ComponentActivity() {
    private val galleryViewModel: PhotoGalleryViewModel by viewModels()
    private val observer = object : ContentObserver(null){
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            reloadPhotos()
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                GalleryWithPermission(
                    contentResolver = contentResolver,
                    viewModel = galleryViewModel
                )
            }
        }
    }
    override fun onResume() {
        super.onResume()

        contentResolver.registerContentObserver(
            MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
            true,
            observer
        )
        reloadPhotos()
    }
    override fun onPause() {

        contentResolver.unregisterContentObserver(
            observer
        )

        super.onPause()
    }
    private fun reloadPhotos(){
        lifecycleScope.launch {
            val newPhotos = withContext(Dispatchers.IO){
                getPhotos(contentResolver)
            }
            galleryViewModel.updatePhotos(newPhotos)
        }
    }
}
@Composable
fun GalleryWithPermission(
    contentResolver: ContentResolver,
    viewModel: PhotoGalleryViewModel
) {
    val context = LocalContext.current
    val permission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    var hasPermission by remember {
        mutableStateOf(
            ContextCompat.checkSelfPermission(
                context,
                permission
            ) == PackageManager.PERMISSION_GRANTED
        )
    }
    val permissionLauncher =
        rememberLauncherForActivityResult(
            contract = ActivityResultContracts.RequestPermission()
        ) { granted ->
            hasPermission = granted
        }
    LaunchedEffect(permission) {
        if (!hasPermission) {
            permissionLauncher.launch(permission)
        }
    }
    if (hasPermission) {
        PhotoGallery(
            contentResolver = contentResolver,
            viewModel = viewModel
        )
    }
}
@Composable
fun PhotoGallery(
    contentResolver: ContentResolver,
    viewModel: PhotoGalleryViewModel
) {
    val context = LocalContext.current
    var totalZoom by remember{
        mutableStateOf(1f)
    }
    LaunchedEffect(Unit) {
        val photos = withContext(Dispatchers.IO) {
            getPhotos(contentResolver)
        }
        viewModel.updatePhotos(photos)
        Log.d(
            "GalleryDebug",
            "PhotoGallery received ${photos.size} photos"
        )
    }
    Scaffold { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(viewModel.columns),
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .pointerInput(Unit){
                    detectTransformGestures { _, _, zoom, _ ->
                        totalZoom*=zoom
                        if(totalZoom>1.5f){
                            viewModel.zoomIn()
                            totalZoom=1f
                        }
                        else if(totalZoom<0.5f){
                            viewModel.zoomOut()
                            totalZoom=1f
                        }

                    }
                }

        ) {
            items(
                items = viewModel.photos,
                key = { photo -> photo.id }
            ) { photo ->
                PhotoThumbnail(
                    photo = photo,
                    contentResolver = contentResolver,
                    onClick = {
                        val intent = Intent(
                            context,
                            PhotoActivity::class.java
                        )
                        intent.putExtra("photoId", photo.id)
                        context.startActivity(intent)
                    }
                )
            }
        }
    }
}
@Composable
fun PhotoThumbnail(
    photo: Photo,
    contentResolver: ContentResolver,
    onClick: () -> Unit
) {
    var bitmap by remember(photo.id) {
        mutableStateOf<Bitmap?>(null)
    }

    LaunchedEffect(photo.id) {
        bitmap = withContext(Dispatchers.IO) {

            val uri = Uri.withAppendedPath(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                photo.id
            )

            val sampleSize = calculateInSampleSize(
                width = photo.width,
                height = photo.height,
                requiredWidth = 500,
                requiredHeight = 500
            )

            val options = BitmapFactory.Options().apply {
                inSampleSize = sampleSize
            }

            contentResolver
                .openInputStream(uri)
                ?.use { inputStream ->
                    BitmapFactory.decodeStream(
                        inputStream,
                        null,
                        options
                    )
                }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .padding(2.dp)
            .clickable {
                onClick()
            }
    ) {
        bitmap?.let { loadedBitmap ->

            Image(
                bitmap = loadedBitmap.asImageBitmap(),
                contentDescription = "Photo ${photo.id}",
                modifier = Modifier.fillMaxSize(),
                contentScale = ContentScale.Crop
            )
        }
    }
}
fun calculateInSampleSize(
    width: Int,
    height: Int,
    requiredWidth: Int,
    requiredHeight: Int
): Int {
    var inSampleSize = 1
    if (
        width > requiredWidth ||
        height > requiredHeight
    ) {
        val halfWidth = width / 2
        val halfHeight = height / 2
        while (
            halfWidth / inSampleSize >= requiredWidth &&
            halfHeight / inSampleSize >= requiredHeight
        ) {
            inSampleSize *= 2
        }
    }
    return inSampleSize
}
fun getPhotos(
    contentResolver: ContentResolver

): List<Photo> {
    val photos = mutableListOf<Photo>()
    val projection = arrayOf(
        MediaStore.Images.Media._ID,
        MediaStore.Images.Media.ORIENTATION,
        MediaStore.Images.Media.WIDTH,
        MediaStore.Images.Media.HEIGHT
    )
    val sortOrder =
        "${MediaStore.Images.Media.DATE_ADDED} DESC"
    contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        sortOrder
    )?.use { cursor ->
        val idColumn =
            cursor.getColumnIndexOrThrow(MediaStore.Images.Media._ID
            )
        val orientationColumn =
            cursor.getColumnIndexOrThrow(MediaStore.Images.Media.ORIENTATION
            )
        val widthColumn =
            cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH
            )
        val heightColumn =
            cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT
            )
        while (cursor.moveToNext()) {
            val id = cursor.getLong(idColumn).toString()
            val orientation = cursor.getInt(orientationColumn)
            val width = cursor.getInt(widthColumn)
            val height = cursor.getInt(heightColumn)
            photos.add(
                Photo(
                    id = id,
                    orientation = orientation,
                    width = width,
                    height = height
                )
            )
        }
    }
    return photos
}