package com.example.smsshield;

import android.Manifest;
import android.app.AlertDialog;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.IntentFilter;
import android.content.SharedPreferences;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.provider.Telephony;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.ActionBarDrawerToggle;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.view.GravityCompat;
import androidx.drawerlayout.widget.DrawerLayout;
import androidx.lifecycle.LiveData;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smsshield.adapters.MessageListAdapter;
import com.example.smsshield.database.entities.Message;
import com.example.smsshield.database.entities.User;
import com.example.smsshield.viewmodel.MessageViewModel;
import com.example.smsshield.viewmodel.UserViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;
import com.google.android.material.navigation.NavigationView;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class MainActivity extends AppCompatActivity 
        implements NavigationView.OnNavigationItemSelectedListener,
        MessageListAdapter.OnMessageClickListener,
        MessageListAdapter.OnMessageLongClickListener {
    
    private static final int PERMISSIONS_REQUEST_CODE = 123;
    private static final String[] REQUIRED_PERMISSIONS = {
            Manifest.permission.READ_SMS,
            Manifest.permission.RECEIVE_SMS,
            Manifest.permission.SEND_SMS,
            Manifest.permission.READ_CONTACTS,
            Manifest.permission.POST_NOTIFICATIONS
    };
    
    private static final int PAGE_SIZE = 50; // Number of messages to load at once
    private int currentPage = 0;
    private boolean isLoading = false;
    private boolean canLoadMore = true;
    
    private DrawerLayout drawerLayout;
    private RecyclerView recyclerViewMessages;
    private TextView textEmptyMessages;
    private FloatingActionButton fabNewContact;
    
    private MessageViewModel messageViewModel;
    private UserViewModel userViewModel;
    private MessageListAdapter adapter;
    
    // Track current filter for messages
    private String currentFilter = "all";
    private List<Message> allMessages = new ArrayList<>();
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main);
        
        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        // Set up drawer layout
        drawerLayout = findViewById(R.id.drawer_layout);
        ActionBarDrawerToggle toggle = new ActionBarDrawerToggle(
                this, drawerLayout, toolbar,
                R.string.app_name, R.string.app_name);
        drawerLayout.addDrawerListener(toggle);
        toggle.syncState();
        
        NavigationView navigationView = findViewById(R.id.nav_view);
        navigationView.setNavigationItemSelectedListener(this);
        
        // Set up recycler view
        recyclerViewMessages = findViewById(R.id.recycler_view_messages);
        textEmptyMessages = findViewById(R.id.text_empty_messages);
        fabNewContact = findViewById(R.id.fab_new_contact);
        
        recyclerViewMessages.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewMessages.setHasFixedSize(true);
        
        adapter = new MessageListAdapter(this, this, this);
        recyclerViewMessages.setAdapter(adapter);
        
        // Add scroll listener for pagination
        recyclerViewMessages.addOnScrollListener(new RecyclerView.OnScrollListener() {
            @Override
            public void onScrolled(@NonNull RecyclerView recyclerView, int dx, int dy) {
                super.onScrolled(recyclerView, dx, dy);
                
                LinearLayoutManager layoutManager = (LinearLayoutManager) recyclerView.getLayoutManager();
                int visibleItemCount = layoutManager.getChildCount();
                int totalItemCount = layoutManager.getItemCount();
                int firstVisibleItemPosition = layoutManager.findFirstVisibleItemPosition();
                
                if (!isLoading && canLoadMore) {
                    if ((visibleItemCount + firstVisibleItemPosition) >= totalItemCount
                            && firstVisibleItemPosition >= 0
                            && totalItemCount >= PAGE_SIZE) {
                        loadMoreMessages();
                    }
                }
            }
        });
        
        // Set up view models
        messageViewModel = new ViewModelProvider(this).get(MessageViewModel.class);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        
        // Now that view models are initialized, check permissions
        checkAndRequestPermissions();
        
        // Observe users
        userViewModel.getAllUsers().observe(this, users -> {
            adapter.setUsers(users);
        });
        
        // Set FAB click listener
        fabNewContact.setOnClickListener(v -> {
            Intent intent = new Intent(MainActivity.this, ContactsActivity.class);
            startActivity(intent);
        });
        
        // Set initial title
        updateTitle();
    }
    
    private void checkAndRequestPermissions() {
        List<String> permissionsNeeded = new ArrayList<>();
        
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                permissionsNeeded.add(permission);
            }
        }
        
        if (!permissionsNeeded.isEmpty()) {
            // Show more detailed info dialog before requesting permissions
            showPermissionsInfoDialog();
            ActivityCompat.requestPermissions(this, 
                    permissionsNeeded.toArray(new String[0]), 
                    PERMISSIONS_REQUEST_CODE);
        } else {
            // Already has permissions, load existing messages if first run
            Log.d("MainActivity", "All permissions granted, loading messages");
            if (isFirstRun()) {
                loadExistingSmsMessages();
            } else {
                loadInitialMessages();
            }
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            Map<String, Boolean> permissionResults = new HashMap<>();
            boolean contactsGranted = false;
            
            for (int i = 0; i < permissions.length; i++) {
                permissionResults.put(permissions[i], grantResults[i] == PackageManager.PERMISSION_GRANTED);
                allGranted = allGranted && (grantResults[i] == PackageManager.PERMISSION_GRANTED);
                
                Log.d("Permissions", permissions[i] + " granted: " + (grantResults[i] == PackageManager.PERMISSION_GRANTED));
                
                if (Manifest.permission.READ_CONTACTS.equals(permissions[i]) && 
                        grantResults[i] == PackageManager.PERMISSION_GRANTED) {
                    contactsGranted = true;
                    Log.d("Permissions", "Contacts permission granted!");
                }
            }
            
            if (permissionResults.getOrDefault(Manifest.permission.READ_SMS, false)) {
                // SMS permission granted, load messages
                if (isFirstRun()) {
                    Log.d("MainActivity", "Loading existing SMS messages after permission granted");
                    loadExistingSmsMessages();
                } else {
                    Log.d("MainActivity", "Loading initial messages after permission granted");
                    loadInitialMessages();
                }
            } else {
                // SMS permission denied, show error
                Log.d("MainActivity", "SMS permission denied");
                textEmptyMessages.setText("SMS permission required to display messages");
            }
            
            if (contactsGranted) {
                Toast.makeText(this, "Contacts permission granted", Toast.LENGTH_SHORT).show();
                updateContactsFromDeviceContacts();
            }
            
            if (!allGranted) {
                Toast.makeText(this, "Some permissions were denied. App may not function properly.", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void filterMessages() {
        Log.d("MainActivity", "Filtering messages with filter: " + currentFilter);
        
        if (allMessages == null) {
            loadInitialMessages(); // Reload from database if messages are null
            return;
        }
        
        new Thread(() -> {
            try {
                // Load device contacts for better name resolution
                Map<String, String> deviceContacts = loadDeviceContacts();
                
                // Group messages by phone number, keeping only the most recent message for each number
                Map<String, Message> latestMessageByContact = new HashMap<>();
                int filteredCount = 0;
                int totalCount = allMessages.size();
                
                Log.d("MainActivity", "Filtering " + totalCount + " messages");
                
                for (Message message : allMessages) {
                    String phoneNumber = message.getPhoneNumber();
                    String status = message.getStatus();
                    
                    // Apply current filter
                    if (!currentFilter.equals("all") && !status.equals(currentFilter)) {
                        Log.d("MainActivity", "Skipping message ID " + message.getId() + 
                              " with status " + status + " (filter: " + currentFilter + ")");
                        continue;
                    }
                    
                    filteredCount++;
                    
                    // Check if we already have a message from this contact
                    Message existingMessage = latestMessageByContact.get(phoneNumber);
                    
                    // If no message exists yet or this message is newer, update the map
                    if (existingMessage == null || message.getTimestamp() > existingMessage.getTimestamp()) {
                        latestMessageByContact.put(phoneNumber, message);
                    }
                }
                
                Log.d("MainActivity", "Filter result: " + filteredCount + " out of " + totalCount + 
                      " messages matched filter: " + currentFilter);
                
                // Convert map values to a list
                List<Message> filteredMessages = new ArrayList<>(latestMessageByContact.values());
                
                // Sort by timestamp (newest first)
                filteredMessages.sort((m1, m2) -> Long.compare(m2.getTimestamp(), m1.getTimestamp()));
                
                // Update UI on main thread
                runOnUiThread(() -> {
                    // Update adapter with device contact names
                    adapter.setDeviceContacts(deviceContacts);
                    adapter.setMessages(filteredMessages);
                    
                    if (filteredMessages.isEmpty()) {
                        recyclerViewMessages.setVisibility(View.GONE);
                        textEmptyMessages.setVisibility(View.VISIBLE);
                        if (currentFilter.equals("all")) {
                            textEmptyMessages.setText("No messages found");
                        } else {
                            textEmptyMessages.setText("No " + currentFilter + " messages found");
                        }
                    } else {
                        recyclerViewMessages.setVisibility(View.VISIBLE);
                        textEmptyMessages.setVisibility(View.GONE);
                    }
                });
            } catch (Exception e) {
                Log.e("MainActivity", "Error filtering messages", e);
            }
        }).start();
    }
    
    private Map<String, String> loadDeviceContacts() {
        Map<String, String> contactMap = new HashMap<>();
        
        // Check permission first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) 
                != PackageManager.PERMISSION_GRANTED) {
            return contactMap;
        }
        
        // Columns to fetch
        String[] projection = new String[]{
                ContactsContract.CommonDataKinds.Phone.NUMBER,
                ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
        };
        
        // Query device contacts
        try (Cursor cursor = getContentResolver().query(
                ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                projection,
                null,
                null,
                null)) {
                
            if (cursor != null) {
                int numberColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                int nameColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                
                while (cursor.moveToNext()) {
                    String phoneNumber = cursor.getString(numberColumnIndex);
                    String name = cursor.getString(nameColumnIndex);
                    
                    // Format phone number (remove spaces, dashes, etc.)
                    phoneNumber = phoneNumber.replaceAll("[\\s-()]", "");
                    
                    contactMap.put(phoneNumber, name);
                }
            }
        } catch (Exception e) {
            Log.e("MainActivity", "Error loading device contacts", e);
        }
        
        return contactMap;
    }
    
    private void updateTitle() {
        String title;
        switch (currentFilter) {
            case Message.STATUS_SAFE:
                title = getString(R.string.filter_safe);
                break;
            case Message.STATUS_SPAM:
                title = getString(R.string.filter_spam);
                break;
            case Message.STATUS_UNCHECKED:
                title = getString(R.string.filter_unknown);
                break;
            default:
                title = getString(R.string.app_name);
                break;
        }
        if (getSupportActionBar() != null) {
            getSupportActionBar().setTitle(title);
        }
    }
    
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        getMenuInflater().inflate(R.menu.main_menu, menu);
        
        // Set up search functionality
        MenuItem searchItem = menu.findItem(R.id.action_search);
        androidx.appcompat.widget.SearchView searchView = (androidx.appcompat.widget.SearchView) searchItem.getActionView();
        
        searchView.setOnQueryTextListener(new androidx.appcompat.widget.SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                adapter.filter(query);
                return true;
            }

            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);
                return true;
            }
        });
        
        return true;
    }
    
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == R.id.action_settings) {
            // TODO: Open settings activity
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
    
    @Override
    public boolean onNavigationItemSelected(@NonNull MenuItem item) {
        int id = item.getItemId();
        
        if (id == R.id.nav_all) {
            currentFilter = "all";
            refreshMessages();
        } else if (id == R.id.nav_safe) {
            currentFilter = Message.STATUS_SAFE;
            refreshMessages();
        } else if (id == R.id.nav_spam) {
            currentFilter = Message.STATUS_SPAM;
            refreshMessages();
        } else if (id == R.id.nav_unknown) {
            currentFilter = Message.STATUS_UNCHECKED;
            refreshMessages();
        } else if (id == R.id.nav_contacts) {
            Intent intent = new Intent(MainActivity.this, ContactsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_home) {
            currentFilter = "all";
            refreshMessages();
        }
        
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
    }
    
    /**
     * Refreshes messages by reloading from the database and applying the current filter
     */
    private void refreshMessages() {
        Log.d("MainActivity", "Refreshing messages with filter: " + currentFilter);
        // Reload messages from the database to get latest changes
        loadInitialMessages();
        // Update the title based on current filter
        updateTitle();
    }
    
    @Override
    public void onBackPressed() {
        if (drawerLayout.isDrawerOpen(GravityCompat.START)) {
            drawerLayout.closeDrawer(GravityCompat.START);
        } else {
            super.onBackPressed();
        }
    }
    
    @Override
    public void onMessageClick(String phoneNumber, String contactName) {
        Intent intent = new Intent(this, MessageDetailActivity.class);
        intent.putExtra("phone_number", phoneNumber);
        intent.putExtra("contact_name", contactName);
        startActivity(intent);
    }
    
    @Override
    public void onMessageLongClick(Message message, int position) {
        showMessageOptionsDialog(message);
    }
    
    private void showMessageOptionsDialog(Message message) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        // Create a list to hold all options
        java.util.List<String> optionList = new java.util.ArrayList<>();
        optionList.add(getString(R.string.message_delete));
        
        // Store user lookup result to avoid duplicate lookup
        final User contactUser = userViewModel.getUserByPhoneNumber(message.getPhoneNumber());
        
        if (message.isIncoming()) {
            // Add more options for received messages
            if (contactUser != null && !User.STATUS_BLOCKED.equals(contactUser.getStatus())) {
                optionList.add(getString(R.string.message_block_sender));
            }
            
            if (Message.STATUS_UNCHECKED.equals(message.getStatus())) {
                optionList.add(getString(R.string.message_mark_safe));
                optionList.add(getString(R.string.message_mark_spam));
            }
        }
        
        // Convert list to array
        final String[] options = optionList.toArray(new String[0]);
        
        builder.setItems(options, (dialog, which) -> {
            String selectedOption = options[which];
            
            if (selectedOption.equals(getString(R.string.message_delete))) {
                // First delete from Android SMS storage
                deleteSmsFromDevice(message);
                
                // Then delete from our database
                messageViewModel.delete(message);
                Toast.makeText(this, "Message deleted", Toast.LENGTH_SHORT).show();
                
                // Refresh the message list to reflect changes
                refreshMessages();
            } else if (selectedOption.equals(getString(R.string.message_block_sender))) {
                if (contactUser != null) {
                    contactUser.setStatus(User.STATUS_BLOCKED);
                    userViewModel.update(contactUser);
                    Toast.makeText(this, "Sender blocked", Toast.LENGTH_SHORT).show();
                    
                    // Refresh the message list
                    refreshMessages();
                }
            } else if (selectedOption.equals(getString(R.string.message_mark_safe))) {
                messageViewModel.updateMessageStatus(message.getId(), Message.STATUS_SAFE);
                Toast.makeText(this, "Marked as safe", Toast.LENGTH_SHORT).show();
                
                // Refresh the message list
                refreshMessages();
            } else if (selectedOption.equals(getString(R.string.message_mark_spam))) {
                messageViewModel.updateMessageStatus(message.getId(), Message.STATUS_SPAM);
                Toast.makeText(this, "Marked as spam", Toast.LENGTH_SHORT).show();
                
                // Refresh the message list
                refreshMessages();
            }
        });
        
        builder.show();
    }
    
    private void deleteSmsFromDevice(Message message) {
        // Only attempt to delete if this is an incoming message (as we don't have URI for sent messages)
        if (message.isIncoming()) {
            try {
                // Log the message we're trying to delete
                Log.d("MainActivity", "Attempting to delete SMS from device: ID=" + message.getId() + 
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
                    Log.d("MainActivity", "Deletion attempt with query '" + selection + 
                          "' deleted " + rows + " messages");
                    
                    deletedRows += rows;
                    
                    // If we deleted something, we can stop
                    if (rows > 0) {
                        break;
                    }
                }
                
                if (deletedRows > 0) {
                    Log.d("MainActivity", "Successfully deleted " + deletedRows + " message(s) from device SMS database");
                    // Force refresh the conversation
                    sendBroadcast(new Intent("android.provider.Telephony.SMS_RECEIVED"));
                } else {
                    Log.e("MainActivity", "Failed to delete message from device SMS database - no matching message found");
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error deleting SMS from device", e);
            }
        } else {
            Log.d("MainActivity", "Not deleting outgoing message from device SMS database");
        }
    }
    
    private boolean hasRequiredPermissions() {
        for (String permission : REQUIRED_PERMISSIONS) {
            if (ContextCompat.checkSelfPermission(this, permission) != PackageManager.PERMISSION_GRANTED) {
                Log.d("Permissions", "Missing permission: " + permission);
                return false;
            }
        }
        Log.d("Permissions", "All permissions granted including READ_CONTACTS");
        return true;
    }
    
    private void requestPermissions() {
        ActivityCompat.requestPermissions(this, REQUIRED_PERMISSIONS, PERMISSIONS_REQUEST_CODE);
    }
    
    private void showPermissionsInfoDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle("Permissions Needed");
        
        StringBuilder message = new StringBuilder();
        message.append("SmsShield needs the following permissions to function properly:\n\n");
        message.append("• SMS Read/Receive: To analyze your messages and protect you\n");
        message.append("• Send SMS: To allow you to reply to messages\n");
        message.append("• Contacts: To show contact names instead of just phone numbers\n");
        message.append("• Notifications: To alert you about incoming messages\n");
        
        builder.setMessage(message.toString());
        builder.setPositiveButton("OK", (dialog, which) -> dialog.dismiss());
        builder.show();
    }
    
    private void loadExistingSmsMessages() {
        // Show loading message
        textEmptyMessages.setVisibility(View.VISIBLE);
        textEmptyMessages.setText("Loading existing messages...");
        
        // Run SMS loading in a background thread to avoid blocking the UI
        new Thread(() -> {
            try {
                Uri uri = Uri.parse("content://sms");
                String[] projection = new String[]{"_id", "address", "body", "date", "type"};
                
                // Query all SMS messages
                try (Cursor cursor = getContentResolver().query(uri, projection, null, null, "date DESC")) {
                    int count = 0;
                    int max = 500; // Limit to 500 messages to avoid loading too many
                
                if (cursor != null && cursor.moveToFirst()) {
                        do {
                            // Extract message data
                            String messageId = cursor.getString(cursor.getColumnIndexOrThrow("_id"));
                            String phoneNumber = cursor.getString(cursor.getColumnIndexOrThrow("address"));
                            String messageBody = cursor.getString(cursor.getColumnIndexOrThrow("body"));
                            long messageDate = cursor.getLong(cursor.getColumnIndexOrThrow("date"));
                            int messageType = cursor.getInt(cursor.getColumnIndexOrThrow("type"));
                            
                            // Normalize phone number
                            if (phoneNumber != null) {
                                phoneNumber = phoneNumber.replaceAll("[^0-9+]", "");
                            } else {
                                phoneNumber = "unknown";
                            }
                            
                            // Check if user exists
                            User user = userViewModel.getUserByPhoneNumber(phoneNumber);
                            long userId;
                            
                            // Process in batches to avoid overwhelming the database
                            if (count % 50 == 0) {
                                // Update UI to show progress
                                final int processedCount = count;
                                runOnUiThread(() -> {
                                    textEmptyMessages.setText("Loading messages: " + processedCount + " processed");
                                });
                                
                                // Small delay to let UI update
                                Thread.sleep(10);
                            }
                            
                            if (user == null) {
                                // Create a new user if not exists
                                User newUser = new User(phoneNumber, phoneNumber, User.STATUS_UNKNOWN);
                                userId = userViewModel.insert(newUser);
                                // Create a new user object since insert doesn't update the ID
                                user = new User(phoneNumber, phoneNumber, User.STATUS_UNKNOWN);
                                user.setId(userId);
                                } else {
                                    userId = user.getId();
                            }
                            
                            // Create message object
                            boolean isIncoming = messageType == 1; // 1 = Received, 2 = Sent
                            String status = isIncoming ? Message.STATUS_UNCHECKED : "";
                            
                            Message message = new Message(
                                    userId,
                                    messageBody,
                                    messageDate,
                                    isIncoming,
                                    status,
                                    phoneNumber
                            );
                            
                            // Save message to database
                            messageViewModel.insert(message);
                            
                            count++;
                            
                            // Limit the number of messages to load
                            if (count >= max) {
                                break;
                        }
                    } while (cursor.moveToNext());
                    }
                    
                    // Mark first run completed
                    SharedPreferences prefs = getSharedPreferences("app_prefs", MODE_PRIVATE);
                    prefs.edit().putBoolean("first_run", false).apply();
                    
                    // Load messages into the UI
                    runOnUiThread(() -> {
                        loadInitialMessages();
                    });
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error loading SMS messages", e);
                runOnUiThread(() -> {
                    textEmptyMessages.setText("Error loading messages: " + e.getMessage());
                });
            }
        }).start();
    }
    
    private void loadContactsInfo(Map<String, String> contactsMap) {
        try {
            Log.d("Contacts", "Loading contacts from device");
            
            // Query the contacts content provider
            Uri uri = ContactsContract.CommonDataKinds.Phone.CONTENT_URI;
            String[] projection = new String[] {
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            };
            
            Cursor cursor = getContentResolver().query(uri, projection, null, null, null);
            
            if (cursor != null && cursor.moveToFirst()) {
                int nameIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                int numberIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                
                do {
                    String name = cursor.getString(nameIndex);
                    String number = cursor.getString(numberIndex);
                    
                    // Clean up number format to match SMS address format
                    if (number != null) {
                        number = number.replaceAll("[^0-9+]", "");
                        contactsMap.put(number, name);
                    }
                } while (cursor.moveToNext());
                cursor.close();
            }
            
            Log.d("Contacts", "Loaded " + contactsMap.size() + " contacts");
        } catch (Exception e) {
            Log.e("Contacts", "Error loading contacts", e);
        }
    }
    
    private void analyzeMessages(List<Message> messages) {
        // Create analyzer service
        com.example.smsshield.api.SmsAnalyzerService analyzerService = 
                new com.example.smsshield.api.SmsAnalyzerService(this, new com.example.smsshield.repository.MessageRepository(this));
        
        // Track how many messages have been analyzed
        final int[] analyzedCount = {0};
        final int totalCount = messages.size();
        
        // Analyze each message
        for (Message message : messages) {
            analyzerService.analyzeMessage(message, new com.example.smsshield.api.SmsAnalyzerService.AnalysisCallback() {
                @Override
                public void onResult(boolean isSpam, String resultMessage) {
                    analyzedCount[0]++;
                    
                    // Update message status in the UI thread
                    runOnUiThread(() -> {
                        // Update progress
                        Toast.makeText(MainActivity.this, 
                                "Analyzed " + analyzedCount[0] + " of " + totalCount + " messages", 
                                Toast.LENGTH_SHORT).show();
                        
                        // If all messages have been analyzed, refresh the contacts
                        if (analyzedCount[0] >= totalCount) {
                            refreshContactsPage();
                        }
                    });
                }
                
                @Override
                public void onError(String error) {
                    analyzedCount[0]++;
                    Log.e("MainActivity", "Error analyzing message: " + error);
                    
                    // Update UI on main thread
                    runOnUiThread(() -> {
                        // If all messages have been analyzed (including failures), refresh the contacts
                        if (analyzedCount[0] >= totalCount) {
                            refreshContactsPage();
                        }
                    });
                }
            });
        }
    }
    
    private void refreshContactsPage() {
        // Refresh the contacts page by notifying the adapter
        adapter.notifyDataSetChanged();
        
        // Show confirmation toast
        Toast.makeText(this, "Analysis complete!", Toast.LENGTH_SHORT).show();
    }
    
    private boolean isFirstRun() {
        SharedPreferences sharedPreferences = getSharedPreferences("app_prefs", MODE_PRIVATE);
        boolean isFirstRun = sharedPreferences.getBoolean("is_first_run", true);
        
        if (isFirstRun) {
            // Set first run to false for next time
            SharedPreferences.Editor editor = sharedPreferences.edit();
            editor.putBoolean("is_first_run", false);
            editor.apply();
        }
        
        return isFirstRun;
    }
    
    private void registerMessageAnalysisReceiver() {
        // We're now using the static receiver declared in the manifest
        // No dynamic registration needed
    }
    
    public static void handleMessageAnalyzed(Context context) {
        if (context instanceof MainActivity) {
            ((MainActivity) context).refreshMessageList();
        }
    }
    
    private void refreshMessageList() {
        // Trigger a reload of messages to show newly received messages
        runOnUiThread(() -> {
            // Reload messages from database
            loadInitialMessages();
            
            // Notify adapter to refresh UI
            if (adapter != null) {
                adapter.notifyDataSetChanged();
            }
            
            Log.d("MainActivity", "Message list refreshed after message analysis");
        });
    }
    
    @Override
    protected void onDestroy() {
        super.onDestroy();
        
        // No need to unregister the receiver
    }
    
    // Update existing users with info from device contacts
    private void updateContactsFromDeviceContacts() {
        new Thread(() -> {
            try {
                Map<String, String> contactsMap = new HashMap<>();
                loadContactsInfo(contactsMap);
                
                if (contactsMap.isEmpty()) {
                    return;
                }
                
                // Get all users
                List<User> users = userViewModel.getAllUsers().getValue();
                if (users == null || users.isEmpty()) {
                    return;
                }
                
                for (User user : users) {
                    String number = user.getPhoneNumber();
                    if (contactsMap.containsKey(number)) {
                        String contactName = contactsMap.get(number);
                        if (contactName != null && !contactName.equals(user.getName())) {
                            Log.d("Contacts", "Updating user " + number + " name to " + contactName);
                            user.setName(contactName);
                            userViewModel.update(user);
                        }
                    }
                }
                
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Updated contact names from device contacts", Toast.LENGTH_SHORT).show();
                });
            } catch (Exception e) {
                Log.e("Contacts", "Error updating contacts", e);
            }
        }).start();
    }
    
    private void loadInitialMessages() {
        currentPage = 0;
        canLoadMore = true;
        
        // Show loading indicator
        textEmptyMessages.setVisibility(View.VISIBLE);
        textEmptyMessages.setText("Loading messages...");
        
        // Check if we have the necessary permissions
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_SMS) 
                != PackageManager.PERMISSION_GRANTED) {
            textEmptyMessages.setText("SMS permission required to display messages");
            return;
        }
        
        // Load top 50 messages
        messageViewModel.getPagedMessages(currentPage, 50).observe(this, messages -> {
            isLoading = false;
            Log.d("MainActivity", "Loaded " + (messages != null ? messages.size() : 0) + " messages");
            
            if (messages == null || messages.isEmpty()) {
                textEmptyMessages.setText("No messages found");
                textEmptyMessages.setVisibility(View.VISIBLE);
                recyclerViewMessages.setVisibility(View.GONE);
                return;
            }
            
            this.allMessages = messages;
            filterMessages();
            
            if (messages.size() < 50) {
                canLoadMore = false;
            }
            
            // Check unchecked messages in background
            if (!messages.isEmpty()) {
                new Thread(() -> checkUnverifiedMessages(messages)).start();
            }
        });
    }
    
    // Check unverified messages
    private void checkUnverifiedMessages(List<Message> messages) {
        // Create analyzer service
        com.example.smsshield.api.SmsAnalyzerService analyzerService = 
                new com.example.smsshield.api.SmsAnalyzerService(this, new com.example.smsshield.repository.MessageRepository(this));
        
        // Find unverified messages and check them if we have internet
        if (isNetworkAvailable()) {
            List<Message> uncheckedMessages = new ArrayList<>();
            
            // Find unchecked messages that are incoming
            for (Message message : messages) {
                if (message.isIncoming() && Message.STATUS_UNCHECKED.equals(message.getStatus())) {
                    uncheckedMessages.add(message);
                }
                
                // Limit to 10 messages at a time
                if (uncheckedMessages.size() >= 10) {
                    break;
                }
            }
            
            // Process each unchecked message
            for (Message message : uncheckedMessages) {
                analyzerService.analyzeMessage(message, new com.example.smsshield.api.SmsAnalyzerService.AnalysisCallback() {
                    @Override
                    public void onResult(boolean isSpam, String resultMessage) {
                        // Update message status
                        String newStatus = isSpam ? Message.STATUS_SPAM : Message.STATUS_SAFE;
                        messageViewModel.updateMessageStatus(message.getId(), newStatus);
                    }
                    
                    @Override
                    public void onError(String error) {
                        // Leave as unchecked
                        Log.e("MainActivity", "Error checking message: " + error);
                    }
                });
            }
        } else {
            // Queue messages for later checking
            List<Message> messagesToQueue = new ArrayList<>();
            
            // Find unchecked messages that are incoming
            for (Message message : messages) {
                if (message.isIncoming() && Message.STATUS_UNCHECKED.equals(message.getStatus())) {
                    messagesToQueue.add(message);
                }
            }
            
            // Add all to queue
            addMessagesToCheckQueue(messagesToQueue);
        }
    }
    
    // Helper methods for message queue
    private void addMessagesToCheckQueue(List<Message> messages) {
        // Get shared preferences
        SharedPreferences prefs = getSharedPreferences("message_queue_prefs", MODE_PRIVATE);
        
        // Get existing queue
        Set<String> queuedMessageIds = prefs.getStringSet("message_ids", new HashSet<>());
        
        // Create a new set (because the returned set might be unmodifiable)
        Set<String> updatedQueue = new HashSet<>(queuedMessageIds);
        
        // Add new message IDs
        for (Message message : messages) {
            updatedQueue.add(String.valueOf(message.getId()));
        }
        
        // Save updated queue
        prefs.edit().putStringSet("message_ids", updatedQueue).apply();
        
        Log.d("MainActivity", "Added " + messages.size() + " messages to check queue");
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
    
    private void loadMoreMessages() {
        if (isLoading || !canLoadMore) return;
        
        isLoading = true;
        currentPage++;
        
        // Show a loading indicator (could be a progress bar at the bottom of the list)
        runOnUiThread(() -> {
            // You could show a loading indicator here
            Log.d("MainActivity", "Loading more messages, page: " + currentPage);
        });
        
        // Load messages in background thread
        new Thread(() -> {
            try {
                List<Message> newMessages = messageViewModel.getPagedMessagesSync(currentPage, PAGE_SIZE);
                
                if (newMessages == null || newMessages.isEmpty()) {
                    runOnUiThread(() -> {
                        canLoadMore = false;
                        isLoading = false;
                        Log.d("MainActivity", "No more messages to load");
                    });
                    return;
                }
                
                List<Message> combinedMessages = new ArrayList<>(allMessages);
                combinedMessages.addAll(newMessages);
                
                // Update UI on main thread
                runOnUiThread(() -> {
                    isLoading = false;
                    allMessages = combinedMessages;
                    filterMessages();
                    
                    if (newMessages.size() < PAGE_SIZE) {
                        canLoadMore = false;
                    }
                    
                    Log.d("MainActivity", "Loaded " + newMessages.size() + " more messages");
                });
                
                // Check unchecked messages in background
                checkUnverifiedMessages(newMessages);
                
            } catch (Exception e) {
                Log.e("MainActivity", "Error loading more messages", e);
                runOnUiThread(() -> {
                    isLoading = false;
                });
            }
        }).start();
    }
} 