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
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.Image
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import massey.android.myapplication.model.Photo
import massey.android.myapplication.ui.theme.MyApplicationTheme
import massey.android.myapplication.viewmodel.PhotoGalleryViewModel
class MainActivity : ComponentActivity() {
    private val galleryViewModel: PhotoGalleryViewModel by viewModels()
    private var observerRegistered = false
    private val observer = object : ContentObserver(null) {
        override fun onChange(selfChange: Boolean) {
            super.onChange(selfChange)
            if (hasImagePermission()) {
                reloadPhotos()
            }
        }
    }
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            MyApplicationTheme {
                GalleryWithPermission(
                    contentResolver = contentResolver,
                    viewModel = galleryViewModel,
                    onPermissionGranted = {
                        registerObserverIfNeeded()
                    }
                )
            }
        }
    }
    override fun onResume() {
        super.onResume()
        if (hasImagePermission()) {
            registerObserverIfNeeded()
            reloadPhotos()
        }
    }
    override fun onPause() {
        if (observerRegistered) {
            contentResolver.unregisterContentObserver(observer)
            observerRegistered = false
        }
        super.onPause()
    }
    private fun hasImagePermission(): Boolean {
        val permission =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                Manifest.permission.READ_MEDIA_IMAGES
            } else {
                Manifest.permission.READ_EXTERNAL_STORAGE
            }
        return ContextCompat.checkSelfPermission(
            this,
            permission
        ) == PackageManager.PERMISSION_GRANTED
    }
    private fun registerObserverIfNeeded() {
        if (hasImagePermission() && !observerRegistered) {
            contentResolver.registerContentObserver(
                MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
                true,
                observer
            )
            observerRegistered = true
        }
    }
    private fun reloadPhotos() {
        if (!hasImagePermission()) {
            return
        }
        lifecycleScope.launch {
            val newPhotos = withContext(Dispatchers.IO) {
                getPhotos(contentResolver)
            }
            galleryViewModel.updatePhotos(
                newPhotos
            )
        }
    }
}
@Composable
fun GalleryWithPermission(
    contentResolver: ContentResolver,
    viewModel: PhotoGalleryViewModel,
    onPermissionGranted: () -> Unit
) {
    val context = LocalContext.current
    val permission =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
    var hasPermission by remember(permission) {
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
            permissionLauncher.launch(
                permission
            )
        }
    }
    LaunchedEffect(hasPermission) {
        if (hasPermission) {
            onPermissionGranted()
        }
    }
    if (hasPermission) {
        PhotoGallery(
            contentResolver = contentResolver,
            viewModel = viewModel
        )
    } else {
        Box(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            Button(
                onClick = {
                    permissionLauncher.launch(
                        permission
                    )
                }
            ) {
                Text(
                    text = "Allow photo access"
                )
            }
        }
    }
}
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PhotoGallery(
    contentResolver: ContentResolver,
    viewModel: PhotoGalleryViewModel
) {
    val context = LocalContext.current
    LaunchedEffect(Unit) {
        val photos =
            withContext(Dispatchers.IO) {
                getPhotos(
                    contentResolver
                )
            }
        viewModel.updatePhotos(
            photos
        )
    }
    Scaffold(
        topBar={
            TopAppBar(
                title = {
                    Text(
                        text = "Photo Gallery"
                    )
                }
            )
        }
    ) { innerPadding ->
        LazyVerticalGrid(
            columns =
                GridCells.Fixed(
                    viewModel.columns
                ),
            modifier =
                Modifier
                    .fillMaxSize()
                    .padding(innerPadding)
                    .pointerInput(Unit) {
                        awaitEachGesture {
                            awaitFirstDown(pass = PointerEventPass.Initial)
                            var zoom = 1f
                            var changed = false
                            do {
                                val event = awaitPointerEvent(pass = PointerEventPass.Initial)
                                zoom *= event.calculateZoom()
                                if (!changed) {
                                    if (zoom > 1.25f && viewModel.columns > 1) {
                                        viewModel.zoomIn()
                                        changed = true
                                    }
                                    else if (zoom < 0.8f && viewModel.columns < 4
                                    ) {
                                        viewModel.zoomOut()
                                        changed = true
                                    }
                                }
                            } while (event.changes.any {
                                    it.pressed
                                }
                            )
                        }
                    }
        ) {
            items(
                items = viewModel.photos,
                key = { photo ->
                    photo.id
                }
            ) { photo ->
                PhotoThumbnail(
                    photo = photo,
                    contentResolver = contentResolver,
                    modifier = Modifier.animateItem(),
                    onClick = {
                        val intent =
                            Intent(
                                context,
                                PhotoActivity::class.java
                            )
                        intent.putExtra(
                            "photoId",
                            photo.id
                        )
                        intent.putExtra(
                            "photoOrientation",
                            photo.orientation
                        )
                        intent.putExtra(
                            "photoWidth",
                            photo.width
                        )
                        intent.putExtra(
                            "photoHeight",
                            photo.height
                        )
                        context.startActivity(
                            intent
                        )
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
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val cacheKey =
        "${photo.id}_" +
                "${photo.orientation}_" +
                "${photo.width}_" +
                "${photo.height}"
    var bitmap by remember(cacheKey) {
        mutableStateOf(
            ThumbnailMemoryCache.get(
                cacheKey
            )
        )
    }
    LaunchedEffect(cacheKey) {
        if (bitmap == null) {
            bitmap =
                withContext(Dispatchers.IO) {
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
                    val options =
                        BitmapFactory.Options().apply {
                            inSampleSize = sampleSize
                        }
                    val decodedBitmap: Bitmap? =
                        contentResolver
                            .openInputStream(uri)
                            ?.use { inputStream ->
                                BitmapFactory.decodeStream(
                                    inputStream,
                                    null,
                                    options
                                )
                            }
                    val rotatedBitmap: Bitmap? =
                        decodedBitmap?.let {
                            rotateBitmap(
                                bitmap = it,
                                orientation =
                                    photo.orientation
                            )
                        }
                    if (rotatedBitmap != null) {
                        ThumbnailMemoryCache.put(
                            cacheKey,
                            rotatedBitmap
                        )
                    }
                    rotatedBitmap
                }
        }
    }
    Box(
        modifier =
            modifier
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
    val sortOrder = "${MediaStore.Images.Media.DATE_ADDED} DESC"
    contentResolver.query(
        MediaStore.Images.Media.EXTERNAL_CONTENT_URI,
        projection,
        null,
        null,
        sortOrder
    )?.use { cursor ->
        val idColumn = cursor.getColumnIndexOrThrow(
            MediaStore.Images.Media._ID
        )
        val orientationColumn = cursor.getColumnIndexOrThrow(
            MediaStore.Images.Media.ORIENTATION
        )
        val widthColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.WIDTH)
        val heightColumn = cursor.getColumnIndexOrThrow(MediaStore.Images.Media.HEIGHT)
        while (cursor.moveToNext()) {
            val id = cursor
                .getLong(idColumn)
                .toString()
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