package com.example.smsshield;

import android.Manifest;
import android.app.AlertDialog;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.database.Cursor;
import android.net.Uri;
import android.os.Bundle;
import android.provider.ContactsContract;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.ProgressBar;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smsshield.adapters.ContactAdapter;
import com.example.smsshield.database.entities.User;
import com.example.smsshield.viewmodel.UserViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class ContactsActivity extends AppCompatActivity implements 
        ContactAdapter.OnContactClickListener, 
        ContactAdapter.OnContactLongClickListener {
    
    private static final String TAG = "ContactsActivity";
    private RecyclerView recyclerViewContacts;
    private TextView textEmptyContacts;
    private ProgressBar progressLoading;
    private FloatingActionButton fabAddContact;
    private SearchView searchView;
    
    private UserViewModel userViewModel;
    private ContactAdapter adapter;
    
    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_contacts);
        
        // Set up toolbar
        Toolbar toolbar = findViewById(R.id.toolbar);
        setSupportActionBar(toolbar);
        
        if (getSupportActionBar() != null) {
            getSupportActionBar().setDisplayHomeAsUpEnabled(true);
            getSupportActionBar().setTitle(R.string.nav_contacts);
        }
        
        // Set up views
        recyclerViewContacts = findViewById(R.id.recycler_view_contacts);
        textEmptyContacts = findViewById(R.id.text_empty_contacts);
        progressLoading = findViewById(R.id.progress_loading);
        fabAddContact = findViewById(R.id.fab_add_contact);
        searchView = findViewById(R.id.search_view);
        
        // Set up recycler view
        recyclerViewContacts.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewContacts.setHasFixedSize(true);
        
        adapter = new ContactAdapter(this, this, this);
        recyclerViewContacts.setAdapter(adapter);
        
        // Set up view model
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        
        // Load device contacts
        loadDeviceContacts();
        
        // Set up search view
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }
            
            @Override
            public boolean onQueryTextChange(String newText) {
                adapter.filter(newText);
                return true;
            }
        });
        
        // Set FAB click listener
        fabAddContact.setOnClickListener(v -> {
            showAddContactDialog();
        });
    }
    
    private void loadDeviceContacts() {
        // Check permission first
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CONTACTS) 
                != PackageManager.PERMISSION_GRANTED) {
            requestContactsPermission();
            return;
        }
        
        new Thread(() -> {
            List<User> deviceContacts = new ArrayList<>();
            // Use a HashMap to ensure unique phone numbers
            Map<String, User> uniqueContacts = new HashMap<>();
            
            // Columns to fetch
            String[] projection = new String[]{
                    ContactsContract.CommonDataKinds.Phone._ID,
                    ContactsContract.CommonDataKinds.Phone.NUMBER,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME
            };
            
            // Query device contacts
            try (Cursor cursor = getContentResolver().query(
                    ContactsContract.CommonDataKinds.Phone.CONTENT_URI,
                    projection,
                    null,
                    null,
                    ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME + " ASC")) {
                    
                if (cursor != null) {
                    int idColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone._ID);
                    int numberColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.NUMBER);
                    int nameColumnIndex = cursor.getColumnIndex(ContactsContract.CommonDataKinds.Phone.DISPLAY_NAME);
                    
                    while (cursor.moveToNext()) {
                        long id = cursor.getLong(idColumnIndex);
                        String phoneNumber = cursor.getString(numberColumnIndex);
                        String name = cursor.getString(nameColumnIndex);
                        
                        // Format phone number (remove spaces, dashes, etc.)
                        phoneNumber = phoneNumber.replaceAll("[\\s-()]", "");
                        
                        // Create user object and store in map with phone number as key to ensure uniqueness
                        User user = new User(name, phoneNumber, User.STATUS_KNOWN);
                        user.setId(id);
                        uniqueContacts.put(phoneNumber, user);
                    }
                }
            } catch (Exception e) {
                Log.e(TAG, "Error loading device contacts", e);
            }
            
            // Convert map values to list
            deviceContacts.addAll(uniqueContacts.values());
            
            // Update UI on main thread
            runOnUiThread(() -> {
                progressLoading.setVisibility(View.GONE);
                
                if (deviceContacts.isEmpty()) {
                    textEmptyContacts.setText("No contacts found");
                    textEmptyContacts.setVisibility(View.VISIBLE);
                } else {
                    adapter.setContacts(deviceContacts);
                    textEmptyContacts.setVisibility(View.GONE);
                }
            });
        }).start();
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
    public void onContactClick(User contact) {
        Intent intent = new Intent(this, MessageDetailActivity.class);
        intent.putExtra("phone_number", contact.getPhoneNumber());
        intent.putExtra("contact_name", contact.getName());
        startActivity(intent);
    }
    
    @Override
    public void onContactLongClick(User contact, int position) {
        showContactOptionsDialog(contact);
    }
    
    private void showAddContactDialog() {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.contact_add);
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_contact, null);
        EditText editName = dialogView.findViewById(R.id.edit_contact_name);
        EditText editPhone = dialogView.findViewById(R.id.edit_phone_number);
        Spinner spinnerStatus = dialogView.findViewById(R.id.spinner_status);
        
        builder.setView(dialogView);
        
        builder.setPositiveButton(R.string.action_save, (dialog, which) -> {
            String name = editName.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();
            String status = getStatusFromPosition(spinnerStatus.getSelectedItemPosition());
            
            if (phone.isEmpty()) {
                Toast.makeText(this, "Phone number required", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Check if already stored in our database
            User existingUser = userViewModel.getUserByPhoneNumber(phone);
            
            if (existingUser == null) {
                // Create new user
                User newUser = new User(name, phone, status);
                userViewModel.insert(newUser);
                
                // Add to device contacts too
                if (status.equals(User.STATUS_KNOWN)) {
                    addToDeviceContacts(name, phone);
                }
                
                // Refresh contact list
                loadDeviceContacts();
                
                Toast.makeText(this, "Contact added", Toast.LENGTH_SHORT).show();
            } else {
                Toast.makeText(this, "Contact already exists", Toast.LENGTH_SHORT).show();
            }
        });
        
        builder.setNegativeButton(R.string.action_cancel, null);
        
        builder.show();
    }
    
    private void showContactOptionsDialog(User contact) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        
        String[] options = {
                getString(R.string.contact_edit),
                getString(R.string.contact_delete)
        };
        
        User dbUser = userViewModel.getUserByPhoneNumber(contact.getPhoneNumber());
        boolean isBlocked = dbUser != null && User.STATUS_BLOCKED.equals(dbUser.getStatus());
        
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // Edit contact
                showEditContactDialog(contact);
            } else if (which == 1) {
                // Delete contact
                deleteContact(contact);
            }
        });
        
        builder.show();
    }
    
    private void deleteContact(User contact) {
        // Show confirmation dialog
        new AlertDialog.Builder(this)
                .setTitle("Delete Contact")
                .setMessage("Are you sure you want to delete this contact?")
                .setPositiveButton("Delete", (dialog, which) -> {
                    // Delete from our database if exists
                    User dbUser = userViewModel.getUserByPhoneNumber(contact.getPhoneNumber());
                    if (dbUser != null) {
                        userViewModel.delete(dbUser);
                    }
                    
                    // Refresh list
                    loadDeviceContacts();
                    
                    Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton("Cancel", null)
                .show();
    }
    
    private void showEditContactDialog(User contact) {
        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setTitle(R.string.contact_edit);
        
        View dialogView = LayoutInflater.from(this).inflate(R.layout.dialog_contact, null);
        EditText editName = dialogView.findViewById(R.id.edit_contact_name);
        EditText editPhone = dialogView.findViewById(R.id.edit_phone_number);
        Spinner spinnerStatus = dialogView.findViewById(R.id.spinner_status);
        
        // Set existing values
        editName.setText(contact.getName());
        editPhone.setText(contact.getPhoneNumber());
        
        // Get status from our database
        User dbUser = userViewModel.getUserByPhoneNumber(contact.getPhoneNumber());
        if (dbUser != null) {
            spinnerStatus.setSelection(getPositionFromStatus(dbUser.getStatus()));
        } else {
            spinnerStatus.setSelection(getPositionFromStatus(User.STATUS_KNOWN));
        }
        
        builder.setView(dialogView);
        
        builder.setPositiveButton(R.string.action_save, (dialog, which) -> {
            String name = editName.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();
            String status = getStatusFromPosition(spinnerStatus.getSelectedItemPosition());
            
            if (phone.isEmpty()) {
                Toast.makeText(this, "Phone number required", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Update in our database
            if (dbUser != null) {
                dbUser.setName(name);
                dbUser.setPhoneNumber(phone);
                dbUser.setStatus(status);
                userViewModel.update(dbUser);
            } else {
                User newUser = new User(name, phone, status);
                userViewModel.insert(newUser);
            }
            
            // Refresh list
            loadDeviceContacts();
            
            Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton(R.string.action_cancel, null);
        
        builder.show();
    }
    
    private void addToDeviceContacts(String name, String phoneNumber) {
        try {
            Intent intent = new Intent(ContactsContract.Intents.Insert.ACTION);
            intent.setType(ContactsContract.RawContacts.CONTENT_TYPE);
            intent.putExtra(ContactsContract.Intents.Insert.NAME, name);
            intent.putExtra(ContactsContract.Intents.Insert.PHONE, phoneNumber);
            startActivity(intent);
        } catch (Exception e) {
            Log.e(TAG, "Error adding to device contacts", e);
            Toast.makeText(this, "Failed to add to device contacts", Toast.LENGTH_SHORT).show();
        }
    }
    
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        if (requestCode == 100) {
            if (grantResults.length > 0 && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                loadDeviceContacts();
            } else {
                textEmptyContacts.setText("Contacts permission denied");
                textEmptyContacts.setVisibility(View.VISIBLE);
            }
        }
    }
    
    private String getStatusFromPosition(int position) {
        switch (position) {
            case 0:
                return User.STATUS_KNOWN;
            case 1:
                return User.STATUS_UNKNOWN;
            case 2:
                return User.STATUS_BLOCKED;
            default:
                return User.STATUS_UNKNOWN;
        }
    }
    
    private int getPositionFromStatus(String status) {
        switch (status) {
            case User.STATUS_KNOWN:
                return 0;
            case User.STATUS_UNKNOWN:
                return 1;
            case User.STATUS_BLOCKED:
                return 2;
            default:
                return 1;
        }
    }
    
    private void requestContactsPermission() {
        progressLoading.setVisibility(View.GONE);
        textEmptyContacts.setText("Contacts permission needed");
        textEmptyContacts.setVisibility(View.VISIBLE);
        ActivityCompat.requestPermissions(this, 
                new String[]{Manifest.permission.READ_CONTACTS}, 
                100);
    }
} 