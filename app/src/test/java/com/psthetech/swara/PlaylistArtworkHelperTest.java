package com.psthetech.swara;

import android.content.Context;
import android.widget.ImageView;

import com.psthetech.swara.data.repository.PlaylistArtworkStore;
import com.psthetech.swara.util.PlaylistArtworkHelper;

import org.junit.Test;
import org.mockito.Mockito;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;

/**
 * JVM-level unit tests for PlaylistArtworkHelper.
 */
public class PlaylistArtworkHelperTest {

    @Test
    public void clear_handlesNullContextGracefully() {
        ImageView mockView = mock(ImageView.class);

        PlaylistArtworkHelper.clear(null, mockView);

        // Assert that the view's tag was cleared
        verify(mockView).setTag(com.psthetech.swara.R.id.ivPlaylistArtwork, null);
    }

    @Test
    public void clear_handlesNullViewGracefully() {
        // Should not throw any exceptions
        PlaylistArtworkHelper.clear(null, null);
    }

    @Test
    public void invalidatePlaylistCollage_delegatesToStore() {
        long playlistId = 100L;
        PlaylistArtworkStore mockStore = mock(PlaylistArtworkStore.class);

        PlaylistArtworkHelper.invalidatePlaylistCollage(null, playlistId, mockStore);

        // Verify that the helper delegates invalidation to the store
        verify(mockStore).invalidateCollage(playlistId);
    }

    @Test
    public void invalidatePlaylistCollage_handlesNullStoreGracefully() {
        long playlistId = 100L;
        // Should not throw exception
        PlaylistArtworkHelper.invalidatePlaylistCollage(null, playlistId, null);
    }
}
