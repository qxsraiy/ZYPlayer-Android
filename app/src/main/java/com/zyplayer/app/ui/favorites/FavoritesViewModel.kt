package com.zyplayer.app.ui.favorites

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.LiveData
import androidx.lifecycle.asLiveData
import com.zyplayer.app.data.local.AppDatabase
import com.zyplayer.app.data.model.Favorite

class FavoritesViewModel(application: Application) : AndroidViewModel(application) {

    private val db = AppDatabase.getInstance(application)
    private val favoriteDao = db.favoriteDao()

    val favorites: LiveData<List<Favorite>> = favoriteDao.getAllFavorites().asLiveData()

    suspend fun deleteFavorite(favorite: Favorite) {
        favoriteDao.delete(favorite)
    }
}