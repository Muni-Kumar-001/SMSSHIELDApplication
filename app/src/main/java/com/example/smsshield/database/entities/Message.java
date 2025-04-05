package com.example.smsshield.database.entities;

import androidx.room.ColumnInfo;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import java.util.Date;

@Entity(tableName = "messages",
        foreignKeys = @ForeignKey(entity = User.class,
                parentColumns = "id",
                childColumns = "user_id",
                onDelete = ForeignKey.CASCADE),
        indices = {@Index("user_id")})
public class Message {
    public static final String STATUS_SAFE = "safe";
    public static final String STATUS_SPAM = "spam";
    public static final String STATUS_UNCHECKED = "unchecked";
    
    @PrimaryKey(autoGenerate = true)
    private long id;
    
    @ColumnInfo(name = "user_id")
    private long userId;
    
    @ColumnInfo(name = "content")
    private String content;
    
    @ColumnInfo(name = "timestamp")
    private long timestamp;
    
    @ColumnInfo(name = "is_incoming")
    private boolean isIncoming;
    
    @ColumnInfo(name = "status")
    private String status;
    
    @ColumnInfo(name = "phone_number")
    private String phoneNumber;
    
    public Message(long userId, String content, long timestamp, boolean isIncoming, String status, String phoneNumber) {
        this.userId = userId;
        this.content = content;
        this.timestamp = timestamp;
        this.isIncoming = isIncoming;
        this.status = status;
        this.phoneNumber = phoneNumber;
    }
    
    public long getId() {
        return id;
    }
    
    public void setId(long id) {
        this.id = id;
    }
    
    public long getUserId() {
        return userId;
    }
    
    public void setUserId(long userId) {
        this.userId = userId;
    }
    
    public String getContent() {
        return content;
    }
    
    public void setContent(String content) {
        this.content = content;
    }
    
    public long getTimestamp() {
        return timestamp;
    }
    
    public void setTimestamp(long timestamp) {
        this.timestamp = timestamp;
    }
    
    public boolean isIncoming() {
        return isIncoming;
    }
    
    public void setIncoming(boolean incoming) {
        isIncoming = incoming;
    }
    
    public String getStatus() {
        return status;
    }
    
    public void setStatus(String status) {
        this.status = status;
    }
    
    public String getPhoneNumber() {
        return phoneNumber;
    }
    
    public void setPhoneNumber(String phoneNumber) {
        this.phoneNumber = phoneNumber;
    }
} 