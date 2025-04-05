package com.example.smsshield.database;

import android.content.Context;

import androidx.annotation.NonNull;
import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;
import androidx.room.TypeConverters;
import androidx.sqlite.db.SupportSQLiteDatabase;

import com.example.smsshield.database.dao.MessageDao;
import com.example.smsshield.database.dao.UserDao;
import com.example.smsshield.database.entities.Message;
import com.example.smsshield.database.entities.User;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Database(entities = {User.class, Message.class}, version = 1, exportSchema = false)
@TypeConverters({Converters.class})
public abstract class SmsShieldDatabase extends RoomDatabase {

    public abstract UserDao userDao();
    public abstract MessageDao messageDao();

    private static volatile SmsShieldDatabase INSTANCE;
    private static final int NUMBER_OF_THREADS = 4;
    public static final ExecutorService databaseWriteExecutor = Executors.newFixedThreadPool(NUMBER_OF_THREADS);

    public static SmsShieldDatabase getDatabase(final Context context) {
        if (INSTANCE == null) {
            synchronized (SmsShieldDatabase.class) {
                if (INSTANCE == null) {
                    INSTANCE = Room.databaseBuilder(
                            context.getApplicationContext(),
                            SmsShieldDatabase.class,
                            "sms_shield_database")
                            .addCallback(sRoomDatabaseCallback)
                            .build();
                }
            }
        }
        return INSTANCE;
    }

    private static RoomDatabase.Callback sRoomDatabaseCallback = new RoomDatabase.Callback() {
        @Override
        public void onCreate(@NonNull SupportSQLiteDatabase db) {
            super.onCreate(db);

            databaseWriteExecutor.execute(() -> {
                // Populate the database in the background if needed
                // For example, add default system contacts or message categories
            });
        }
    };
} 