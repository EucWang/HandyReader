package com.wxn.reader.data.model.backup

import com.wxn.reader.data.remote.sync.canonical.BookShelfRelationRecord
import com.wxn.reader.data.remote.sync.canonical.ShelfRecord
import kotlinx.serialization.Serializable

/** shelves.json 结构:书架列表 + 关系列表。 */
@Serializable
data class ShelvesFile(
    val shelves: List<ShelfRecord>,
    val relations: List<BookShelfRelationRecord>,
)
