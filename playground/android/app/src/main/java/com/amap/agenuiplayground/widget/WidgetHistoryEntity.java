package com.amap.agenuiplayground.widget;

import androidx.room.Entity;
import androidx.room.Index;
import androidx.room.PrimaryKey;

/**
 * Widget 生成历史记录实体。
 *
 * 字段:
 * - id          自增主键
 * - prompt      用户输入 prompt(截断到 200 字)
 * - a2uiJson    LLM 输出的完整内容(截断到 5000 字)
 * - timestamp   记录时间戳(毫秒)
 * - latencyMs   生成耗时(毫秒)
 * - success     是否合法 A2UI JSON
 * - widgetId    对应的 appWidgetId
 */
@Entity(
        tableName = "widget_history",
        indices = {@Index("timestamp"), @Index("success")})
public class WidgetHistoryEntity {

    @PrimaryKey(autoGenerate = true)
    public long id;

    public String prompt;

    public String a2uiJson;

    public long timestamp;

    public long latencyMs;

    public boolean success;

    public int widgetId;

    /** 用于 UI 展示的格式化时间,冗余存储避免每次格式化 */
    public String timeFormatted;
}
