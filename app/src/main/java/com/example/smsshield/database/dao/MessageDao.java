package com.example.smsshield.database.dao;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Delete;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.example.smsshield.database.entities.Message;

import java.util.List;

@Dao
public interface MessageDao {
    
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    long insert(Message message);
    
    @Update
    void update(Message message);
    
    @Delete
    void delete(Message message);
    
    @Query("DELETE FROM messages WHERE id = :messageId")
    void deleteMessageById(long messageId);
    
    @Query("SELECT * FROM messages WHERE user_id = :userId ORDER BY timestamp ASC")
    LiveData<List<Message>> getMessagesForUser(long userId);
    
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    LiveData<List<Message>> getAllMessages();
    
    @Query("SELECT COUNT(*) FROM messages WHERE user_id = :userId")
    int getMessageCountForUser(long userId);
    
    @Query("UPDATE messages SET status = :newStatus WHERE id = :messageId")
    void updateMessageStatus(long messageId, String newStatus);
    
    @Query("SELECT * FROM messages WHERE user_id = :userId AND timestamp = (SELECT MAX(timestamp) FROM messages WHERE user_id = :userId)")
    Message getLatestMessageForUser(long userId);
} 