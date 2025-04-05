package com.example.smsshield;

import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smsshield.adapters.ChatAdapter;
import com.example.smsshield.database.entities.Message;
import com.example.smsshield.database.entities.User;
import com.example.smsshield.viewmodel.MessageViewModel;
import com.example.smsshield.viewmodel.UserViewModel;
import com.example.smsshield.receivers.SmsReceiver;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.Date;

public class MessageDetailActivity extends AppCompatActivity implements ChatAdapter.OnMessageLongClickListener {
    
    private RecyclerView recyclerViewChat;
    private TextView textEmptyChat;
    private EditText editTextMessage;
    private ImageButton buttonSend;
    private FloatingActionButton buttonScrollToBottom;
    
    private MessageViewModel messageViewModel;
    private UserViewModel userViewModel;
    private ChatAdapter adapter;
    
    private String phoneNumber;
    private String contactName;
    private long userId;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_detail);
        
        // Get phone number from intent
        phoneNumber = getIntent().getStringExtra("phone_number");
        contactName = getIntent().getStringExtra("contact_name");
        
        if (phoneNumber == null) {
            finish();
            return;
        }
        
        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(contactName != null ? contactName : phoneNumber);
        }
        
        // Set up views
        recyclerViewChat = findViewById(R.id.recycler_view_chat);
        textEmptyChat = findViewById(R.id.text_empty_chat);
        editTextMessage = findViewById(R.id.edit_text_message);
        buttonSend = findViewById(R.id.button_send);
        buttonScrollToBottom = findViewById(R.id.button_scroll_to_bottom);
        
        if (buttonScrollToBottom != null) {
            buttonScrollToBottom.setOnClickListener(v -> scrollToBottom());
        }
        
        // Set up RecyclerView scroll listener
        recyclerViewChat.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                // Show the scroll button only when not at the bottom
                if (recyclerView.canScrollVertically(1)) {
                    buttonScrollToBottom.setVisibility(View.VISIBLE);
                } else {
                    buttonScrollToBottom.setVisibility(View.GONE);
                }
            }
        });
        
        // Set up recycler view
        LinearLayoutManager layoutManager = new LinearLayoutManager(this);
        layoutManager.setStackFromEnd(false); // Don't stack from end - show oldest first
        layoutManager.setReverseLayout(false); // Don't reverse order
        recyclerViewChat.setLayoutManager(layoutManager);
        
        adapter = new ChatAdapter(this, this);
        recyclerViewChat.setAdapter(adapter);
        
        // Set up view models
        messageViewModel = new ViewModelProvider(this).get(MessageViewModel.class);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        
        // Get user ID for this contact
        User user = userViewModel.getUserByPhoneNumber(phoneNumber);
        if (user != null) {
            userId = user.getId();
            
            // Get messages for this user
            messageViewModel.setCurrentUserId(userId);
            observeMessages();
        } else {
            // No user found, create a new one
            User newUser = new User(phoneNumber, phoneNumber, User.STATUS_UNKNOWN);
            userId = userViewModel.insert(newUser);
            messageViewModel.setCurrentUserId(userId);
            observeMessages();
        }
        
        // Set send button click listener
        buttonSend.setOnClickListener(v -> {
            sendMessage();
            // Auto-scroll to bottom when user sends a message
            new Handler().postDelayed(this::scrollToBottom, 200);
        });
    }
    
    private void sendMessage() {
        String messageContent = editTextMessage.getText().toString().trim();
        
        if (messageContent.isEmpty()) {
            return;
        }
        
        try {
            // Use package-specific action names to avoid unprotected broadcast warnings
            String sentAction = getPackageName() + ".SMS_SENT";
            String deliveredAction = getPackageName() + ".SMS_DELIVERED";
            
            // Create sent intent to track delivery status
            Intent sentIntent = new Intent(sentAction);
            PendingIntent sentPI = PendingIntent.getBroadcast(
                    this, 0, sentIntent, PendingIntent.FLAG_IMMUTABLE);
            
            // Create delivered intent to confirm delivery
            Intent deliveredIntent = new Intent(deliveredAction);
            PendingIntent deliveredPI = PendingIntent.getBroadcast(
                    this, 0, deliveredIntent, PendingIntent.FLAG_IMMUTABLE);
            
            // Create a BroadcastReceiver for sent SMS
            BroadcastReceiver sentReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    switch (getResultCode()) {
                        case Activity.RESULT_OK:
                            Log.d("MessageDetailActivity", "SMS sent successfully");
                            break;
                        case SmsManager.RESULT_ERROR_GENERIC_FAILURE:
                            Toast.makeText(context, "Generic failure", Toast.LENGTH_SHORT).show();
                            break;
                        case SmsManager.RESULT_ERROR_NO_SERVICE:
                            Toast.makeText(context, "No service", Toast.LENGTH_SHORT).show();
                            break;
                        case SmsManager.RESULT_ERROR_NULL_PDU:
                            Toast.makeText(context, "Null PDU", Toast.LENGTH_SHORT).show();
                            break;
                        case SmsManager.RESULT_ERROR_RADIO_OFF:
                            Toast.makeText(context, "Radio off", Toast.LENGTH_SHORT).show();
                            break;
                    }
                    try {
                        unregisterReceiver(this);
                    } catch (Exception e) {
                        Log.e("MessageDetailActivity", "Error unregistering receiver", e);
                    }
                }
            };
            
            // Create a BroadcastReceiver for delivered SMS
            BroadcastReceiver deliveredReceiver = new BroadcastReceiver() {
                @Override
                public void onReceive(Context context, Intent intent) {
                    switch (getResultCode()) {
                        case Activity.RESULT_OK:
                            Log.d("MessageDetailActivity", "SMS delivered");
                            break;
                        case Activity.RESULT_CANCELED:
                            Log.d("MessageDetailActivity", "SMS not delivered");
                            break;
                    }
                    try {
                        unregisterReceiver(this);
                    } catch (Exception e) {
                        Log.e("MessageDetailActivity", "Error unregistering receiver", e);
                    }
                }
            };
            
            // Register receivers based on Android version
            if (android.os.Build.VERSION.SDK_INT >= 33) { // Android 13+
                // For Android 13 and above, the flag is required
                registerReceiver(sentReceiver, new IntentFilter(sentAction), android.content.Context.RECEIVER_EXPORTED);
                registerReceiver(deliveredReceiver, new IntentFilter(deliveredAction), android.content.Context.RECEIVER_EXPORTED);
            } else if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.O) { // Android 8.0-12
                // For Android 8.0 to 12, we can use the flag
                registerReceiver(sentReceiver, new IntentFilter(sentAction), android.content.Context.RECEIVER_EXPORTED);
                registerReceiver(deliveredReceiver, new IntentFilter(deliveredAction), android.content.Context.RECEIVER_EXPORTED);
            } else { // Below Android 8.0
                // For older versions, the flag isn't available
                registerReceiver(sentReceiver, new IntentFilter(sentAction));
                registerReceiver(deliveredReceiver, new IntentFilter(deliveredAction));
            }
            
            // Send the SMS immediately with delivery tracking
            SmsManager smsManager = SmsManager.getDefault();
            
            // Check if message is longer than 160 chars
            if (messageContent.length() > 160) {
                ArrayList<String> messageParts = smsManager.divideMessage(messageContent);
                ArrayList<PendingIntent> sentIntents = new ArrayList<>();
                ArrayList<PendingIntent> deliveredIntents = new ArrayList<>();
                
                for (int i = 0; i < messageParts.size(); i++) {
                    sentIntents.add(sentPI);
                    deliveredIntents.add(deliveredPI);
                }
                
                smsManager.sendMultipartTextMessage(
                        phoneNumber, null, messageParts, sentIntents, deliveredIntents);
            } else {
                smsManager.sendTextMessage(
                        phoneNumber, null, messageContent, sentPI, deliveredPI);
            }
            
            // Save to database
            Message message = new Message(
                    userId,
                    messageContent,
                    new Date().getTime(),
                    false, // not incoming (sent by user)
                    Message.STATUS_SAFE,
                    phoneNumber
            );
            
            messageViewModel.insert(message);
            
            // Check if the contact exists
            User user = userViewModel.getUserByPhoneNumber(phoneNumber);
            if (user == null) {
                // Create as known
                user = new User(contactName != null ? contactName : phoneNumber, phoneNumber, User.STATUS_KNOWN);
                userId = userViewModel.insert(user);
            }
            
            // Clear input
            editTextMessage.setText("");
            
        } catch (Exception e) {
            Log.e("MessageDetailActivity", "Error sending SMS", e);
            Toast.makeText(this, "Failed to send SMS: " + e.getMessage(), Toast.LENGTH_SHORT).show();
        }
    }
    
    private void registerMessageAnalysisReceiver() {
        // We're now using the static receiver declared in the manifest
        // No dynamic registration needed
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // No need to unregister the receiver
    }
    
    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public void onMessageLongClick(Message message, int position) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        String[] options = {getString(R.string.message_delete)};
        
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // First delete from Android SMS storage
                deleteSmsFromDevice(message);
                
                // Then delete from our database
                messageViewModel.delete(message);
                Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.show();
    }
    
    private void deleteSmsFromDevice(Message message) {
        // Only attempt to delete if this is an incoming message (as we don't have URI for sent messages)
        if (message.isIncoming()) {
            try {
                // Constructing the SMS Uri
                Uri smsUri = Uri.parse("content://sms");
                
                // Query to find the message in the system database
                String selection = "address = ? AND date = ? AND body = ?";
                String[] selectionArgs = new String[]{
                        message.getPhoneNumber(),
                        String.valueOf(message.getTimestamp()),
                        message.getContent()
                };
                
                // Delete the message
                getContentResolver().delete(smsUri, selection, selectionArgs);
            } catch (Exception e) {
                Log.e("MessageDetailActivity", "Error deleting SMS from device", e);
            }
        }
    }
    
    // Update handleMessageAnalyzed method to auto-scroll only for new messages
    public static void handleMessageAnalyzed(Context context) {
        if (context instanceof MessageDetailActivity) {
            MessageDetailActivity activity = (MessageDetailActivity) context;
            // Only auto-scroll when a new message comes in
            if (activity.adapter != null && activity.adapter.getItemCount() > 0) {
                // This is a new message, scroll to it
                activity.recyclerViewChat.scrollToPosition(activity.adapter.getItemCount() - 1);
            }
        }
    }
    
    private void scrollToBottom() {
        if (adapter != null && adapter.getItemCount() > 0) {
            recyclerViewChat.smoothScrollToPosition(adapter.getItemCount() - 1);
        }
    }

    // Message observing
    private void observeMessages() {
        messageViewModel.getMessagesForUser().observe(this, messages -> {
            if (messages != null && !messages.isEmpty()) {
                textEmptyChat.setVisibility(View.GONE);
                recyclerViewChat.setVisibility(View.VISIBLE);
                
                // Update adapter with messages
                adapter.setMessages(messages);
                
                // Show/hide scroll button based on scroll position
                if (recyclerViewChat.canScrollVertically(1)) {
                    buttonScrollToBottom.setVisibility(View.VISIBLE);
                } else {
                    buttonScrollToBottom.setVisibility(View.GONE);
                }
            } else {
                textEmptyChat.setVisibility(View.VISIBLE);
                recyclerViewChat.setVisibility(View.GONE);
                buttonScrollToBottom.setVisibility(View.GONE);
            }
        });
    }
} 