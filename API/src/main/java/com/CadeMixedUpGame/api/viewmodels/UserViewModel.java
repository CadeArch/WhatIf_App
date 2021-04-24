package com.CadeMixedUpGame.api.viewmodels;
import androidx.databinding.ObservableArrayList;
import androidx.lifecycle.ViewModel;
import com.CadeMixedUpGame.api.models.User;
import com.google.firebase.database.DatabaseReference;
import com.google.firebase.database.FirebaseDatabase;


public class UserViewModel extends ViewModel {
    ObservableArrayList<User> users = new ObservableArrayList<User>();
    public String localName;
    DatabaseReference db;

    public UserViewModel() {
        db = FirebaseDatabase.getInstance().getReference();
    }

    public ObservableArrayList<User> getUsers() {
        return users;
    }

    public void pushData(User user) {
        db.child("rooms").child(user.gameRoom).child(user.userName).setValue(user);
    }

}

