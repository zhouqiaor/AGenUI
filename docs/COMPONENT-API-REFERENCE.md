# AGenUI 组件 API 参考

本文档覆盖 AGenUI v0.9 协议的 22 个内置组件。

## 目录

1. [Button](#button)
2. [Text](#text)
3. [Card](#card)
4. [Modal](#modal)
5. [List](#list)
6. [Slider](#slider)
7. [CheckBox](#checkbox)
8. [Icon](#icon)
9. [Image](#image)
10. [Row](#row)
11. [Column](#column)
12. [Tabs](#tabs)
13. [Table](#table)
14. [RichText](#richtext)
15. [TextField](#textfield)
16. [Divider](#divider)
17. [Carousel](#carousel)
18. [Web](#web)
19. [Video](#video)
20. [AudioPlayer](#audioplayer)
21. [DateTimeInput](#datetimeinput)
22. [ChoicePicker](#choicepicker)

---

## 通用属性

所有组件共享以下基础属性:

| 属性 | 类型 | 必填 | 描述 |
|------|------|------|------|
| id | string | 是 | 组件唯一标识 |
| type | string | 是 | 组件类型 (如 "Text", "Button") |
| properties | object | 否 | 组件特定属性 |
| styles | object | 否 | CSS-like 样式 (width, height, margin, padding, flexDirection 等) |
| children | array | 否 | 子组件定义数组 |
| parentId | string | 否 | 父组件 ID (用于流式追加) |
| insertIndex | int | 否 | 在父组件中的插入位置 |

## 通用样式

| 样式 | 类型 | 描述 |
|------|------|------|
| width | string/number | 宽度 ("100%", "200px", 200) |
| height | string/number | 高度 |
| margin | string | 外边距 |
| padding | string | 内边距 |
| flexDirection | string | row / column |
| justifyContent | string | flex-start / center / flex-end / space-between |
| alignItems | string | flex-start / center / flex-end / stretch |
| backgroundColor | string | 背景颜色 |
| borderRadius | string | 圆角 |
| borderWidth | string | 边框宽度 |
| borderColor | string | 边框颜色 |
| opacity | number | 透明度 0-1 |
| flex | number | 弹性布局权重 |
| position | string | absolute / relative |
| top/left | string | 绝对定位偏移 |
| zIndex | int | 层叠顺序 |

---

## Button

用户交互按钮组件。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| text | string | 按钮文本 |
| action | object | 点击事件 (type, surfaceId, data) |
| disabled | boolean | 是否禁用 |

### 示例
```json
{
  "id": "submit-btn",
  "type": "Button",
  "properties": {
    "text": "Submit",
    "action": {"type": "updateDataModel", "surfaceId": "main", "data": {"submitted": true}}
  }
}
```

## Text

文本显示组件。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| text | string | 文本内容 |
| numberOfLines | int | 最大行数 |

### 样式
| 样式 | 描述 |
|------|------|
| fontSize | 字号 |
| fontWeight | bold/normal |
| color | 文字颜色 |
| textAlign | left/center/right |
| lineHeight | 行高 |
| letterSpacing | 字间距 |

## Card

卡片容器组件，支持阴影和圆角。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| elevation | int | 阴影高度 |

## Modal

模态对话框。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| visible | boolean | 是否可见 |
| animationType | string | fade/slide/none |

## List

列表组件，支持虚拟化滚动 (R29: 垂直+水平统一 RecyclerView)。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| direction | string | vertical (默认) / horizontal |
| itemTemplate | object | 列表项模板 (支持数据绑定) |

## Slider

滑块输入。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| min | number | 最小值 |
| max | number | 最大值 |
| value | number | 当前值 |
| step | number | 步长 |
| label | string | 标签 |

## CheckBox

复选框。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| checked | boolean | 是否选中 |
| label | string | 标签 |
| errorText | string | 错误提示 |

## Icon

图标组件。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| name | string | 图标名称 |
| size | number | 图标大小 |
| color | string | 图标颜色 |

## Image

图片组件。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| src | string | 图片 URL |
| mode | string | aspectFill/aspectFit/center |
| placeholder | string | 占位图 URL |

## Row

水平布局容器 (flexDirection: row 的快捷方式)。

## Column

垂直布局容器 (flexDirection: column 的快捷方式)。

## Tabs

标签页组件。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| selectedIndex | int | 当前选中索引 |
| tabs | array | 标签配置 [{title: string}] |

## Table

表格组件。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| columns | array | 列配置 [{key, title}] |
| dataKey | string | 数据源路径 |

## RichText

富文本组件。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| segments | array | 文本段 [{type, text, href?}] |

## TextField

文本输入框。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| placeholder | string | 占位文字 |
| value | string | 当前值 |
| maxLength | int | 最大长度 |
| type | string | text/password/number/email |

## Divider

分隔线组件。

## Carousel

轮播图组件。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| autoPlay | boolean | 自动播放 |
| interval | int | 切换间隔(ms) |

## Web

WebView 嵌入组件。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| url | string | 网页 URL |

## Video

视频播放组件。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| src | string | 视频 URL |
| autoplay | boolean | 自动播放 |
| controls | boolean | 显示控制栏 |

## AudioPlayer

音频播放组件。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| src | string | 音频 URL |
| autoplay | boolean | 自动播放 |

## DateTimeInput

日期时间选择器。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| mode | string | date/time/datetime |
| value | string | 当前值 (ISO 格式) |

## ChoicePicker

选择器组件。

### 特有属性
| 属性 | 类型 | 描述 |
|------|------|------|
| options | array | 选项列表 [{label, value}] |
| selectedValue | string | 选中值 |
