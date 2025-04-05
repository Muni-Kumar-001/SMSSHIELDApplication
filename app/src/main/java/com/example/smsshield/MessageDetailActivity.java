package com.example.smsshield;

import android.Manifest;
import android.app.Activity;
import android.app.AlertDialog;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.provider.ContactsContract;
import android.telephony.SmsManager;
import android.util.Log;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ImageButton;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
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
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class MessageDetailActivity extends AppCompatActivity implements ChatAdapter.OnMessageLongClickListener {
    
    private static final String TAG = "MessageDetailActivity";
    
    private RecyclerView recyclerViewChat;
    private TextView textEmptyChat;
    private EditText editTextMessage;
    private ImageButton buttonSend;
    private FloatingActionButton buttonScrollToBottom;
    private ProgressBar progressApiChecking;
    
    private MessageViewModel messageViewModel;
    private UserViewModel userViewModel;
    private ChatAdapter adapter;
    
    private String phoneNumber;
    private String contactName;
    private long userId;
    private User currentUser;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_message_detail);
        
        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        }
        
        // Set up views
        recyclerViewChat = findViewById(R.id.recycler_view_chat);
        textEmptyChat = findViewById(R.id.text_empty_chat);
        editTextMessage = findViewById(R.id.edit_text_message);
        buttonSend = findViewById(R.id.button_send);
        buttonScrollToBottom = findViewById(R.id.button_scroll_to_bottom);
        progressApiChecking = findViewById(R.id.progress_api_checking);
        
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
        layoutManager.setStackFromEnd(true); // Stack from end - show newest at bottom
        layoutManager.setReverseLayout(false); // Don't reverse order
        recyclerViewChat.setLayoutManager(layoutManager);
        
        adapter = new ChatAdapter(this, this);
        recyclerViewChat.setAdapter(adapter);
        
        // Set up view models
        messageViewModel = new ViewModelProvider(this).get(MessageViewModel.class);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        
        setupContactInfo();
        
        // Set send button click listener
        buttonSend.setOnClickListener(v -> {
            sendMessage();
            // Auto-scroll to bottom when user sends a message
            new Handler().postDelayed(this::scrollToBottom, 200);
        });
    }
    
    private void setupContactInfo() {
        // Get phone number from intent
        phoneNumber = getIntent().getStringExtra("phone_number");
        if (phoneNumber == null) {
            Toast.makeText(this, "Error: No phone number provided", Toast.LENGTH_SHORT).show();
            finish();
            return;
        }
        
        // Check if contact name was provided in intent
        String contactName = getIntent().getStringExtra("contact_name");
        
        // If not, try to look up from device contacts
        if (contactName == null || contactName.equals(phoneNumber)) {
            contactName = getContactNameFromDevice(phoneNumber);
        }
        
        // If still not found, use phone number as name
        if (contactName == null || contactName.isEmpty()) {
            contactName = phoneNumber;
        }
        
        // Set contact name in toolbar
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(contactName);
        }
        
        // Store contact name for later use
        this.contactName = contactName;
        
        // Get user by phone number and observe any changes
        userViewModel.getUserByPhoneNumber(phoneNumber);
        User user = userViewModel.getUserByPhoneNumber(phoneNumber);
        
        if (user == null) {
            // Create new user if not exists
            User newUser = new User(contactName, phoneNumber, User.STATUS_UNKNOWN);
            userId = userViewModel.insert(newUser);
            messageViewModel.setCurrentUserId(userId);
            observeMessages();
        } else {
            // User exists, set user ID and observe messages
            currentUser = user;
            userId = user.getId();
            updateContactStatusUI(user.getStatus());
        }
    }
    
    private String getContactNameFromDevice(String phoneNumber) {
        // Check permission first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) 
                != PackageManager.PERMISSION_GRANTED) {
            return null;
        }
        
        // Format phone number for matching
        String formattedNumber = phoneNumber.replaceAll("[\\s-()]", "");
        
        // Query for contact name
        String[] projection = new String[] {
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        };
        
        String selection = ContactsContract.CommonDataKinds.Phone.NUMBER + " LIKE ?";
        String[] selectionArgs = new String[] { "%" + formattedNumber + "%" };
        
        try (Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                selection,
                selectionArgs,
                null)) {
                
            if (cursor != null && cursor.moveToFirst()) {
                int nameColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                if (nameColumnIndex >= 0) {
                    return cursor.getString(nameColumnIndex);
                }
            }
        } catch (Exception e) {
            Log.e(TAG, "Error getting contact name", e);
        }
        
        return null;
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
                user = new User(contactName, phoneNumber, User.STATUS_KNOWN);
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
        
        // Build options based on message type
        List<String> optionsList = new ArrayList<>();
        optionsList.add(getString(R.string.message_delete));
        optionsList.add(getString(R.string.action_copy));
        
        // Add check option for received messages
        if (message.isIncoming()) {
            optionsList.add("Check Message");
        }
        
        String[] options = optionsList.toArray(new String[0]);
        
        builder.setItems(options, (dialog, which) -> {
            String selectedOption = options[which];
            
            if (selectedOption.equals(getString(R.string.message_delete))) {
                // Delete the message from database
                messageViewModel.delete(message);
                
                // Also delete from the device SMS database
                deleteSmsFromDevice(message);
                
                // Notify the system that SMS content has changed to refresh other apps
                sendBroadcast(new Intent("android.provider.Telephony.SMS_RECEIVED"));
                
                Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show();
            } else if (selectedOption.equals(getString(R.string.action_copy))) {
                // Copy to clipboard
                android.content.ClipboardManager clipboard = (android.content.ClipboardManager) getSystemService(Context.CLIPBOARD_SERVICE);
                android.content.ClipData clip = android.content.ClipData.newPlainText("Message", message.getContent());
                clipboard.setPrimaryClip(clip);
                Toast.makeText(this, "Message copied to clipboard", Toast.LENGTH_SHORT).show();
            } else if (selectedOption.equals("Check Message")) {
                // Check message via API
                checkMessageViaApi(message);
            }
        });
        
        builder.show();
    }
    
    private void deleteSmsFromDevice(Message message) {
        // Only attempt to delete if this is an incoming message (as we don't have URI for sent messages)
        if (message.isIncoming()) {
            try {
                // Log the message we're trying to delete
                Log.d(TAG, "Attempting to delete SMS from device: ID=" + message.getId() + 
                      ", Phone=" + message.getPhoneNumber() + ", Date=" + message.getTimestamp());
                
                // Constructing the SMS Uri
                Uri smsUri = Uri.parse("content://sms");
                
                // First try to find the message by id
                int deletedRows = 0;
                
                // Try to match on multiple criteria to better find the message
                String[] queries = {
                    // Try by phone and approximate time (more lenient)
                    "address LIKE ? AND date BETWEEN ? AND ?",
                    // Try by content and date range
                    "body = ? AND date BETWEEN ? AND ?",
                    // Try by all criteria (most specific)
                    "address LIKE ? AND body = ? AND date BETWEEN ? AND ?"
                };
                
                // Get a time range of 5 seconds around the message timestamp
                long timeStart = message.getTimestamp() - 5000;
                long timeEnd = message.getTimestamp() + 5000;
                
                for (String selection : queries) {
                    String[] selectionArgs;
                    
                    if (selection.startsWith("address")) {
                        if (selection.contains("body")) {
                            // Combined query
                            selectionArgs = new String[]{
                                "%" + message.getPhoneNumber() + "%",
                                message.getContent(),
                                String.valueOf(timeStart),
                                String.valueOf(timeEnd)
                            };
                        } else {
                            // Just phone number and time
                            selectionArgs = new String[]{
                                "%" + message.getPhoneNumber() + "%",
                                String.valueOf(timeStart),
                                String.valueOf(timeEnd)
                            };
                        }
                    } else {
                        // Just content and time
                        selectionArgs = new String[]{
                            message.getContent(),
                            String.valueOf(timeStart),
                            String.valueOf(timeEnd)
                        };
                    }
                    
                    // Try to delete with this query
                    int rows = getContentResolver().delete(smsUri, selection, selectionArgs);
                    Log.d(TAG, "Deletion attempt with query '" + selection + 
                          "' deleted " + rows + " messages");
                    
                    deletedRows += rows;
                    
                    // If we deleted something, we can stop
                    if (rows > 0) {
                        break;
                    }
                }
                
                if (deletedRows > 0) {
                    Log.d(TAG, "Successfully deleted " + deletedRows + " message(s) from device SMS database");
                    // Force refresh the conversation
                    sendBroadcast(new Intent("android.provider.Telephony.SMS_RECEIVED"));
                } else {
                    Log.e(TAG, "Failed to delete message from device SMS database - no matching message found");
                }
            } catch (Exception e) {
                Log.e(TAG, "Error deleting SMS from device", e);
            }
        } else {
            Log.d(TAG, "Not deleting outgoing message from device SMS database");
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
                
                // Auto-scroll to bottom when messages are loaded
                new Handler(Looper.getMainLooper()).postDelayed(this::scrollToBottom, 100);
                
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

    private void updateContactStatusUI(String status) {
        // Update UI based on contact status
        if (User.STATUS_BLOCKED.equals(status)) {
            // Show blocked status indicator
            Toast.makeText(this, "This contact is blocked", Toast.LENGTH_SHORT).show();
        }
        
        // Set up messages for this user - observeMessages must be called after currentUser is set
        if (currentUser != null) {
            userId = currentUser.getId();
            messageViewModel.setCurrentUserId(userId);
            observeMessages();
        }
    }

    // Add method to check message via API
    private void checkMessageViaApi(Message message) {
        // Show loading spinner
        progressApiChecking.setVisibility(View.VISIBLE);
        
        // Check network connectivity
        if (!isNetworkAvailable()) {
            // No internet, mark as unchecked and add to queue
            messageViewModel.updateMessageStatus(message.getId(), Message.STATUS_UNCHECKED);
            addToMessageCheckQueue(message.getId());
            progressApiChecking.setVisibility(View.GONE);
            Toast.makeText(this, "Message queued for checking when internet is available", Toast.LENGTH_SHORT).show();
            return;
        }
        
        // Use API service to check the message
        com.example.smsshield.api.SmsAnalyzerService analyzerService = 
                new com.example.smsshield.api.SmsAnalyzerService(this, new com.example.smsshield.repository.MessageRepository(this));
        
        analyzerService.analyzeMessage(message, new com.example.smsshield.api.SmsAnalyzerService.AnalysisCallback() {
            @Override
            public void onResult(boolean isSpam, String resultMessage) {
                // Update UI on main thread
                runOnUiThread(() -> {
                    // Hide spinner
                    progressApiChecking.setVisibility(View.GONE);
                    
                    // Update message status
                    String newStatus = isSpam ? Message.STATUS_SPAM : Message.STATUS_SAFE;
                    messageViewModel.updateMessageStatus(message.getId(), newStatus);
                    
                    // Show result toast
                    String resultText = isSpam ? "Message marked as SPAM" : "Message marked as SAFE";
                    Toast.makeText(MessageDetailActivity.this, resultText, Toast.LENGTH_SHORT).show();
                });
            }
            
            @Override
            public void onError(String error) {
                // Update UI on main thread
                runOnUiThread(() -> {
                    // Hide spinner
                    progressApiChecking.setVisibility(View.GONE);
                    
                    // Mark as unchecked and add to queue for later
                    messageViewModel.updateMessageStatus(message.getId(), Message.STATUS_UNCHECKED);
                    addToMessageCheckQueue(message.getId());
                    
                    // Show error toast
                    Toast.makeText(MessageDetailActivity.this, 
                            "Error checking message: " + error, Toast.LENGTH_SHORT).show();
                });
            }
        });
    }
    
    // Check if network is available
    private boolean isNetworkAvailable() {
        android.net.ConnectivityManager connectivityManager = (android.net.ConnectivityManager) 
                getSystemService(Context.CONNECTIVITY_SERVICE);
        
        if (connectivityManager != null) {
            if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.M) {
                android.net.Network network = connectivityManager.getActiveNetwork();
                if (network != null) {
                    android.net.NetworkCapabilities capabilities = 
                            connectivityManager.getNetworkCapabilities(network);
                    return capabilities != null && 
                            (capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_WIFI) || 
                             capabilities.hasTransport(android.net.NetworkCapabilities.TRANSPORT_CELLULAR));
                }
            } else {
                // Use deprecated method for older devices
                android.net.NetworkInfo networkInfo = connectivityManager.getActiveNetworkInfo();
                return networkInfo != null && networkInfo.isConnected();
            }
        }
        return false;
    }
    
    // Implement message queue for offline messages
    private static final String MESSAGE_QUEUE_PREFS = "message_queue_prefs";
    private static final String MESSAGE_QUEUE_KEY = "message_ids";
    
    private void addToMessageCheckQueue(long messageId) {
        // Get shared preferences
        SharedPreferences prefs = getSharedPreferences(MESSAGE_QUEUE_PREFS, MODE_PRIVATE);
        
        // Get existing queue
        Set<String> queuedMessageIds = prefs.getStringSet(MESSAGE_QUEUE_KEY, new HashSet<>());
        
        // Create a new set (because the returned set might be unmodifiable)
        Set<String> updatedQueue = new HashSet<>(queuedMessageIds);
        
        // Add new message ID
        updatedQueue.add(String.valueOf(messageId));
        
        // Save updated queue
        prefs.edit().putStringSet(MESSAGE_QUEUE_KEY, updatedQueue).apply();
        
        Log.d(TAG, "Added message ID " + messageId + " to check queue");
    }
    
    // Process queued messages if internet is available
    private void processMessageQueue() {
        if (!isNetworkAvailable()) {
            return;
        }
        
        // Get shared preferences
        SharedPreferences prefs = getSharedPreferences(MESSAGE_QUEUE_PREFS, MODE_PRIVATE);
        
        // Get queued message IDs
        Set<String> queuedMessageIds = prefs.getStringSet(MESSAGE_QUEUE_KEY, new HashSet<>());
        
        if (queuedMessageIds.isEmpty()) {
            return;
        }
        
        // Create analyzer service
        com.example.smsshield.api.SmsAnalyzerService analyzerService = 
                new com.example.smsshield.api.SmsAnalyzerService(this, new com.example.smsshield.repository.MessageRepository(this));
        
        // Process each message
        com.example.smsshield.repository.MessageRepository repository = 
                new com.example.smsshield.repository.MessageRepository(this);
        
        Set<String> processedIds = new HashSet<>();
        
        for (String idStr : queuedMessageIds) {
            try {
                long messageId = Long.parseLong(idStr);
                Message message = repository.getMessageById(messageId);
                
                if (message != null) {
                    // Show spinner for current message checking
                    runOnUiThread(() -> progressApiChecking.setVisibility(View.VISIBLE));
                    
                    // Check message
                    analyzerService.analyzeMessage(message, new com.example.smsshield.api.SmsAnalyzerService.AnalysisCallback() {
                        @Override
                        public void onResult(boolean isSpam, String resultMessage) {
                            // Update message status
                            String newStatus = isSpam ? Message.STATUS_SPAM : Message.STATUS_SAFE;
                            messageViewModel.updateMessageStatus(messageId, newStatus);
                            
                            // Mark as processed
                            processedIds.add(idStr);
                            
                            // If all messages processed, hide spinner and update queue
                            if (processedIds.size() >= queuedMessageIds.size()) {
                                updateQueueAfterProcessing(processedIds);
                                runOnUiThread(() -> progressApiChecking.setVisibility(View.GONE));
                            }
                        }
                        
                        @Override
                        public void onError(String error) {
                            // Keep in queue if error
                            Log.e(TAG, "Error processing queued message " + messageId + ": " + error);
                            
                            // If all messages attempted, hide spinner and update queue
                            processedIds.add(idStr);
                            if (processedIds.size() >= queuedMessageIds.size()) {
                                updateQueueAfterProcessing(processedIds);
                                runOnUiThread(() -> progressApiChecking.setVisibility(View.GONE));
                            }
                        }
                    });
                } else {
                    // Message no longer exists, mark as processed
                    processedIds.add(idStr);
                }
            } catch (Exception e) {
                Log.e(TAG, "Error processing message ID " + idStr, e);
                processedIds.add(idStr);
            }
        }
    }
    
    private void updateQueueAfterProcessing(Set<String> processedIds) {
        // Get shared preferences
        SharedPreferences prefs = getSharedPreferences(MESSAGE_QUEUE_PREFS, MODE_PRIVATE);
        
        // Get existing queue
        Set<String> queuedMessageIds = prefs.getStringSet(MESSAGE_QUEUE_KEY, new HashSet<>());
        
        // Create a new set with only unprocessed IDs
        Set<String> remainingQueue = new HashSet<>(queuedMessageIds);
        remainingQueue.removeAll(processedIds);
        
        // Save updated queue
        prefs.edit().putStringSet(MESSAGE_QUEUE_KEY, remainingQueue).apply();
        
        Log.d(TAG, "Processed " + processedIds.size() + " messages, " + remainingQueue.size() + " remaining in queue");
    }
    
    @Override
    protected void onResume() {
        super.onResume();
        
        // Process queued messages when activity resumes
        new Thread(this::processMessageQueue).start();
    }
} 