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
    
    @Query("DELETE FROM messages WHERE user_id = :userId")
    void deleteAllMessagesForUser(long userId);
    
    @Query("SELECT * FROM messages ORDER BY timestamp DESC")
    LiveData<List<Message>> getAllMessages();
    
    @Query("SELECT * FROM messages ORDER BY timestamp DESC LIMIT :limit OFFSET :offset")
    List<Message> getPagedMessages(int offset, int limit);
    
    @Query("SELECT * FROM messages WHERE user_id = :userId ORDER BY timestamp ASC")
    LiveData<List<Message>> getMessagesByUserId(long userId);
    
    @Query("SELECT * FROM messages WHERE status = :status ORDER BY timestamp DESC")
    LiveData<List<Message>> getMessagesByStatus(String status);
    
    @Query("SELECT * FROM messages WHERE content LIKE :query ORDER BY timestamp DESC")
    LiveData<List<Message>> searchMessages(String query);
    
    @Query("SELECT COUNT(*) FROM messages WHERE user_id = :userId")
    int getMessageCountForUser(long userId);
    
    @Query("SELECT * FROM messages WHERE user_id = :userId ORDER BY timestamp DESC LIMIT 1")
    Message getLatestMessageForUser(long userId);
    
    @Query("UPDATE messages SET status = :status WHERE id = :messageId")
    void updateMessageStatus(long messageId, String status);
    
    @Query("SELECT * FROM messages WHERE id = :messageId")
    Message getMessageById(long messageId);
} 