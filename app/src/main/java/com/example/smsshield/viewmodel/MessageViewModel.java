package com.example.smsshield.viewmodel;

import android.app.Application;

import androidx.annotation.NonNull;
import androidx.lifecycle.AndroidViewModel;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.MutableLiveData;
import androidx.lifecycle.Transformations;

import com.example.smsshield.database.entities.Message;
import com.example.smsshield.database.entities.User;
import com.example.smsshield.repository.MessageRepository;
import com.example.smsshield.repository.UserRepository;

import java.util.Date;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

public class MessageViewModel extends AndroidViewModel {
    
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    
    private final LiveData<List<Message>> allMessages;
    
    private final MutableLiveData<Long> currentUserId = new MutableLiveData<>();
    private final LiveData<List<Message>> messagesForUser;
    
    private final MutableLiveData<String> currentPhoneNumber = new MutableLiveData<>();
    
    private final ExecutorService executorService;
    
    public MessageViewModel(@NonNull Application application) {
        super(application);
        messageRepository = new MessageRepository(application);
        userRepository = new UserRepository(application);
        
        allMessages = messageRepository.getAllMessages();
        
        // Messages for a specific user
        messagesForUser = Transformations.switchMap(currentUserId, userId -> {
            if (userId == null || userId <= 0) {
                return allMessages;
            } else {
                return messageRepository.getMessagesForUser(userId);
            }
        });
        
        executorService = Executors.newSingleThreadExecutor();
    }
    
    public LiveData<List<Message>> getAllMessages() {
        return allMessages;
    }
    
    public LiveData<List<Message>> getMessagesForUser() {
        return messagesForUser;
    }
    
    public void setCurrentUserId(long userId) {
        currentUserId.setValue(userId);
    }
    
    public void setCurrentPhoneNumber(String phoneNumber) {
        currentPhoneNumber.setValue(phoneNumber);
        // Get user by phone number and set the user ID
        User user = userRepository.getUserByPhoneNumber(phoneNumber);
        if (user != null) {
            setCurrentUserId(user.getId());
        }
    }
    
    public String getCurrentPhoneNumber() {
        return currentPhoneNumber.getValue();
    }
    
    public LiveData<List<Message>> getPagedMessages(int page, int pageSize) {
        return messageRepository.getPagedMessages(page, pageSize);
    }
    
    public LiveData<List<Message>> getMessagesByUserId(long userId) {
        return messageRepository.getMessagesByUserId(userId);
    }
    
    public LiveData<List<Message>> getMessagesByStatus(String status) {
        return messageRepository.getMessagesByStatus(status);
    }
    
    public LiveData<List<Message>> searchMessages(String query) {
        return messageRepository.searchMessages(query);
    }
    
    public void insert(Message message) {
        executorService.execute(() -> {
            // Check if the contact exists in the user table
            User user = userRepository.getUserByPhoneNumber(message.getPhoneNumber());
            if (user == null && message.isIncoming()) {
                // If the contact doesn't exist and it's a received message, create it as unknown
                user = new User(message.getPhoneNumber(), message.getPhoneNumber(), User.STATUS_UNKNOWN);
                userRepository.insert(user);
            }
            
            messageRepository.insert(message);
        });
    }
    
    public long insertSync(Message message) {
        return messageRepository.insert(message);
    }
    
    public void update(Message message) {
        executorService.execute(() -> messageRepository.update(message));
    }
    
    public void delete(Message message) {
        executorService.execute(() -> messageRepository.delete(message));
    }
    
    public void deleteMessageById(long messageId) {
        executorService.execute(() -> messageRepository.deleteMessageById(messageId));
    }
    
    public void deleteAllMessagesForUser(long userId) {
        executorService.execute(() -> messageRepository.deleteAllMessagesForUser(userId));
    }
    
    public void updateMessageStatus(long messageId, String status) {
        executorService.execute(() -> messageRepository.updateMessageStatus(messageId, status));
    }
    
    public Message getLatestMessageForUser(long userId) {
        return messageRepository.getLatestMessageForUser(userId);
    }
    
    public List<Message> getPagedMessagesSync(int page, int pageSize) {
        return messageRepository.getPagedMessagesSync(page, pageSize);
    }
} 