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

import java.util.List;

public class MessageViewModel extends AndroidViewModel {
    
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    
    private final LiveData<List<Message>> allMessages;
    
    private final MutableLiveData<Long> currentUserId = new MutableLiveData<>();
    private final LiveData<List<Message>> messagesForUser;
    
    private final MutableLiveData<String> currentPhoneNumber = new MutableLiveData<>();
    
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
    
    public long insert(Message message) {
        // Check if the contact exists in the user table
        User user = userRepository.getUserByPhoneNumber(message.getPhoneNumber());
        if (user == null && message.isIncoming()) {
            // If the contact doesn't exist and it's a received message, create it as unknown
            user = new User(message.getPhoneNumber(), message.getPhoneNumber(), User.STATUS_UNKNOWN);
            userRepository.insert(user);
        }
        
        return messageRepository.insert(message);
    }
    
    public void update(Message message) {
        messageRepository.update(message);
    }
    
    public void delete(Message message) {
        messageRepository.delete(message);
    }
    
    public void updateMessageStatus(long messageId, String status) {
        messageRepository.updateMessageStatus(messageId, status);
    }
    
    public Message getLatestMessageForUser(long userId) {
        return messageRepository.getLatestMessageForUser(userId);
    }
} 