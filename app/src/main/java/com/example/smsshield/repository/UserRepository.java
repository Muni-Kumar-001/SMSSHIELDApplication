package com.example.smsshield.repository;

import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.smsshield.database.SmsShieldDatabase;
import com.example.smsshield.database.dao.UserDao;
import com.example.smsshield.database.entities.User;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class UserRepository {
    private final UserDao userDao;
    private final LiveData<List<User>> allUsers;
    private final ExecutorService executor;
    
    public UserRepository(Context context) {
        SmsShieldDatabase database = SmsShieldDatabase.getInstance(context);
        userDao = database.userDao();
        allUsers = userDao.getAllUsers();
        executor = Executors.newSingleThreadExecutor();
    }
    
    public long insert(User user) {
        final long[] id = new long[1];
        executor.execute(() -> {
            id[0] = userDao.insert(user);
        });
        try {
            // Wait for the operation to complete
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return id[0];
    }
    
    public void update(User user) {
        SmsShieldDatabase.databaseWriteExecutor.execute(() -> {
            userDao.update(user);
        });
    }
    
    public void delete(User user) {
        SmsShieldDatabase.databaseWriteExecutor.execute(() -> {
            userDao.delete(user);
        });
    }
    
    public void deleteUserById(long userId) {
        SmsShieldDatabase.databaseWriteExecutor.execute(() -> {
            userDao.deleteUserById(userId);
        });
    }
    
    public LiveData<List<User>> getAllUsers() {
        return allUsers;
    }
    
    public LiveData<List<User>> getUsersByStatus(String status) {
        return userDao.getUsersByStatus(status);
    }
    
    public LiveData<List<User>> searchUsers(String query) {
        return userDao.searchUsers(query);
    }
    
    public User getUserByPhoneNumber(String phoneNumber) {
        final User[] user = new User[1];
        executor.execute(() -> {
            user[0] = userDao.getUserByPhoneNumber(phoneNumber);
        });
        try {
            // Wait for the operation to complete
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return user[0];
    }
    
    public void updateUserStatus(long userId, String newStatus) {
        SmsShieldDatabase.databaseWriteExecutor.execute(() -> {
            userDao.updateUserStatus(userId, newStatus);
        });
    }
    
    public boolean userExists(String phoneNumber) {
        final boolean[] exists = new boolean[1];
        executor.execute(() -> {
            exists[0] = userDao.userExists(phoneNumber);
        });
        try {
            // Wait for the operation to complete
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return exists[0];
    }
} 