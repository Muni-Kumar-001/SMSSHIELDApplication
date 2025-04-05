package com.example.smsshield.repository;

import android.app.Application;
import android.content.Context;
import android.os.AsyncTask;
import android.util.Log;

import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;

import com.example.smsshield.database.SmsShieldDatabase;
import com.example.smsshield.database.dao.MessageDao;
import com.example.smsshield.database.entities.Message;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

public class MessageRepository {
    private final MessageDao messageDao;
    private final LiveData<List<Message>> allMessages;
    private final ExecutorService executorService;
    
    public MessageRepository(Context context) {
        SmsShieldDatabase database = SmsShieldDatabase.getInstance(context);
        messageDao = database.messageDao();
        allMessages = messageDao.getAllMessages();
        executorService = Executors.newFixedThreadPool(4);
    }
    
    public LiveData<List<Message>> getAllMessages() {
        return allMessages;
    }
    
    public LiveData<List<Message>> getPagedMessages(int page, int pageSize) {
        MutableLiveData<List<Message>> result = new MutableLiveData<>();
        
        executorService.execute(() -> {
            List<Message> messages = messageDao.getPagedMessages(page * pageSize, pageSize);
            result.postValue(messages);
        });
        
        return result;
    }
    
    public LiveData<List<Message>> getMessagesByUserId(long userId) {
        return messageDao.getMessagesByUserId(userId);
    }
    
    public LiveData<List<Message>> getMessagesByStatus(String status) {
        return messageDao.getMessagesByStatus(status);
    }
    
    public LiveData<List<Message>> searchMessages(String query) {
        return messageDao.searchMessages("%" + query + "%");
    }
    
    public long insert(Message message) {
        try {
            Future<Long> future = executorService.submit(() -> messageDao.insert(message));
            return future.get();
        } catch (Exception e) {
            e.printStackTrace();
            return -1;
        }
    }
    
    public void update(Message message) {
        executorService.execute(() -> messageDao.update(message));
    }
    
    public void delete(Message message) {
        executorService.execute(() -> messageDao.delete(message));
    }
    
    public void deleteMessageById(long messageId) {
        executorService.execute(() -> messageDao.deleteMessageById(messageId));
    }
    
    public void deleteAllMessagesForUser(long userId) {
        executorService.execute(() -> messageDao.deleteAllMessagesForUser(userId));
    }
    
    public void updateMessageStatus(long messageId, String status) {
        try {
            Future<?> future = executorService.submit(() -> {
                messageDao.updateMessageStatus(messageId, status);
                Log.d("MessageRepository", "Status updated for message " + messageId + " to " + status);
                return null;
            });
            // Wait for the operation to complete
            future.get();
        } catch (Exception e) {
            Log.e("MessageRepository", "Error updating message status", e);
        }
    }
    
    public Message getMessageById(long messageId) {
        try {
            Future<Message> future = executorService.submit(() -> messageDao.getMessageById(messageId));
            return future.get();
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }
    
    public LiveData<List<Message>> getMessagesForUser(long userId) {
        return messageDao.getMessagesByUserId(userId);
    }
    
    public int getMessageCountForUser(long userId) {
        final int[] count = new int[1];
        executorService.execute(() -> {
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
        executorService.execute(() -> {
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
    
    public List<Message> getPagedMessagesSync(int page, int pageSize) {
        try {
            Future<List<Message>> future = executorService.submit(() -> 
                messageDao.getPagedMessages(page * pageSize, pageSize));
            return future.get();
        } catch (Exception e) {
            Log.e("MessageRepository", "Error getting paged messages", e);
            return new ArrayList<>();
        }
    }
} 