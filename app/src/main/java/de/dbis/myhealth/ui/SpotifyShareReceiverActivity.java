package de.dbis.myhealth.ui;

import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.View;
import android.widget.Toast;

import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.databinding.DataBindingUtil;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.navigation.Navigation;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.spotify.sdk.android.authentication.AuthenticationClient;
import com.spotify.sdk.android.authentication.AuthenticationRequest;
import com.spotify.sdk.android.authentication.AuthenticationResponse;

import java.util.List;

import de.dbis.myhealth.ApplicationConstants;
import de.dbis.myhealth.R;
import de.dbis.myhealth.databinding.ActivitySpotifyReceiverBinding;
import de.dbis.myhealth.models.SpotifyTrack;
import de.dbis.myhealth.ui.settings.SettingsViewModel;
import kaaes.spotify.webapi.android.SpotifyApi;

import static de.dbis.myhealth.ApplicationConstants.SPOTIFY_CLIENT_ID;
import static de.dbis.myhealth.ApplicationConstants.SPOTIFY_REDIRECT_URI;
import static de.dbis.myhealth.ApplicationConstants.SPOTIFY_REQUEST_CODE;

public class SpotifyShareReceiverActivity extends AppCompatActivity {
    private static final String TAG = "SpotifyShareReceiverActivity";

    private ActivitySpotifyReceiverBinding mSpotifyReceiverBinding;
    private SettingsViewModel mSettingsViewModel;
    private SharedPreferences mSharedPreferences;

    private LiveData<SpotifyApi> mSpotifyApiLiveData;
    private LiveData<SpotifyTrack> mSpotifyTrackLiveData;
    private LiveData<List<SpotifyTrack>> mSpotifyTracksLiveData;

    @Override
    public void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        this.mSpotifyReceiverBinding = DataBindingUtil.setContentView(this, R.layout.activity_spotify_receiver);
        this.mSettingsViewModel = new ViewModelProvider(this).get(SettingsViewModel.class);
        this.mSharedPreferences = getSharedPreferences(ApplicationConstants.PREFERENCES, Context.MODE_PRIVATE);
    }

    @Override
    protected void onStart() {
        super.onStart();// get track and handle data
        this.mSpotifyApiLiveData = this.mSettingsViewModel.getSpotifyApi();
        this.mSpotifyTracksLiveData = this.mSettingsViewModel.getAllSpotifyTracks();

        String trackId = this.handleIntent(getIntent());
        if (trackId != null) {
            this.mSpotifyApiLiveData.observe(this, spotifyApi -> {
                this.mSpotifyTrackLiveData = this.mSettingsViewModel.loadSpotifyTrack(trackId);
                this.mSpotifyTrackLiveData.observe(this, spotifyTrack -> this.mSpotifyReceiverBinding.setSpotifyTrack(spotifyTrack));
            });

            // check if spotify is enabled
            boolean enabled = this.mSharedPreferences.getBoolean(getString(R.string.spotify_key), false);
            if (enabled) {
                // Connect is enabled. Disconnect otherwise.
                this.connectToApiOrAuth();
            } else {
                new MaterialAlertDialogBuilder(this)
                        .setTitle("Enable Spotify in your app")
                        .setMessage("You have to allow MyHealth to access Spotify before continuing")
                        .setCancelable(false)
                        .setPositiveButton("Enable", (dialogInterface, i) -> {
                            this.mSharedPreferences
                                    .edit()
                                    .putBoolean(getString(R.string.spotify_key), true)
                                    .apply();
                            this.connectToApiOrAuth();
                        })
                        .setNegativeButton("No", (dialogInterface, i) ->
                        {
                            Toast.makeText(this, "Sorry. Could't connect to Spotify without your permission.", Toast.LENGTH_LONG).show();
                            finish();
                        })
                        .show();
            }
        } else {
            Toast.makeText(this, "Could not get necessary data to include Spotify track into the app.", Toast.LENGTH_LONG).show();
        }
    }

    /**
     * Connect to the Spotify API or authenticate if no accesstoken is set.
     */
    public void connectToApiOrAuth() {
        String accessToken = this.mSharedPreferences.getString(getString(R.string.access_token), null);
        if (accessToken != null) {
            SpotifyApi spotifyApi = new SpotifyApi();
            spotifyApi.setAccessToken(accessToken);
            this.mSettingsViewModel.setSpotifyApi(spotifyApi);
        } else {
            AuthenticationRequest.Builder builder = new AuthenticationRequest.Builder(SPOTIFY_CLIENT_ID, AuthenticationResponse.Type.TOKEN, SPOTIFY_REDIRECT_URI);
            builder.setScopes(new String[]{"streaming", "app-remote-control"});
            AuthenticationRequest request = builder.build();
            AuthenticationClient.openLoginActivity(this, SPOTIFY_REQUEST_CODE, request);
        }
    }

    @Override
    protected void onStop() {
        super.onStop();
        if (this.mSpotifyApiLiveData != null) {
            this.mSpotifyApiLiveData.removeObservers(this);
        }

        if (this.mSpotifyTrackLiveData != null) {
            this.mSpotifyTrackLiveData.removeObservers(this);
        }

        if (this.mSpotifyTracksLiveData != null) {
            this.mSpotifyTracksLiveData.removeObservers(this);
        }
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, @Nullable Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        if (resultCode == RESULT_OK) {
            // Spotify
            if (requestCode == SPOTIFY_REQUEST_CODE) {
                AuthenticationResponse response = AuthenticationClient.getResponse(resultCode, data);
                switch (response.getType()) {
                    case TOKEN:
                        this.mSharedPreferences
                                .edit()
                                .putString(getString(R.string.access_token), response.getAccessToken())
                                .apply();

                        this.connectToApiOrAuth();
                        break;
                    case ERROR:
                        Log.d(TAG, "Spotify-onActivityResult: " + response.getError());
                        break;
                    default:
                        Log.d(TAG, "Spotify-onActivityResult: No TOKEN nor ERROR");

                }
            }
        } else {
            Toast.makeText(this, "Please grant permission", Toast.LENGTH_SHORT).show();
        }
    }

    public void saveSpotifyTrack(View view) {
        SpotifyTrack spotifyTrack = this.mSpotifyReceiverBinding.getSpotifyTrack();
        if (spotifyTrack != null) {
            this.mSpotifyTracksLiveData.observe(this, spotifyTracks -> {
                Toast.makeText(this, spotifyTrack.getTrack().name + " was successfully saved. You can now select it in Settings", Toast.LENGTH_LONG).show();
                finish();
            });

            this.mSettingsViewModel.save(spotifyTrack);
        }
    }

    private String handleIntent(Intent intent) {
        // Check if from other app
        String action = intent.getAction();
        String type = intent.getType();

        if (Intent.ACTION_SEND.equals(action) && type != null) {
            if ("text/plain".equals(type)) {
                String sharedText = intent.getStringExtra(Intent.EXTRA_TEXT);
                if (sharedText != null) {
                    // get track Id
                    String[] lines = sharedText.split("\\r?\\n");
                    Uri uri = Uri.parse(lines[1]);
                    return uri.getLastPathSegment();
                }
            }
        }
        return null;
    }
}