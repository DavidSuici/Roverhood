package com.suici.roverhood.databases;

import android.util.Log;

import com.google.firebase.database.DataSnapshot;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;
import org.mindrot.jbcrypt.BCrypt;

import java.util.HashMap;
import java.util.Map;

public class FirebaseAccessCodeHasher {

    private static final String FIREBASE_URL = "https://roverhoodapp-default-rtdb.europe-west1.firebasedatabase.app";
    private static final String USERS_REF = "users";
    private static final int BCRYPT_COST = 12;

    public static void hashAllAccessCodes() {
        FirebaseDatabase database = FirebaseDatabase.getInstance(FIREBASE_URL);
        DatabaseReference usersRef = database.getReference(USERS_REF);

        usersRef.get().addOnSuccessListener(snapshot -> {
            if (snapshot.exists()) {
                Map<String, Object> updates = new HashMap<>();

                for (DataSnapshot child : snapshot.getChildren()) {
                    String userId = child.getKey();
                    String accessCode = child.child("accessCode").getValue(String.class);

                    if (accessCode != null && !isBCryptHash(accessCode)) {
                        String hashedAccessCode = BCrypt.hashpw(accessCode, BCrypt.gensalt(BCRYPT_COST));
                        Log.d("AccessCodeHash", "User " + userId + " accessCode hashed.");

                        updates.put(userId + "/accessCode", hashedAccessCode);
                    }
                }

                if (!updates.isEmpty()) {
                    usersRef.updateChildren(updates).addOnCompleteListener(task -> {
                        if (task.isSuccessful()) {
                            Log.d("AccessCodeHash", "All accessCodes successfully hashed and updated.");
                        } else {
                            Log.e("AccessCodeHash", "Failed to update accessCodes: " + task.getException());
                        }
                    });
                } else {
                    Log.d("AccessCodeHash", "No accessCodes required hashing.");
                }
            }
        }).addOnFailureListener(e -> {
            Log.e("AccessCodeHash", "Failed to fetch users from Firebase: " + e.getMessage());
        });
    }

    private static boolean isBCryptHash(String accessCode) {
        return accessCode.startsWith("$2a$") || accessCode.startsWith("$2b$") || accessCode.startsWith("$2y$");
    }
}