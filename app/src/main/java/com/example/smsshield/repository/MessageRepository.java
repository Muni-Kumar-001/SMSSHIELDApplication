package com.example.smsshield.repository;

import android.app.Application;
import android.content.Context;

import androidx.lifecycle.LiveData;

import com.example.smsshield.database.SmsShieldDatabase;
import com.example.smsshield.database.dao.MessageDao;
import com.example.smsshield.database.entities.Message;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessageRepository {
    private final MessageDao messageDao;
    private final LiveData<List<Message>> allMessages;
    private final ExecutorService executor;
    
    public MessageRepository(Context context) {
        SmsShieldDatabase database = SmsShieldDatabase.getDatabase(context);
        messageDao = database.messageDao();
        allMessages = messageDao.getAllMessages();
        executor = Executors.newSingleThreadExecutor();
    }
    
    public long insert(Message message) {
        final long[] id = new long[1];
        executor.execute(() -> {
            id[0] = messageDao.insert(message);
        });
        try {
            // Wait for the operation to complete
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return id[0];
    }
    
    public void update(Message message) {
        SmsShieldDatabase.databaseWriteExecutor.execute(() -> {
            messageDao.update(message);
        });
    }
    
    public void delete(Message message) {
        SmsShieldDatabase.databaseWriteExecutor.execute(() -> {
            messageDao.delete(message);
        });
    }
    
    public void deleteMessageById(long messageId) {
        SmsShieldDatabase.databaseWriteExecutor.execute(() -> {
            messageDao.deleteMessageById(messageId);
        });
    }
    
    public LiveData<List<Message>> getAllMessages() {
        return allMessages;
    }
    
    public LiveData<List<Message>> getMessagesForUser(long userId) {
        return messageDao.getMessagesForUser(userId);
    }
    
    public int getMessageCountForUser(long userId) {
        final int[] count = new int[1];
        executor.execute(() -> {
            count[0] = messageDao.getMessageCountForUser(userId);
        });
        try {
            // Wait for the operation to complete
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return count[0];
    }
    
    public Message getLatestMessageForUser(long userId) {
        final Message[] message = new Message[1];
        executor.execute(() -> {
            message[0] = messageDao.getLatestMessageForUser(userId);
        });
        try {
            // Wait for the operation to complete
            Thread.sleep(100);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
        return message[0];
    }
    
    public void updateMessageStatus(long messageId, String newStatus) {
        SmsShieldDatabase.databaseWriteExecutor.execute(() -> {
            messageDao.updateMessageStatus(messageId, newStatus);
        });
    }
} 