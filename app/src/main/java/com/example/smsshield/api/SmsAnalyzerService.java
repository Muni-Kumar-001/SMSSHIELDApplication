package com.example.smsshield.api;

import android.content.Context;
import android.util.Log;

import com.android.volley.Request;
import com.android.volley.RequestQueue;
import com.android.volley.Response;
import com.android.volley.VolleyError;
import com.android.volley.toolbox.JsonObjectRequest;
import com.android.volley.toolbox.Volley;
import com.example.smsshield.database.entities.Message;
import com.example.smsshield.repository.MessageRepository;

import org.json.JSONException;
import org.json.JSONObject;

public class SmsAnalyzerService {
    private static final String TAG = "SmsAnalyzerService";
    private static final String API_URL = "https://smsshieldbackend.onrender.com/analyze";
    
    private final RequestQueue requestQueue;
    private final MessageRepository messageRepository;
    
    public SmsAnalyzerService(Context context, MessageRepository messageRepository) {
        this.requestQueue = Volley.newRequestQueue(context);
        this.messageRepository = messageRepository;
    }
    
    public interface AnalysisCallback {
        void onResult(boolean isSpam, String message);
        void onError(String error);
    }
    
    public void analyzeMessage(Message message, AnalysisCallback callback) {
        try {
            JSONObject jsonRequest = new JSONObject();
            jsonRequest.put("message", message.getContent());
//            jsonRequest.put("sender", message.getPhoneNumber());
            
            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    API_URL,
                    jsonRequest,
                    response -> {
                        try {
                            boolean isSpam = response.getBoolean("is_spam");
                            String resultMessage = response.getString("message");
                            
                            // Update message status in database
                            String newStatus = isSpam ? Message.STATUS_SPAM : Message.STATUS_SAFE;
                            messageRepository.updateMessageStatus(message.getId(), newStatus);
                            
                            callback.onResult(isSpam, resultMessage);
                        } catch (JSONException e) {
                            callback.onError("Error parsing API response: " + e.getMessage());
                            Log.e(TAG, "Error parsing API response", e);
                        }
                    },
                    error -> {
                        callback.onError("API request failed: " + error.getMessage());
                        Log.e(TAG, "API request failed", error);
                    });
            
            requestQueue.add(request);
        } catch (JSONException e) {
            callback.onError("Error creating API request: " + e.getMessage());
            Log.e(TAG, "Error creating API request", e);
        }
    }
} 