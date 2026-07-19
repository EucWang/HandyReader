package com.wxn.reader.data.remote.sync.canonical

/**
 * 同步作用域:决定一次本地写操作要推进哪一档 HLC。
 *
 * - [META]:书籍元数据(标题/作者/封面/语言等)变更。
 * - [USER]:用户对书籍的偏好(评分/收藏/书评/阅读状态)变更。
 * - [READING]:阅读进度(locator/progression/scrollIndex 等)变更。
 * - [ANNOTATION]:子表(标注/笔记/书签/单词本)的 per-row HLC,在各自装饰器路径处理,
 *     仅作枚举占位(子表写方法走 per-row DAO,不进 [com.wxn.reader.data.repository.SyncableBooksRepository.markDirty])。
 * - [SHELF]:书架及书架-书籍关系变更,在 [com.wxn.reader.data.repository.SyncableShelfRepository] 处理。
 * - [ALL]:三档(meta/user/reading)全部推进(`markDirtyAll` 用)。
 *
 * ★ 同步方案文档:一期修正 v2.6 §4.2 漏列的 [SHELF] 档(严重-7)。
 */
enum class SyncScope {
    META,
    USER,
    READING,
    ANNOTATION,
    SHELF,
    ALL;
}
