package com.example.smsshield.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.smsshield.database.entities.User;

import java.util.List;

@Dao
public interface UserDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(User user);
    
    @Update
    void update(User user);
    
    @Delete
    void delete(User user);
    
    @Query("DELETE FROM users WHERE id = :userId")
    void deleteUserById(long userId);
    
    @Query("SELECT * FROM users ORDER BY name ASC")
    LiveData<List<User>> getAllUsers();
    
    @Query("SELECT * FROM users WHERE status = :status ORDER BY name ASC")
    LiveData<List<User>> getUsersByStatus(String status);
    
    @Query("SELECT * FROM users WHERE name LIKE '%' || :query || '%' OR phone_number LIKE '%' || :query || '%' ORDER BY name ASC")
    LiveData<List<User>> searchUsers(String query);
    
    @Query("SELECT * FROM users WHERE phone_number = :phoneNumber LIMIT 1")
    User getUserByPhoneNumber(String phoneNumber);
    
    @Query("UPDATE users SET status = :newStatus WHERE id = :userId")
    void updateUserStatus(long userId, String newStatus);
    
    @Query("SELECT EXISTS(SELECT 1 FROM users WHERE phone_number = :phoneNumber)")
    boolean userExists(String phoneNumber);
} 