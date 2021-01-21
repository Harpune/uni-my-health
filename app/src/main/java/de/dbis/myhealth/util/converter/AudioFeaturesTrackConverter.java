package de.dbis.myhealth.util.converter;

import android.util.Log;

import androidx.room.TypeConverter;

import com.google.gson.Gson;
import com.google.gson.reflect.TypeToken;

import java.lang.reflect.Type;

import kaaes.spotify.webapi.android.models.AudioFeaturesTrack;

public class AudioFeaturesTrackConverter {

    private final Gson gson = new Gson();
    private final Type type = new TypeToken<AudioFeaturesTrack>() {
    }.getType();

    @TypeConverter
    public String fromAudioFeaturesTrack(AudioFeaturesTrack audioFeaturesTrack) {
        if (audioFeaturesTrack == null) {
            return null;
        }

        String json = this.gson.toJson(audioFeaturesTrack, this.type);
        Log.d("JSON-audioFeaturesTrack", json);
        return json;
    }

    @TypeConverter
    public AudioFeaturesTrack toAudioFeaturesTrack(String json) {
        if (json == null) {
            return null;
        }

        AudioFeaturesTrack audioFeaturesTrack = this.gson.fromJson(json, this.type);
        Log.d("trackList", audioFeaturesTrack.toString());
        return audioFeaturesTrack;
    }
}