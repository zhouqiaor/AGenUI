package com.amap.agenuiplayground.widget;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import java.util.concurrent.atomic.AtomicReference;

/**
 * Room 数据库 — widget_history 单表。
 *
 * 单例,进程内共享。
 */
@Database(
        entities = {WidgetHistoryEntity.class},
        version = 1,
        exportSchema = false)
public abstract class WidgetHistoryDatabase extends RoomDatabase {

    private static final String DB_NAME = "widget_history.db";
    private static final AtomicReference<WidgetHistoryDatabase> INSTANCE = new AtomicReference<>(null);

    public abstract WidgetHistoryDao historyDao();

    public static WidgetHistoryDatabase getInstance(Context context) {
        WidgetHistoryDatabase existing = INSTANCE.get();
        if (existing != null) {
            return existing;
        }
        synchronized (WidgetHistoryDatabase.class) {
            WidgetHistoryDatabase current = INSTANCE.get();
            if (current != null) {
                return current;
            }
            WidgetHistoryDatabase created = Room.databaseBuilder(
                            context.getApplicationContext(),
                            WidgetHistoryDatabase.class,
                            DB_NAME)
                    .fallbackToDestructiveMigration()
                    .build();
            INSTANCE.set(created);
            return created;
        }
    }
}
