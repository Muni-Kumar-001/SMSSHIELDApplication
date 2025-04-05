package com.example.smsshield.adapters;

import android.content.Context;
import android.text.format.DateFormat;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smsshield.R;
import com.example.smsshield.database.entities.Message;

import java.util.ArrayList;
import java.util.Calendar;
import java.util.Date;
import java.util.List;

public class ChatAdapter extends RecyclerView.Adapter<RecyclerView.ViewHolder> {
    
    private static final int VIEW_TYPE_SENT = 1;
    private static final int VIEW_TYPE_RECEIVED = 2;
    private static final int VIEW_TYPE_DATE_SEPARATOR = 3;
    
    private final Context context;
    private final List<Object> chatItems = new ArrayList<>();
    private final OnMessageLongClickListener longClickListener;
    
    public interface OnMessageLongClickListener {
        void onMessageLongClick(Message message, int position);
    }
    
    public ChatAdapter(Context context, OnMessageLongClickListener longClickListener) {
        this.context = context;
        this.longClickListener = longClickListener;
    }
    
    @NonNull
    @Override
    public RecyclerView.ViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        if (viewType == VIEW_TYPE_SENT) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_message_sent, parent, false);
            return new SentMessageViewHolder(view);
        } else if (viewType == VIEW_TYPE_RECEIVED) {
            View view = LayoutInflater.from(context).inflate(R.layout.item_message_received, parent, false);
            return new ReceivedMessageViewHolder(view);
        } else {
            View view = LayoutInflater.from(context).inflate(R.layout.item_date_separator, parent, false);
            return new DateSeparatorViewHolder(view);
        }
    }
    
    @Override
    public void onBindViewHolder(@NonNull RecyclerView.ViewHolder holder, int position) {
        Object item = chatItems.get(position);
        
        if (holder instanceof DateSeparatorViewHolder) {
            configureDateSeparatorViewHolder((DateSeparatorViewHolder) holder, (String) item);
        } else if (holder instanceof SentMessageViewHolder) {
            configureSentMessageViewHolder((SentMessageViewHolder) holder, (Message) item, position);
        } else if (holder instanceof ReceivedMessageViewHolder) {
            configureReceivedMessageViewHolder((ReceivedMessageViewHolder) holder, (Message) item, position);
        }
    }
    
    private void configureDateSeparatorViewHolder(DateSeparatorViewHolder holder, String date) {
        holder.textDateSeparator.setText(date);
    }
    
    private void configureSentMessageViewHolder(SentMessageViewHolder holder, Message message, int position) {
        holder.textMessageBody.setText(message.getContent());
        holder.textMessageTime.setText(formatTime(message.getTimestamp()));
        
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onMessageLongClick(message, getMessagePositionInOriginalList(position));
                return true;
            }
            return false;
        });
    }
    
    private void configureReceivedMessageViewHolder(ReceivedMessageViewHolder holder, Message message, int position) {
        holder.textMessageBody.setText(message.getContent());
        holder.textMessageTime.setText(formatTime(message.getTimestamp()));
        
        // Set background based on message status
        int backgroundResource = getStatusBackgroundResource(message.getStatus());
        holder.messageContainer.setBackgroundResource(backgroundResource);
        
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onMessageLongClick(message, getMessagePositionInOriginalList(position));
                return true;
            }
            return false;
        });
    }
    
    private int getMessagePositionInOriginalList(int adapterPosition) {
        int count = 0;
        for (int i = 0; i <= adapterPosition; i++) {
            if (chatItems.get(i) instanceof Message) {
                count++;
            }
        }
        return count - 1;
    }
    
    @Override
    public int getItemCount() {
        return chatItems.size();
    }
    
    @Override
    public int getItemViewType(int position) {
        Object item = chatItems.get(position);
        if (item instanceof String) {
            return VIEW_TYPE_DATE_SEPARATOR;
        }
        
        Message message = (Message) item;
        return !message.isIncoming() ? VIEW_TYPE_SENT : VIEW_TYPE_RECEIVED;
    }
    
    public void setMessages(List<Message> messages) {
        chatItems.clear();
        
        if (messages != null && !messages.isEmpty()) {
            String currentDateString = null;
            
            for (Message message : messages) {
                // Get formatted date for this message
                String dateString = formatDate(message.getTimestamp());
                
                if (currentDateString == null || !currentDateString.equals(dateString)) {
                    // Add a date separator
                    currentDateString = dateString;
                    chatItems.add(dateString);
                }
                
                // Add the message
                chatItems.add(message);
            }
        }
        
        notifyDataSetChanged();
    }
    
    public Message getMessageAt(int position) {
        Object item = chatItems.get(position);
        if (item instanceof Message) {
            return (Message) item;
        }
        return null;
    }
    
    private String formatTime(long timestamp) {
        return DateFormat.format("HH:mm", new Date(timestamp)).toString();
    }
    
    private int getStatusBackgroundResource(String status) {
        if (Message.STATUS_SPAM.equals(status)) {
            return R.drawable.bg_message_spam;
        } else if (Message.STATUS_SAFE.equals(status)) {
            return R.drawable.bg_message_safe;
        } else {
            return R.drawable.bg_message_unknown;
        }
    }
    
    private String formatDate(long timestamp) {
        Calendar messageCalendar = Calendar.getInstance();
        messageCalendar.setTimeInMillis(timestamp);
        
        Calendar todayCalendar = Calendar.getInstance();
        Calendar yesterdayCalendar = Calendar.getInstance();
        yesterdayCalendar.add(Calendar.DAY_OF_YEAR, -1);
        
        // Clear time information for date comparison
        clearTimeInfo(messageCalendar);
        clearTimeInfo(todayCalendar);
        clearTimeInfo(yesterdayCalendar);
        
        if (messageCalendar.equals(todayCalendar)) {
            return "Today";
        } else if (messageCalendar.equals(yesterdayCalendar)) {
            return "Yesterday";
        } else {
            return DateFormat.format("dd/MM/yyyy", messageCalendar).toString();
        }
    }
    
    private void clearTimeInfo(Calendar calendar) {
        calendar.set(Calendar.HOUR_OF_DAY, 0);
        calendar.set(Calendar.MINUTE, 0);
        calendar.set(Calendar.SECOND, 0);
        calendar.set(Calendar.MILLISECOND, 0);
    }
    
    static class SentMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView textMessageBody;
        private final TextView textMessageTime;
        
        public SentMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textMessageBody = itemView.findViewById(R.id.text_message_body);
            textMessageTime = itemView.findViewById(R.id.text_message_time);
        }
    }
    
    static class ReceivedMessageViewHolder extends RecyclerView.ViewHolder {
        private final TextView textMessageBody;
        private final TextView textMessageTime;
        private final LinearLayout messageContainer;
        
        public ReceivedMessageViewHolder(@NonNull View itemView) {
            super(itemView);
            textMessageBody = itemView.findViewById(R.id.text_message_body);
            textMessageTime = itemView.findViewById(R.id.text_message_time);
            messageContainer = itemView.findViewById(R.id.message_container);
        }
    }
    
    static class DateSeparatorViewHolder extends RecyclerView.ViewHolder {
        private final TextView textDateSeparator;
        
        public DateSeparatorViewHolder(@NonNull View itemView) {
            super(itemView);
            textDateSeparator = itemView.findViewById(R.id.text_date_separator);
        }
    }
} 