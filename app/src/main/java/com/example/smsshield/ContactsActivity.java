package com.example.smsshield;

import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.widget.SearchView;
import androidx.appcompat.widget.Toolbar;
import androidx.lifecycle.ViewModelProvider;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smsshield.adapters.ContactAdapter;
import com.example.smsshield.database.entities.User;
import com.example.smsshield.viewmodel.MessageViewModel;
import com.example.smsshield.viewmodel.UserViewModel;
import com.google.android.material.floatingactionbutton.FloatingActionButton;

import java.util.Arrays;

public class ContactsActivity extends AppCompatActivity 
        implements ContactAdapter.OnContactClickListener, ContactAdapter.OnContactLongClickListener {
    
    private RecyclerView recyclerViewContacts;
    private TextView textEmptyContacts;
    private FloatingActionButton fabAddContact;
    private SearchView searchView;
    
    private UserViewModel userViewModel;
    private MessageViewModel messageViewModel;
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
        fabAddContact = findViewById(R.id.fab_add_contact);
        searchView = findViewById(R.id.search_view);
        
        // Set up recycler view
        recyclerViewContacts.setLayoutManager(new LinearLayoutManager(this));
        recyclerViewContacts.setHasFixedSize(true);
        
        adapter = new ContactAdapter(this, this, this);
        recyclerViewContacts.setAdapter(adapter);
        
        // Set up view models
        userViewModel = new ViewModelProvider(this).get(UserViewModel.class);
        messageViewModel = new ViewModelProvider(this).get(MessageViewModel.class);
        
        // Observe contacts
        userViewModel.getSearchResults().observe(this, users -> {
            adapter.setContacts(users);
            
            if (users == null || users.isEmpty()) {
                recyclerViewContacts.setVisibility(View.GONE);
                textEmptyContacts.setVisibility(View.VISIBLE);
            } else {
                recyclerViewContacts.setVisibility(View.VISIBLE);
                textEmptyContacts.setVisibility(View.GONE);
            }
        });
        
        // Set up search view
        searchView.setOnQueryTextListener(new SearchView.OnQueryTextListener() {
            @Override
            public boolean onQueryTextSubmit(String query) {
                return false;
            }
            
            @Override
            public boolean onQueryTextChange(String newText) {
                userViewModel.setSearchQuery(newText);
                return true;
            }
        });
        
        // Set FAB click listener
        fabAddContact.setOnClickListener(v -> {
            showAddContactDialog();
        });
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
            
            User user = new User(phone, name, status);
            userViewModel.insert(user);
            Toast.makeText(this, "Contact added", Toast.LENGTH_SHORT).show();
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
        
        if (User.STATUS_BLOCKED.equals(contact.getStatus())) {
            options = Arrays.copyOf(options, options.length + 1);
            options[options.length - 1] = getString(R.string.action_unblock);
        } else {
            options = Arrays.copyOf(options, options.length + 1);
            options[options.length - 1] = getString(R.string.action_block);
        }
        
        builder.setItems(options, (dialog, which) -> {
            if (which == 0) {
                // Edit contact
                showEditContactDialog(contact);
            } else if (which == 1) {
                // Delete contact
                userViewModel.delete(contact);
                Toast.makeText(this, "Contact deleted", Toast.LENGTH_SHORT).show();
            } else if (which == 2) {
                // Block/Unblock
                if (User.STATUS_BLOCKED.equals(contact.getStatus())) {
                    contact.setStatus(User.STATUS_KNOWN);
                    userViewModel.update(contact);
                    Toast.makeText(this, "Contact unblocked", Toast.LENGTH_SHORT).show();
                } else {
                    contact.setStatus(User.STATUS_BLOCKED);
                    userViewModel.update(contact);
                    Toast.makeText(this, "Contact blocked", Toast.LENGTH_SHORT).show();
                }
            }
        });
        
        builder.show();
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
        spinnerStatus.setSelection(getPositionFromStatus(contact.getStatus()));
        
        builder.setView(dialogView);
        
        builder.setPositiveButton(R.string.action_save, (dialog, which) -> {
            String name = editName.getText().toString().trim();
            String phone = editPhone.getText().toString().trim();
            String status = getStatusFromPosition(spinnerStatus.getSelectedItemPosition());
            
            if (phone.isEmpty()) {
                Toast.makeText(this, "Phone number required", Toast.LENGTH_SHORT).show();
                return;
            }
            
            // Update contact
            contact.setName(name);
            contact.setStatus(status);
            
            if (!phone.equals(contact.getPhoneNumber())) {
                // Phone number changed, create a new contact and delete the old one
                userViewModel.delete(contact);
                User newContact = new User(phone, name, status);
                userViewModel.insert(newContact);
            } else {
                // Just update the existing contact
                userViewModel.update(contact);
            }
            
            Toast.makeText(this, "Contact updated", Toast.LENGTH_SHORT).show();
        });
        
        builder.setNegativeButton(R.string.action_cancel, null);
        
        builder.show();
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
        if (User.STATUS_KNOWN.equals(status)) {
            return 0;
        } else if (User.STATUS_UNKNOWN.equals(status)) {
            return 1;
        } else if (User.STATUS_BLOCKED.equals(status)) {
            return 2;
        }
        return 1; // Default to unknown
    }
} 