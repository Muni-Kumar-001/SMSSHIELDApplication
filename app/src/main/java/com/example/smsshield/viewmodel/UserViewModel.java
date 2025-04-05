package com.example.smsshield.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.smsshield.database.entities.User;
import com.example.smsshield.repository.UserRepository;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserViewModel extends AndroidViewModel {
    
    private final UserRepository repository;
    private final LiveData<List<User>> allUsers;
    private final MutableLiveData<String> searchQuery = new MutableLiveData<>();
    private final LiveData<List<User>> searchResults;
    private final ExecutorService executorService = Executors.newSingleThreadExecutor();
    
    public UserViewModel(@NonNull Application application) {
        super(application);
        repository = new UserRepository(application);
        allUsers = repository.getAllUsers();
        
        // Transform search query into search results
        searchResults = Transformations.switchMap(searchQuery, query -> {
            if (query == null || query.isEmpty()) {
                return allUsers;
            } else {
                return repository.searchUsers(query);
            }
        });
    }
    
    public LiveData<List<User>> getAllUsers() {
        return allUsers;
    }
    
    public LiveData<List<User>> getUsersByStatus(String status) {
        return repository.getUsersByStatus(status);
    }
    
    public LiveData<List<User>> getSearchResults() {
        return searchResults;
    }
    
    public void setSearchQuery(String query) {
        searchQuery.setValue(query);
    }
    
    /**
     * Insert a user asynchronously
     * @param user The user to insert
     */
    public void insertAsync(User user) {
        executorService.execute(() -> repository.insert(user));
    }
    
    /**
     * Insert a user synchronously and return the inserted user ID
     * @param user The user to insert
     * @return The ID of the inserted user
     */
    public long insert(User user) {
        return repository.insert(user);
    }
    
    public void update(User user) {
        repository.update(user);
    }
    
    public void delete(User user) {
        repository.delete(user);
    }
    
    public void deleteUserById(long userId) {
        repository.deleteUserById(userId);
    }
    
    public void updateUserStatus(long userId, String status) {
        repository.updateUserStatus(userId, status);
    }
    
    public User getUserByPhoneNumber(String phoneNumber) {
        return repository.getUserByPhoneNumber(phoneNumber);
    }
    
    public boolean userExists(String phoneNumber) {
        return repository.userExists(phoneNumber);
    }
} 