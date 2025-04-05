# SmsShield Application

## Overview
SmsShield is an Android application designed to help users manage their SMS messages with enhanced security features. The app allows users to read, analyze, and categorize SMS messages to protect against spam and unwanted messages.

## Key Features

### Message Management
- View all messages with clear timestamps
- Date separators show when conversations span multiple days
- Real-time message delivery with delivery confirmation
- Delete messages from both the app and Android's SMS storage

### Contact Management
- Add, edit, and manage contacts
- Block unwanted senders
- Contacts are displayed with name if saved, but functionality uses phone numbers
- Contacts with multiple messages are properly grouped

### Message Analysis
- Categorize messages as Safe, Spam, or Unchecked
- Messages are color-coded based on their status
- Analyze incoming messages for potential threats

### User Interface
- Clean, intuitive interface with Material Design principles
- Navigation drawer for easy access to contacts
- Detailed message view with conversation history
- Real-time timestamps for all messages

## Permissions Required
- READ_SMS: To read existing SMS messages
- RECEIVE_SMS: To process incoming SMS messages
- SEND_SMS: To send outgoing SMS messages
- READ_CONTACTS: To access contact information
- POST_NOTIFICATIONS: To show notifications for new messages

## Usage Tips
- Tap on a message to open the conversation
- Long-press on a message for options (delete, mark as spam/safe)
- Use the navigation drawer to switch between messages and contacts
- Use the + button to add new contacts
- Outgoing messages are automatically marked as safe 