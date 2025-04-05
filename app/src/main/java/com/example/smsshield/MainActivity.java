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
        
        // Set up view models
        messageViewModel = new ViewModelProvider(this).get(MessageViewModel.class);
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        
        // Now that view models are initialized, check permissions
        if (!hasRequiredPermissions()) {
            showPermissionsInfoDialog();
            requestPermissions();
        } else {
            // Already has permissions, load existing messages if first run
            if (isFirstRun()) {
                loadExistingSmsMessages();
            }
        }
        
        // Observe messages
        messageViewModel.getAllMessages().observe(this, messages -> {
            this.allMessages = messages;
            filterMessages();
        });
        
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
    
    private void filterMessages() {
        if (allMessages == null) {
            return;
        }
        
        // Group messages by phone number, keeping only the most recent message for each number
        Map<String, Message> latestMessageByContact = new HashMap<>();
        
        for (Message message : allMessages) {
            String phoneNumber = message.getPhoneNumber();
            
            // Apply current filter
            if (!currentFilter.equals("all") && !message.getStatus().equals(currentFilter)) {
                continue;
            }
            
            // Check if we already have a message from this contact
            Message existingMessage = latestMessageByContact.get(phoneNumber);
            
            // If no message exists yet or this message is newer, update the map
            if (existingMessage == null || message.getTimestamp() > existingMessage.getTimestamp()) {
                latestMessageByContact.put(phoneNumber, message);
            }
        }
        
        // Convert map values to a list
        List<Message> filteredMessages = new ArrayList<>(latestMessageByContact.values());
        
        // Sort by timestamp (newest first)
        filteredMessages.sort((m1, m2) -> Long.compare(m2.getTimestamp(), m1.getTimestamp()));
        
        adapter.setMessages(filteredMessages);
        
        if (filteredMessages.isEmpty()) {
            recyclerViewMessages.setVisibility(View.GONE);
            textEmptyMessages.setVisibility(View.VISIBLE);
        } else {
            recyclerViewMessages.setVisibility(View.VISIBLE);
            textEmptyMessages.setVisibility(View.GONE);
        }
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
            filterMessages();
            updateTitle();
        } else if (id == R.id.nav_safe) {
            currentFilter = Message.STATUS_SAFE;
            filterMessages();
            updateTitle();
        } else if (id == R.id.nav_spam) {
            currentFilter = Message.STATUS_SPAM;
            filterMessages();
            updateTitle();
        } else if (id == R.id.nav_unknown) {
            currentFilter = Message.STATUS_UNCHECKED;
            filterMessages();
            updateTitle();
        } else if (id == R.id.nav_contacts) {
            Intent intent = new Intent(MainActivity.this, ContactsActivity.class);
            startActivity(intent);
        } else if (id == R.id.nav_home) {
            currentFilter = "all";
            filterMessages();
            updateTitle();
        }
        
        drawerLayout.closeDrawer(GravityCompat.START);
        return true;
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
            } else if (selectedOption.equals(getString(R.string.message_block_sender))) {
                if (contactUser != null) {
                    contactUser.setStatus(User.STATUS_BLOCKED);
                    userViewModel.update(contactUser);
                    Toast.makeText(this, "Sender blocked", Toast.LENGTH_SHORT).show();
                }
            } else if (selectedOption.equals(getString(R.string.message_mark_safe))) {
                messageViewModel.updateMessageStatus(message.getId(), Message.STATUS_SAFE);
                Toast.makeText(this, "Marked as safe", Toast.LENGTH_SHORT).show();
            } else if (selectedOption.equals(getString(R.string.message_mark_spam))) {
                messageViewModel.updateMessageStatus(message.getId(), Message.STATUS_SPAM);
                Toast.makeText(this, "Marked as spam", Toast.LENGTH_SHORT).show();
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
                Log.e("MainActivity", "Error deleting SMS from device", e);
            }
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
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == PERMISSIONS_REQUEST_CODE) {
            boolean allGranted = true;
            boolean contactsGranted = false;
            
            for (int i = 0; i < permissions.length; i++) {
                if (grantResults[i] != PackageManager.PERMISSION_GRANTED) {
                    allGranted = false;
                    Log.d("Permissions", "Permission denied: " + permissions[i]);
                } else if (Manifest.permission.READ_CONTACTS.equals(permissions[i])) {
                    contactsGranted = true;
                    Log.d("Permissions", "Contacts permission granted!");
                }
            }
            
            if (allGranted) {
                // Permissions granted, load existing SMS messages
                Toast.makeText(this, "All permissions granted", Toast.LENGTH_SHORT).show();
                loadExistingSmsMessages();
            } else {
                if (contactsGranted) {
                    Toast.makeText(this, "Contacts permission granted, some other permissions denied", Toast.LENGTH_LONG).show();
                    updateContactsFromDeviceContacts();
                } else {
                    Toast.makeText(this, "Contacts permission denied. Contact names will not be displayed.", Toast.LENGTH_LONG).show();
                }
                Toast.makeText(this, "Some permissions were denied. App may not function properly.", Toast.LENGTH_LONG).show();
            }
        }
    }
    
    private void loadExistingSmsMessages() {
        // Show a loading message
        Toast.makeText(this, "Loading existing messages...", Toast.LENGTH_SHORT).show();
        
        // Run in background thread to avoid blocking UI
        new Thread(() -> {
            try {
                // Load contacts info first if we have permission
                Map<String, String> contactsMap = new HashMap<>();
                if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) == PackageManager.PERMISSION_GRANTED) {
                    loadContactsInfo(contactsMap);
                }
                
                // Create a cursor to query the SMS content provider
                Uri smsUri = Uri.parse("content://sms");
                String[] projection = new String[] {
                        "_id", "address", "date", "body", "type"
                };
                
                // Sort by date descending (newest first)
                String sortOrder = "date DESC";
                
                // Query SMS content provider
                Cursor cursor = getContentResolver().query(smsUri, projection, null, null, sortOrder);
                
                if (cursor != null && cursor.moveToFirst()) {
                    int idxAddress = cursor.getColumnIndex("address");
                    int idxDate = cursor.getColumnIndex("date");
                    int idxBody = cursor.getColumnIndex("body");
                    int idxType = cursor.getColumnIndex("type");
                    
                    // Get all known phone numbers to avoid adding duplicates
                    Set<String> existingPhoneNumbers = new HashSet<>();
                    userViewModel.getAllUsers().getValue();
                    if (userViewModel.getAllUsers().getValue() != null) {
                        for (User user : userViewModel.getAllUsers().getValue()) {
                            existingPhoneNumbers.add(user.getPhoneNumber());
                        }
                    }
                    
                    // Keep track of message IDs to avoid duplicates
                    Map<String, Boolean> processedMessages = new HashMap<>();
                    
                    // Keep track of the top 10 oldest messages to analyze
                    List<Message> oldestMessages = new ArrayList<>();
                    
                    do {
                        // Check if all required columns exist
                        if (idxAddress >= 0 && idxDate >= 0 && idxBody >= 0 && idxType >= 0) {
                            String address = cursor.getString(idxAddress);
                            long date = cursor.getLong(idxDate);
                            String body = cursor.getString(idxBody);
                            int type = cursor.getInt(idxType);
                            
                            // Skip if message is empty
                            if (address == null || address.isEmpty() || body == null || body.isEmpty()) {
                                continue;
                            }
                            
                            // Clean up phone number
                            address = address.replaceAll("[^0-9+]", "");
                            
                            // Create a unique message identifier to avoid duplicates
                            String messageKey = address + "_" + date + "_" + body.hashCode();
                            
                            // Skip if message already processed
                            if (processedMessages.containsKey(messageKey)) {
                                continue;
                            }
                            
                            processedMessages.put(messageKey, true);
                            
                            // Get or create a user for this phone number
                            User user = userViewModel.getUserByPhoneNumber(address);
                            long userId;
                            
                            if (user == null) {
                                // Create new user if not exists
                                User newUser = new User(address, address, User.STATUS_UNKNOWN);
                                userId = userViewModel.insert(newUser);
                                existingPhoneNumbers.add(address);
                                
                                // Re-get the user to ensure we have the ID
                                user = userViewModel.getUserByPhoneNumber(address);
                                if (user == null) {
                                    // If still null, use the returned ID
                                    if (userId <= 0) {
                                        Log.e("MainActivity", "Failed to create user for: " + address);
                                        continue;
                                    }
                                } else {
                                    userId = user.getId();
                                }
                            } else {
                                userId = user.getId();
                                existingPhoneNumbers.add(address);
                            }
                            
                            // Create message entity with all required parameters
                            boolean isIncoming = (type == 1); // 1 for received, 2 for sent
                            Message message = new Message(
                                userId,      // userId
                                body,        // content
                                date,        // timestamp
                                isIncoming,  // isIncoming
                                Message.STATUS_UNCHECKED, // status
                                address      // phoneNumber
                            );
                            
                            // Insert message into database
                            long messageId = messageViewModel.insert(message);
                            
                            // Set the inserted ID on the message object
                            message.setId(messageId);
                            
                            // If this is an incoming message, add it to our list of messages to analyze
                            // We only want to analyze incoming messages
                            if (isIncoming && oldestMessages.size() < 10) {
                                oldestMessages.add(message);
                            }
                        }
                    } while (cursor.moveToNext());
                    
                    cursor.close();
                    
                    // Update UI to show messages have been loaded
                    runOnUiThread(() -> {
                        Toast.makeText(MainActivity.this, "Analyzing messages...", Toast.LENGTH_SHORT).show();
                    });
                    
                    // Analyze the oldest messages (which are the first in the list)
                    if (!oldestMessages.isEmpty()) {
                        analyzeMessages(oldestMessages);
                    } else {
                        // Update UI if no messages to analyze
                        runOnUiThread(() -> {
                            Toast.makeText(MainActivity.this, "No messages to analyze", Toast.LENGTH_SHORT).show();
                        });
                    }
                }
            } catch (Exception e) {
                Log.e("MainActivity", "Error loading SMS messages", e);
                runOnUiThread(() -> {
                    Toast.makeText(MainActivity.this, "Error loading messages: " + e.getMessage(), Toast.LENGTH_SHORT).show();
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
        // The adapter will be refreshed automatically through the LiveData observer,
        // but we can trigger an immediate refresh of the UI
        if (adapter != null) {
            adapter.notifyDataSetChanged();
        }
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
} 