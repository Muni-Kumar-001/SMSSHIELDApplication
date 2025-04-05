package com.example.smsshield.api;

import android.content.Context;
import android.util.Log;

import com.android.volley.DefaultRetryPolicy;
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
    
    // Timeout parameters
    private static final int SOCKET_TIMEOUT_MS = 15000; // 15 seconds
    private static final int MAX_RETRIES = 2;
    private static final float BACKOFF_MULTIPLIER = 1.5f;
    
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
            // Add sender phone number for better analysis
            jsonRequest.put("sender", message.getPhoneNumber());
            
            JsonObjectRequest request = new JsonObjectRequest(
                    Request.Method.POST,
                    API_URL,
                    jsonRequest,
                    response -> {
                        try {
                            // Log raw response for debugging
                            Log.d(TAG, "Raw API response: " + response.toString());
                            
                            // Check if the response contains the is_spam field
                            boolean isSpam = false;
                            String resultMessage = "Unknown result";
                            
                            if (response.has("is_spam")) {
                                isSpam = response.getBoolean("is_spam");
                            } else if (response.has("result")) {
                                // Check if result is a String or JSONObject
                                try {
                                    if (response.get("result") instanceof String) {
                                        // The result is a String like "Spam" or "spam"
                                        String resultStr = response.getString("result").toLowerCase();
                                        isSpam = resultStr.contains("spam");
                                        resultMessage = "Message classified as: " + resultStr;
                                        Log.d(TAG, "Result is a String: " + resultStr);
                                    } else {
                                        // Try as a JSONObject
                                        JSONObject result = response.getJSONObject("result");
                                        if (result.has("is_spam")) {
                                            isSpam = result.getBoolean("is_spam");
                                        } else {
                                            // Handle case when API format has changed
                                            Log.d(TAG, "API response format changed: " + response.toString());
                                        }
                                    }
                                } catch (JSONException e) {
                                    Log.d(TAG, "Error parsing result field: " + e.getMessage() + ", response: " + response.toString());
                                    // Try to infer result from the response string
                                    String respStr = response.toString().toLowerCase();
                                    isSpam = respStr.contains("spam");
                                }
                            } else {
                                // Default to unknown/safe if the field is missing
                                Log.d(TAG, "Missing is_spam field in response: " + response.toString());
                            }
                            
                            // Get message field or default to a generic message
                            if (response.has("message")) {
                                resultMessage = response.getString("message");
                            } else if (response.has("result")) {
                                try {
                                    if (response.get("result") instanceof String) {
                                        // We already set the resultMessage in the earlier check
                                    } else {
                                        // Try to get message from result object
                                        JSONObject resultObj = response.getJSONObject("result");
                                        if (resultObj.has("message")) {
                                            resultMessage = resultObj.getString("message");
                                        }
                                    }
                                } catch (JSONException e) {
                                    // Already handled in the earlier check
                                }
                            }
                            
                            // Update message status in database
                            final String newStatus = isSpam ? Message.STATUS_SPAM : Message.STATUS_SAFE;
                            Log.d(TAG, "Updating message " + message.getId() + " status to: " + newStatus);
                            
                            // Make sure the update completes before returning
                            try {
                                messageRepository.updateMessageStatus(message.getId(), newStatus);
                                // Update the local message object as well for immediate UI reflection
                                message.setStatus(newStatus);
                                Log.d(TAG, "Message status updated successfully");
                            } catch (Exception e) {
                                Log.e(TAG, "Error updating message status", e);
                            }
                            
                            callback.onResult(isSpam, resultMessage);
                        } catch (JSONException e) {
                            callback.onError("Error parsing API response: " + e.getMessage());
                            Log.e(TAG, "Error parsing API response", e);
                        }
                    },
                    error -> {
                        Log.e(TAG, "API request failed", error);
                        // Fall back to local analysis when API fails
                        boolean localResult = performLocalAnalysis(message.getContent());
                        String newStatus = localResult ? Message.STATUS_SPAM : Message.STATUS_SAFE;
                        messageRepository.updateMessageStatus(message.getId(), newStatus);
                        
                        callback.onResult(localResult, "Determined using local analysis (API unavailable)");
                    });
            
            // Set retry policy
            request.setRetryPolicy(new DefaultRetryPolicy(
                    SOCKET_TIMEOUT_MS,
                    MAX_RETRIES,
                    BACKOFF_MULTIPLIER));
            
            requestQueue.add(request);
        } catch (JSONException e) {
            callback.onError("Error creating API request: " + e.getMessage());
            Log.e(TAG, "Error creating API request", e);
        }
    }
    
    /**
     * Simple local analysis to detect potential spam messages.
     * This is a fallback when the API is not available.
     * 
     * @param messageContent The message content to analyze
     * @return true if the message is likely spam, false otherwise
     */
    private boolean performLocalAnalysis(String messageContent) {
        if (messageContent == null || messageContent.isEmpty()) {
            return false;
        }
        
        String content = messageContent.toLowerCase();
        
        // Check for common spam keywords and patterns
        String[] spamIndicators = {
            "congrat", "won", "prize", "lottery", "cash", "claim", 
            "free", "offer", "limited time", "click", "link", "verify", 
            "account", "urgent", "alert", "bank", "credit", "update", 
            "confirm", "password", "verify", "login", "suspended", 
            "unusual activity", "gift card", "bitcoin"
        };
        
        // Count spam indicators
        int spamScore = 0;
        for (String indicator : spamIndicators) {
            if (content.contains(indicator)) {
                spamScore++;
            }
        }
        
        // Check for URLs
        if (content.contains("http://") || content.contains("https://") || 
            content.contains("www.") || content.matches(".*\\.[a-z]{2,}.*")) {
            spamScore += 2;
        }
        
        // Check for unusual characters or formatting
        if (content.contains("$") || content.contains("€") || 
            content.contains("%") || content.contains("!")) {
            spamScore++;
        }
        
        // Calculate threshold based on message length
        int threshold = 2;
        if (content.length() > 50) {
            threshold = 3;  // Require more indicators for longer messages
        }
        
        Log.d(TAG, "Local analysis spam score: " + spamScore + ", threshold: " + threshold);
        return spamScore >= threshold;
    }
} 