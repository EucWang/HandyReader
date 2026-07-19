package com.wxn.reader.data.remote.sync.canonical

import com.wxn.base.bean.sync.HlcTimestamp

/**
 * Canonical Record 公共契约:所有可同步的子表记录(标注/笔记/书签/单词本/书架等)实现此接口。
 *
 * 合并引擎按 [uuid] 做并集匹配,按 [hlc] 做 LWW(同 uuid 取较新者),[deleted] 标记墓碑传播。
 *
 * ★ 同步方案文档:本期首次显式定义(v2.6 仅有隐式契约,各 Record 用 `override` 引用但无 interface 定义块)。
 */
interface SyncRecord {
    /** 跨设备稳定 UUID,运行时由 `UUID.randomUUID().toString()` 生成(8-4-4-4-12 带连字符)。 */
    val uuid: String

    /** 本条记录的 HLC 时间戳。 */
    val hlc: HlcTimestamp

    /** 是否已删除(墓碑),合并时传播。 */
    val deleted: Boolean

    /** 本记录序列化用的 schema 版本(向前/向后兼容用)。 */
    val schemaVersion: Int
}
