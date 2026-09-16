package massey.android.myapplication.viewmodel

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import massey.android.myapplication.model.Photo

class PhotoGalleryViewModel : ViewModel() {

    var columns by mutableStateOf(2)
        private set
    var photos by mutableStateOf<List<Photo>>(emptyList())
        private set
    fun updatePhotos(newPhotos: List<Photo>) {
        photos = newPhotos
    }
    fun zoomIn() {
        if (columns >= 2) {
            columns--
        }
    }

    fun zoomOut() {
        if (columns < 3) {
            columns++
        }
    }
}