package com.example.smsshield.adapters;

import android.content.Context;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.core.content.ContextCompat;
import androidx.recyclerview.widget.RecyclerView;

import com.example.smsshield.R;
import com.example.smsshield.database.entities.User;

import java.util.ArrayList;
import java.util.List;

public class ContactAdapter extends RecyclerView.Adapter<ContactAdapter.ContactViewHolder> {
    
    private final Context context;
    private final List<User> contacts = new ArrayList<>();
    private final OnContactClickListener clickListener;
    private final OnContactLongClickListener longClickListener;
    
    public interface OnContactClickListener {
        void onContactClick(User contact);
    }
    
    public interface OnContactLongClickListener {
        void onContactLongClick(User contact, int position);
    }
    
    public ContactAdapter(Context context, OnContactClickListener clickListener, OnContactLongClickListener longClickListener) {
        this.context = context;
        this.clickListener = clickListener;
        this.longClickListener = longClickListener;
    }
    
    @NonNull
    @Override
    public ContactViewHolder onCreateViewHolder(@NonNull ViewGroup parent, int viewType) {
        View view = LayoutInflater.from(context).inflate(R.layout.item_contact, parent, false);
        return new ContactViewHolder(view);
    }
    
    @Override
    public void onBindViewHolder(@NonNull ContactViewHolder holder, int position) {
        User currentContact = contacts.get(position);
        
        // Set contact name (or phone number if name is null)
        String contactName = currentContact.getName();
        if (contactName == null || contactName.isEmpty()) {
            contactName = currentContact.getPhoneNumber();
        }
        holder.textContactName.setText(contactName);
        
        // Set phone number
        holder.textPhoneNumber.setText(currentContact.getPhoneNumber());
        
        // Set status and background color
        holder.textContactStatus.setText(getStatusText(currentContact.getStatus()));
        holder.textContactStatus.setBackgroundResource(getStatusBackgroundResource(currentContact.getStatus()));
        holder.textContactStatus.setTextColor(ContextCompat.getColor(context, getStatusTextColor(currentContact.getStatus())));
        
        // Set click listeners
        holder.itemView.setOnClickListener(v -> {
            if (clickListener != null) {
                clickListener.onContactClick(currentContact);
            }
        });
        
        holder.itemView.setOnLongClickListener(v -> {
            if (longClickListener != null) {
                longClickListener.onContactLongClick(currentContact, position);
                return true;
            }
            return false;
        });
    }
    
    @Override
    public int getItemCount() {
        return contacts.size();
    }
    
    public void setContacts(List<User> contacts) {
        this.contacts.clear();
        if (contacts != null) {
            this.contacts.addAll(contacts);
        }
        notifyDataSetChanged();
    }
    
    public User getContactAt(int position) {
        if (position >= 0 && position < contacts.size()) {
            return contacts.get(position);
        }
        return null;
    }
    
    private String getStatusText(String status) {
        switch (status) {
            case User.STATUS_KNOWN:
                return "KNOWN";
            case User.STATUS_UNKNOWN:
                return "UNKNOWN";
            case User.STATUS_BLOCKED:
                return "BLOCKED";
            default:
                return status.toUpperCase();
        }
    }
    
    private int getStatusBackgroundResource(String status) {
        switch (status) {
            case User.STATUS_KNOWN:
                return R.drawable.bg_message_safe;
            case User.STATUS_UNKNOWN:
                return R.drawable.bg_message_unknown;
            case User.STATUS_BLOCKED:
                return R.drawable.bg_message_spam;
            default:
                return R.drawable.bg_message_unknown;
        }
    }
    
    private int getStatusTextColor(String status) {
        return android.R.color.black;
    }
    
    static class ContactViewHolder extends RecyclerView.ViewHolder {
        private final TextView textContactName;
        private final TextView textPhoneNumber;
        private final TextView textContactStatus;
        
        public ContactViewHolder(@NonNull View itemView) {
            super(itemView);
            textContactName = itemView.findViewById(R.id.text_contact_name);
            textPhoneNumber = itemView.findViewById(R.id.text_phone_number);
            textContactStatus = itemView.findViewById(R.id.text_contact_status);
        }
    }
} 