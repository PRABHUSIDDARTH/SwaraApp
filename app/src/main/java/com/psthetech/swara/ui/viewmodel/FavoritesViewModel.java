package com.psthetech.swara.ui.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.Transformations;

import com.psthetech.swara.data.db.AppDatabase;
import com.psthetech.swara.data.db.entity.FavoriteSong;
import com.psthetech.swara.data.repository.FavoritesRepository;
import com.psthetech.swara.domain.model.Song;

import java.util.ArrayList;
import java.util.List;

public class FavoritesViewModel extends AndroidViewModel {

    private final FavoritesRepository repository;
    private final LiveData<List<FavoriteSong>> favorites;
    private final LiveData<List<Long>> favoriteSongIds;
    private final LiveData<List<Song>> favoriteSongs;

    public FavoritesViewModel(@NonNull Application application) {
        super(application);
        AppDatabase db = AppDatabase.getInstance(application);
        repository = new FavoritesRepository(db);
        favorites = repository.getFavoritesLive();

        favoriteSongIds = Transformations.map(favorites, list -> {
            List<Long> ids = new ArrayList<>();
            if (list != null) {
                for (FavoriteSong item : list) {
                    ids.add(item.id);
                }
            }
            return ids;
        });

        favoriteSongs = Transformations.map(favorites, list -> {
            List<Song> songs = new ArrayList<>();
            if (list != null) {
                for (FavoriteSong item : list) {
                    Song canonical = com.psthetech.swara.data.repository.MusicRepository.getCanonicalSong(item.id);
                    long dur = item.duration > 0 ? item.duration : (canonical != null ? canonical.getDuration() : 0);
                    long albumId = item.albumId != 0 ? item.albumId : (canonical != null ? canonical.getAlbumId() : 0);
                    songs.add(new Song(
                            item.id,
                            item.title,
                            item.artist,
                            item.album,
                            albumId,
                            dur,
                            canonical != null ? canonical.getTrackNumber() : 0,
                            canonical != null ? canonical.getYear() : 0,
                            item.favoritedAt
                    ));
                }
            }
            return songs;
        });
    }

    public LiveData<List<FavoriteSong>> getFavorites() { return favorites; }

    public LiveData<List<Long>> getFavoriteSongIds() { return favoriteSongIds; }

    public LiveData<List<Song>> getFavoriteSongs() { return favoriteSongs; }

    public LiveData<Boolean> isFavorite(long songId) {
        return repository.isFavoriteLive(songId);
    }

    public LiveData<Boolean> isFavoriteLive(long songId) {
        return repository.isFavoriteLive(songId);
    }

    public void addFavorite(Song song) {
        repository.addFavorite(song);
    }

    public void removeFavorite(long songId) {
        repository.removeFavorite(songId);
    }

    public void toggleFavorite(Song song) {
        repository.toggleFavorite(song);
    }
}
