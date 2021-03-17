package de.dbis.myhealth.ui.settings;

import android.app.Application;
import android.content.Context;
import android.content.SharedPreferences;
import android.util.Log;
import android.widget.Toast;

import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MediatorLiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.android.gms.fitness.SessionsClient;
import com.spotify.android.appremote.api.SpotifyAppRemote;
import com.spotify.protocol.client.CallResult;
import com.spotify.protocol.types.Empty;
import com.spotify.protocol.types.PlayerContext;
import com.spotify.protocol.types.PlayerState;
import com.spotify.protocol.types.Repeat;

import java.util.List;

import de.dbis.myhealth.ApplicationConstants;
import de.dbis.myhealth.R;
import de.dbis.myhealth.models.SpotifyTrack;
import de.dbis.myhealth.repository.SpotifyRepository;
import kaaes.spotify.webapi.android.SpotifyApi;
import kaaes.spotify.webapi.android.models.AudioFeaturesTrack;
import kaaes.spotify.webapi.android.models.Track;
import retrofit.Callback;
import retrofit.RetrofitError;
import retrofit.client.Response;

public class SettingsViewModel extends AndroidViewModel {
    private final static String TAG = "SettingsViewModel";

    private SharedPreferences mSharedPreferences;

    private final MutableLiveData<SpotifyApi> mSpotifyApi;
    private final MutableLiveData<SpotifyAppRemote> mSpotifyAppRemote;
    private final MutableLiveData<PlayerState> mPlayerState;
    private final MutableLiveData<PlayerContext> mPlayerContext;
    private final MutableLiveData<SpotifyTrack> mSpotifyTrack;


    public SettingsViewModel(Application application) {
        super(application);

        this.mSharedPreferences = getApplication().getSharedPreferences(ApplicationConstants.PREFERENCES, Context.MODE_PRIVATE);

        // Live Data
        this.mSpotifyApi = new MutableLiveData<>();
        this.mSpotifyAppRemote = new MutableLiveData<>();
        this.mPlayerState = new MutableLiveData<>();
        this.mPlayerContext = new MutableLiveData<>();
        this.mSpotifyTrack = new MutableLiveData<>();
    }

    public void disconnect() {
        SpotifyAppRemote spotifyAppRemote = this.mSpotifyAppRemote.getValue();
        if (spotifyAppRemote != null) {
            SpotifyAppRemote.disconnect(spotifyAppRemote);
            this.mSpotifyAppRemote.setValue(null);
        }
    }

    public void play(SpotifyTrack spotifyTrack) {
        SpotifyAppRemote spotifyAppRemote = this.mSpotifyAppRemote.getValue();
        if (spotifyAppRemote != null) {
            spotifyAppRemote.getPlayerApi().setShuffle(false);
            spotifyAppRemote.getPlayerApi().setRepeat(Repeat.ONE);

            // play on local device
            boolean playOnLocalDeviceEnabled = this.mSharedPreferences.getBoolean(getApplication().getString(R.string.spotify_play_on_device_key), false);
            if (playOnLocalDeviceEnabled) {
                this.switchToLocalDevice();
            }

            // play or resume
            PlayerState playerState = this.getPlayerState().getValue();
            if (playerState != null && playerState.track.uri.endsWith(spotifyTrack.getTrackId())) {
                spotifyAppRemote.getPlayerApi().resume();
            } else {
                spotifyAppRemote.getPlayerApi().play(spotifyTrack.getTrack().uri);
            }

        } else {
            Log.d(TAG, "Couldn't play: " + spotifyTrack.getTrack().name);
        }
    }

    public void pause() {
        SpotifyAppRemote spotifyAppRemote = this.mSpotifyAppRemote.getValue();
        if (spotifyAppRemote != null) {
            spotifyAppRemote.getPlayerApi().pause();
        }
    }

    public void switchToLocalDevice() {
        SpotifyAppRemote spotifyAppRemote = this.mSpotifyAppRemote.getValue();
        if (spotifyAppRemote != null) {
            spotifyAppRemote.getConnectApi().connectSwitchToLocalDevice().setResultCallback(empty -> Toast.makeText(getApplication(), "Spotify now plays from your device", Toast.LENGTH_LONG).show());
        }
    }

    public LiveData<SpotifyAppRemote> getSpotifyRemoteApp() {
        return this.mSpotifyAppRemote;
    }

    public void setSpotifyAppRemote(SpotifyAppRemote spotifyAppRemote) {
        this.mSpotifyAppRemote.setValue(spotifyAppRemote);

        spotifyAppRemote.getPlayerApi().subscribeToPlayerState()
                .setEventCallback(this::setPlayerState)
                .setErrorCallback(error -> Log.d(TAG, "subscribeToPlayerState", error));

        spotifyAppRemote.getPlayerApi().subscribeToPlayerContext()
                .setEventCallback(this::setPlayerContext)
                .setErrorCallback(error -> Log.d(TAG, "subscribeToPlayerContext", error));
    }

    public LiveData<SpotifyApi> getSpotifyApi() {
        return this.mSpotifyApi;
    }

    public void setSpotifyApi(SpotifyApi spotifyApi) {
        this.mSpotifyApi.setValue(spotifyApi);
    }

    public LiveData<PlayerState> getPlayerState() {
        return mPlayerState;
    }

    public void setPlayerState(PlayerState playerState) {
        this.mPlayerState.setValue(playerState);
    }

    public LiveData<PlayerContext> getPlayerContext() {
        return mPlayerContext;
    }

    public void setPlayerContext(PlayerContext playerContext) {
        this.mPlayerContext.setValue(playerContext);
    }

    public LiveData<SpotifyTrack> loadSpotifyTrack(String id) {
        // LiveData updated by SpotifyAPI-Callbacks
        MutableLiveData<Track> trackLiveData = new MutableLiveData<>();
        MutableLiveData<AudioFeaturesTrack> audioFeaturesTrackLiveData = new MutableLiveData<>();

        // MediatorLiveData returns all callback data if present
        MediatorLiveData<SpotifyTrack> mediatorLiveData = new MediatorLiveData<>();

        // Check if audioFeaturesTrackLiveData is finished on Track-Callback
        mediatorLiveData.addSource(trackLiveData, value -> {
            Track track = trackLiveData.getValue();
            AudioFeaturesTrack audioFeaturesTrack = audioFeaturesTrackLiveData.getValue();
            if (track != null && audioFeaturesTrack != null) {
                SpotifyTrack spotifyTrack = new SpotifyTrack(track.id);
                spotifyTrack.setTrack(track);
                spotifyTrack.setAudioFeaturesTrack(audioFeaturesTrack);
                mediatorLiveData.setValue(spotifyTrack);
            }
        });

        // Check if trackLiveData is finished on AudioFeaturesTrack-Callback
        mediatorLiveData.addSource(audioFeaturesTrackLiveData, value -> {
            Track track = trackLiveData.getValue();
            AudioFeaturesTrack audioFeaturesTrack = audioFeaturesTrackLiveData.getValue();
            if (track != null && audioFeaturesTrack != null) {
                SpotifyTrack spotifyTrack = new SpotifyTrack(track.id);
                spotifyTrack.setTrack(track);
                spotifyTrack.setAudioFeaturesTrack(audioFeaturesTrack);
                mediatorLiveData.setValue(spotifyTrack);
            }
        });

        // Access data
        SpotifyApi spotifyApi = this.mSpotifyApi.getValue();
        if (spotifyApi != null) {
            // Get
            spotifyApi.getService().getTrack(id, new Callback<Track>() {
                @Override
                public void success(Track track, Response response) {
                    trackLiveData.setValue(track);
                }

                @Override
                public void failure(RetrofitError error) {
                    Log.d(TAG, "Get Track", error);
                    Toast.makeText(getApplication(), error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });

            spotifyApi.getService().getTrackAudioFeatures(id, new Callback<AudioFeaturesTrack>() {
                @Override
                public void success(AudioFeaturesTrack audioFeaturesTrack, Response response) {
                    audioFeaturesTrackLiveData.setValue(audioFeaturesTrack);
                }

                @Override
                public void failure(RetrofitError error) {
                    Log.d(TAG, "Get Track", error);
                    Toast.makeText(getApplication(), error.getMessage(), Toast.LENGTH_LONG).show();
                }
            });
        }

        return mediatorLiveData;
    }

    public LiveData<SpotifyTrack> getCurrentSpotifyTrack() {
        return this.mSpotifyTrack;
    }

    public void setCurrentSpotifyTrack(SpotifyTrack spotifyTrack) {
        this.mSpotifyTrack.setValue(spotifyTrack);
    }
}