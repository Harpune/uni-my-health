package de.dbis.myhealth.ui.user;

import android.app.Application;
import android.util.Log;
import android.widget.Toast;

import androidx.core.content.ContextCompat;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.google.firebase.auth.FirebaseAuth;
import com.google.firebase.auth.FirebaseUser;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.firebase.firestore.FirebaseFirestoreSettings;

import java.util.Date;

import de.dbis.myhealth.R;
import de.dbis.myhealth.models.User;

import static de.dbis.myhealth.ApplicationConstants.FIREBASE_COLLECTION_SESSIONS;
import static de.dbis.myhealth.ApplicationConstants.FIREBASE_COLLECTION_USERS;

public class UserViewModel extends AndroidViewModel {
    private final static String TAG = "UserViewModel";

    private final FirebaseAuth firebaseAuth;
    private final FirebaseFirestore firestore;
    private final MutableLiveData<User> mUser;
    private final MutableLiveData<FirebaseUser> mFirebaseUser;

    public UserViewModel(Application application) {
        super(application);

        // NETWORK
        this.firebaseAuth = FirebaseAuth.getInstance();
        this.firestore = FirebaseFirestore.getInstance();

        // settings
        FirebaseFirestoreSettings settings = new FirebaseFirestoreSettings.Builder()
                .setPersistenceEnabled(true)
                .build();
        this.firestore.setFirestoreSettings(settings);

        // live data
        this.mUser = new MutableLiveData<>();
        this.mFirebaseUser = new MutableLiveData<>();

        // Get user from firebase
        FirebaseUser firebaseUser = this.firebaseAuth.getCurrentUser();
        if (firebaseUser != null) {
            this.setFirebaseUser(firebaseUser);
        } else {
            this.signIn();
        }

//        this.firestore.clearPersistence();

    }

    private void signIn() {
        this.firebaseAuth.signInAnonymously()
                .addOnCompleteListener(ContextCompat.getMainExecutor(getApplication()), task -> {
                    if (task.isSuccessful()) {
                        // Sign in success, update UI with the signed-in user's information
                        Log.d("Firebase Login", "signInAnonymously:success");
                        this.setFirebaseUser(this.firebaseAuth.getCurrentUser());
                    } else {
                        // If sign in fails, display a message to the user.
                        Log.w("Firebase Login", "signInAnonymously:failure", task.getException());
                        Toast.makeText(getApplication(), "Authentication failed.", Toast.LENGTH_SHORT).show();
                    }
                });

    }

    private void setFirebaseUser(FirebaseUser firebaseUser) {
        this.mFirebaseUser.setValue(firebaseUser);
    }

    public LiveData<FirebaseUser> getFirebaseUser() {
        return mFirebaseUser;
    }

    public void setUser(User user) {
        this.mUser.setValue(user);
    }

    public LiveData<User> getUser() {
        return this.mUser;
    }

    public void subscribeToUser(FirebaseUser firebaseUser) {
        this.firestore.collection(FIREBASE_COLLECTION_USERS)
                .document(firebaseUser.getUid())
                .addSnapshotListener((snapshot, error) -> {
                    if (error != null) {
                        Log.w(TAG, "Listen failed", error);
                        return;
                    }

                    if (snapshot != null && snapshot.exists()) {
                        Log.d(TAG, "Current data: " + snapshot.getData());
                        User user = snapshot.toObject(User.class);
                        setUser(user);
                    } else {
                        Log.d(TAG, "No results found so we will create a user");
                        this.firestore.collection(FIREBASE_COLLECTION_USERS)
                                .document(firebaseUser.getUid())
                                .set(new User(firebaseUser.getUid()));
                    }
                });
    }


    public void save() {
        User user = this.mUser.getValue();
        if (user != null) {
            user.setUpdateDate(new Date());
            this.firestore.collection(FIREBASE_COLLECTION_USERS)
                    .document(user.getUserId())
                    .set(user)
                    .addOnSuccessListener(aVoid -> Toast.makeText(getApplication().getApplicationContext(), getApplication().getString(R.string.profile_saved), Toast.LENGTH_LONG).show())
                    .addOnFailureListener(e -> Toast.makeText(getApplication().getApplicationContext(), getApplication().getString(R.string.profile_saved_failed), Toast.LENGTH_LONG).show());
        }
    }
}