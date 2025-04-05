package com.example.smsshield.receivers;

import android.Manifest;
import android.app.NotificationChannel;
import android.app.NotificationManager;
import android.app.PendingIntent;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.os.Bundle;
import android.provider.Telephony;
import android.telephony.SmsMessage;
import android.util.Log;

import androidx.core.app.ActivityCompat;
import androidx.core.app.NotificationCompat;
import androidx.core.app.NotificationManagerCompat;

import com.example.smsshield.MessageDetailActivity;
import com.example.smsshield.R;
import com.example.smsshield.api.SmsAnalyzerService;
import com.example.smsshield.database.entities.Message;
import com.example.smsshield.database.entities.User;
import com.example.smsshield.repository.MessageRepository;
import com.example.smsshield.repository.UserRepository;
import com.example.smsshield.receivers.AnalysisReceiver;

import java.util.Date;
import java.util.concurrent.Executor;
import java.util.concurrent.Executors;

public class SmsReceiver extends BroadcastReceiver {
    private static final String TAG = "SmsReceiver";
    private static final String CHANNEL_ID = "sms_shield_channel";
    private static final int NOTIFICATION_ID = 1001;
    private final Executor executor = Executors.newSingleThreadExecutor();
    
    // Action for broadcasting when message analysis is complete - explicitly app-specific
    public static final String ACTION_MESSAGE_ANALYZED = "com.example.smsshield.action.MESSAGE_ANALYZED";

    @Override
    public void onReceive(Context context, Intent intent) {
        if (Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) {
            Bundle bundle = intent.getExtras();
            if (bundle != null) {
                SmsMessage[] messages = Telephony.Sms.Intents.getMessagesFromIntent(intent);
                if (messages != null && messages.length > 0) {
                    processMessages(context, messages);
                }
            }
        }
    }

    private void processMessages(Context context, SmsMessage[] messages) {
        StringBuilder fullMessage = new StringBuilder();
        String sender = messages[0].getOriginatingAddress();

        for (SmsMessage message : messages) {
            fullMessage.append(message.getMessageBody());
        }

        final String messageBody = fullMessage.toString();
        Log.d(TAG, "SMS from " + sender + ": " + messageBody);

        // Initialize repositories
        MessageRepository messageRepository = new MessageRepository(context);
        UserRepository userRepository = new UserRepository(context);

        executor.execute(() -> {
            // Check if sender exists in our user database
            User user = userRepository.getUserByPhoneNumber(sender);
            long userId;
            
            if (user == null) {
                // Create a new unknown user
                User newUser = new User(sender, sender, User.STATUS_UNKNOWN);
                userId = userRepository.insert(newUser);
            } else {
                userId = user.getId();
                
                // If the user is blocked, we do not process the message
                if (User.STATUS_BLOCKED.equals(user.getStatus())) {
                    Log.d(TAG, "Message from blocked user ignored: " + sender);
                    return;
                }
            }

            // Create and save the message
            Message smsMessage = new Message(
                    userId,
                    messageBody,
                    new Date().getTime(),
                    true,
                    Message.STATUS_UNCHECKED,
                    sender
            );
            
            long messageId = messageRepository.insert(smsMessage);
            smsMessage.setId(messageId);

            // Update the messageId in the smsMessage object
            sendNotification(context, sender, messageBody);

            // Analyze the message with the SMS analyzer service
            SmsAnalyzerService analyzerService = new SmsAnalyzerService(context, messageRepository);
            analyzerService.analyzeMessage(smsMessage, new SmsAnalyzerService.AnalysisCallback() {
                @Override
                public void onResult(boolean isSpam, String resultMessage) {
                    Log.d(TAG, "Analysis result: " + (isSpam ? "SPAM" : "SAFE") + " - " + resultMessage);
                    
                    // If it's spam, update notification
                    if (isSpam) {
                        sendSpamNotification(context, sender, messageBody);
                    }
                    
                    // Broadcast that message analysis is complete - use explicit intent
                    Intent analysisCompleteIntent = new Intent(context, AnalysisReceiver.class);
                    analysisCompleteIntent.setAction(ACTION_MESSAGE_ANALYZED);
                    analysisCompleteIntent.putExtra("message_id", messageId);
                    analysisCompleteIntent.putExtra("is_spam", isSpam);
                    context.sendBroadcast(analysisCompleteIntent);
                }

                @Override
                public void onError(String error) {
                    Log.e(TAG, "Analysis error: " + error);
                }
            });
        });
    }

    private void sendNotification(Context context, String sender, String messageBody) {
        createNotificationChannel(context);
        
        Intent intent = new Intent(context, MessageDetailActivity.class);
        intent.putExtra("phone_number", sender);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_message)
                .setContentTitle("New message from " + sender)
                .setContentText(messageBody)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(NOTIFICATION_ID, builder.build());
        } else {
            Log.w(TAG, "Notification permission not granted");
        }
    }

    private void sendSpamNotification(Context context, String sender, String messageBody) {
        createNotificationChannel(context);
        
        Intent intent = new Intent(context, MessageDetailActivity.class);
        intent.putExtra("phone_number", sender);
        intent.setFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        
        PendingIntent pendingIntent = PendingIntent.getActivity(
                context, 0, intent, PendingIntent.FLAG_UPDATE_CURRENT | PendingIntent.FLAG_IMMUTABLE);

        NotificationCompat.Builder builder = new NotificationCompat.Builder(context, CHANNEL_ID)
                .setSmallIcon(R.drawable.ic_warning)
                .setContentTitle("⚠️ SPAM detected from " + sender)
                .setContentText(messageBody)
                .setPriority(NotificationCompat.PRIORITY_HIGH)
                .setContentIntent(pendingIntent)
                .setAutoCancel(true);

        NotificationManagerCompat notificationManager = NotificationManagerCompat.from(context);
        
        if (ActivityCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) 
                == PackageManager.PERMISSION_GRANTED) {
            notificationManager.notify(NOTIFICATION_ID + 1, builder.build());
        } else {
            Log.w(TAG, "Notification permission not granted");
        }
    }

    private void createNotificationChannel(Context context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            CharSequence name = "SMS Shield Notifications";
            String description = "Notifications for incoming messages";
            int importance = NotificationManager.IMPORTANCE_HIGH;
            
            NotificationChannel channel = new NotificationChannel(CHANNEL_ID, name, importance);
            channel.setDescription(description);
            
            NotificationManager notificationManager = context.getSystemService(NotificationManager.class);
            notificationManager.createNotificationChannel(channel);
        }
    }
} 