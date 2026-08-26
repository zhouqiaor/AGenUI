package com.amap.agenuiplayground.widget;

import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.Query;

import java.util.List;

/**
 * Widget 历史 DAO。
 */
@Dao
public interface WidgetHistoryDao {

    /**
     * 插入一条历史记录。
     */
    @Insert
    long insert(WidgetHistoryEntity entity);

    /**
     * 查询最近 N 条记录(按时间倒序)。含全部字段以便 Repository 组装摘要。
     */
    @Query("SELECT * FROM widget_history ORDER BY timestamp DESC LIMIT :limit")
    List<WidgetHistoryEntity> observeRecent(int limit);

    /**
     * 按关键词搜索 prompt(按时间倒序,最多 50 条)。
     */
    @Query("SELECT * FROM widget_history WHERE prompt LIKE '%' || :keyword || '%' " +
            "ORDER BY timestamp DESC LIMIT 50")
    List<WidgetHistoryEntity> search(String keyword);

    /**
     * 取最近一条成功的 a2uiJson(用于断网缓存)。
     */
    @Query("SELECT a2uiJson FROM widget_history WHERE success = 1 " +
            "ORDER BY timestamp DESC LIMIT 1")
    String getLastSuccessfulJson();

    /**
     * 清空历史。
     */
    @Query("DELETE FROM widget_history")
    void clear();

    /**
     * 删除超出容量的旧记录,保留最近 maxRecords 条。
     */
    @Query("DELETE FROM widget_history WHERE id NOT IN " +
            "(SELECT id FROM widget_history ORDER BY timestamp DESC LIMIT :maxRecords)")
    void trimOld(int maxRecords);
}
