package de.dbis.myhealth.ui.dialogs;

import android.app.Dialog;
import android.content.DialogInterface;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AlertDialog;
import androidx.fragment.app.DialogFragment;
import androidx.preference.PreferenceManager;

import com.google.android.material.dialog.MaterialAlertDialogBuilder;

import de.dbis.myhealth.R;

public class DownloadSpotifyDialog extends DialogFragment {

    @NonNull
    @Override
    public Dialog onCreateDialog(@Nullable Bundle savedInstanceState) {
        return new MaterialAlertDialogBuilder(requireContext())
                .setTitle(getString(R.string.download_spotify))
                .setMessage(getString(R.string.download_spotify_confirmation))
                .setPositiveButton(getString(R.string.ok), (dialogInterface, i) -> {
                    dialogInterface.dismiss();
                    try {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=com.spotify.music")));
                    } catch (android.content.ActivityNotFoundException activityNotFoundException) {
                        startActivity(new Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=com.spotify.music")));
                    }
                })
                .setNegativeButton(getString(R.string.cancel), (dialog, i) ->
                        PreferenceManager.getDefaultSharedPreferences(requireContext())
                                .edit()
                                .putBoolean(getString(R.string.spotify_key), false)
                                .apply())
                .create();
    }
}
