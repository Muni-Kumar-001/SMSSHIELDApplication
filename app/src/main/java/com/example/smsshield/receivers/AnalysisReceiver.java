package com.example.smsshield.receivers;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.util.Log;

import com.example.smsshield.MainActivity;
import com.example.smsshield.MessageDetailActivity;

/**
 * Receiver for handling message analysis broadcasts.
 * This static receiver is declared in the manifest and will forward
 * the broadcasts to any active activities.
 */
public class AnalysisReceiver extends BroadcastReceiver {
    private static final String TAG = "AnalysisReceiver";
    
    @Override
    public void onReceive(Context context, Intent intent) {
        Log.d(TAG, "Received broadcast: " + intent.getAction());
        
        if (SmsReceiver.ACTION_MESSAGE_ANALYZED.equals(intent.getAction())) {
            // Forward to MainActivity if it's active
            MainActivity.handleMessageAnalyzed(context);
            
            // Forward to MessageDetailActivity if it's active
            MessageDetailActivity.handleMessageAnalyzed(context);
        }
    }
} 