package com.example.smsshield.adapters;

import android.content.Context;
import android.text.format.DateUtils;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smsshield.R;
import com.example.smsshield.database.entities.Message;
import com.example.smsshield.database.entities.User;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class MessageListAdapter extends RecyclerView.Adapter<MessageListAdapter.MessageViewHolder> {
    
    private final Context context;
    private final List<Message> messages = new ArrayList<>();
    private final List<Message> displayedMessages = new ArrayList<>();
    private final Map<String, User> userMap = new HashMap<>();
    private final OnMessageClickListener clickListener;
    private final OnMessageLongClickListener longClickListener;
    private String currentSearchQuery = "";
    
    public interface OnMessageClickListener {
        void onMessageClick(String phoneNumber, String contactName);
    }
    
    public interface OnMessageLongClickListener {
        void onMessageLongClick(Message message, int position);
    }
    
    public MessageListAdapter(Context context, OnMessageClickListener clickListener, OnMessageLongClickListener longClickListener) {
        this.context = context;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }
    
    @NonNull
    @Override
    public MessageViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_message_list, parent, false);
        return new MessageViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull MessageViewHolder holder, int position) {
        Message currentMessage = displayedMessages.get(position);
        User user = userMap.get(currentMessage.getPhoneNumber());
        
        // Set contact name
        String contactName = (user != null && user.getName() != null) ? user.getName() : currentMessage.getPhoneNumber();
        holder.textContactName.setText(contactName);
        
        // Set message preview
        holder.textMessagePreview.setText(currentMessage.getContent());
        
        // Hide timestamp in the contact list view
        holder.textMessageTime.setVisibility(View.GONE);
        
        // Set status and background color
        holder.textMessageStatus.setText(getStatusText(currentMessage.getStatus()));
        holder.textMessageStatus.setBackgroundResource(getStatusBackgroundResource(currentMessage.getStatus()));
        holder.textMessageStatus.setTextColor(ContextCompat.getColor(context, getStatusTextColor(currentMessage.getStatus())));
        
        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onMessageClick(currentMessage.getPhoneNumber(), contactName);
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onMessageLongClick(currentMessage, position);
                return true;
            }
            return false;
        });
    }
    
    @Override
    public int getItemCount() {
        return displayedMessages.size();
    }
    
    public void setMessages(List<Message> messages) {
        this.messages.clear();
        if (messages != null) {
            this.messages.addAll(messages);
        }
        filterMessages(currentSearchQuery);
    }
    
    public void setUsers(List<User> users) {
        userMap.clear();
        if (users != null) {
            for (User user : users) {
                userMap.put(user.getPhoneNumber(), user);
            }
        }
        filterMessages(currentSearchQuery);
    }
    
    public void filter(String query) {
        currentSearchQuery = query != null ? query.toLowerCase().trim() : "";
        filterMessages(currentSearchQuery);
    }
    
    private void filterMessages(String query) {
        displayedMessages.clear();
        
        if (query.isEmpty()) {
            displayedMessages.addAll(messages);
        } else {
            for (Message message : messages) {
                String phoneNumber = message.getPhoneNumber();
                User user = userMap.get(phoneNumber);
                String contactName = (user != null && user.getName() != null) ? user.getName() : phoneNumber;
                
                if (contactName.toLowerCase().contains(query) || 
                    phoneNumber.toLowerCase().contains(query) ||
                    message.getContent().toLowerCase().contains(query)) {
                    displayedMessages.add(message);
                }
            }
        }
        
        notifyDataSetChanged();
    }
    
    public Message getMessageAt(int position) {
        if (position >= 0 && position < displayedMessages.size()) {
            return displayedMessages.get(position);
        }
        return null;
    }
    
    private String getStatusText(String status) {
        if (Message.STATUS_SPAM.equals(status)) {
            return "SPAM";
        } else if (Message.STATUS_SAFE.equals(status)) {
            return "SAFE";
        } else if (Message.STATUS_UNCHECKED.equals(status)) {
            return "UNCHECKED";
        } else if (!status.isEmpty()) {
            return status.toUpperCase();
        } else {
            return "UNKNOWN";
        }
    }
    
    private int getStatusBackgroundResource(String status) {
        if (Message.STATUS_SPAM.equals(status)) {
            return R.drawable.bg_message_spam;
        } else if (Message.STATUS_SAFE.equals(status)) {
            return R.drawable.bg_message_safe;
        } else if (!status.isEmpty() && !Message.STATUS_UNCHECKED.equals(status)) {
            return R.drawable.bg_message_sent;
        } else {
            return R.drawable.bg_message_unknown;
        }
    }
    
    private int getStatusTextColor(String status) {
        // All statuses currently use the same text color
        return android.R.color.black;
    }
    
    private String formatMessageTime(long timestamp) {
        Calendar messageCalendar = Calendar.getInstance();
        messageCalendar.setTimeInMillis(timestamp);
        
        Calendar todayCalendar = Calendar.getInstance();
        Calendar yesterdayCalendar = Calendar.getInstance();
        yesterdayCalendar.add(Calendar.DAY_OF_YEAR, -1);
        
        // Clear time information for date comparison
        clearTimeInfo(messageCalendar);
        clearTimeInfo(todayCalendar);
        clearTimeInfo(yesterdayCalendar);
        
        String timeStr = android.text.format.DateFormat.format("HH:mm", new Date(timestamp)).toString();
        
        if (messageCalendar.equals(todayCalendar)) {
            return "Today " + timeStr;
        } else if (messageCalendar.equals(yesterdayCalendar)) {
            return "Yesterday " + timeStr;
        } else {
            String dateStr = android.text.format.DateFormat.format("dd/MM/yyyy", messageCalendar).toString();
            return dateStr + " " + timeStr;
        }
    }
    
    private void clearTimeInfo(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
    
    static class MessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView textContactName;
        private final TextView textMessagePreview;
        private final TextView textMessageTime;
        private final TextView textMessageStatus;
        
        public MessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textContactName = itemView.findViewById(R.id.text_contact_name);
            textMessagePreview = itemView.findViewById(R.id.text_message_preview);
            textMessageTime = itemView.findViewById(R.id.text_message_time);
            textMessageStatus = itemView.findViewById(R.id.text_message_status);
        }
    }
} 