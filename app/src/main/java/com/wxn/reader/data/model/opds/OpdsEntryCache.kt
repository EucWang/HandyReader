package com.wxn.reader.data.model.opds

import java.util.LinkedHashMap
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class OpdsEntryCache @Inject constructor() {
    private val maxEntries = 50
    private val cache = LinkedHashMap<String, OpdsEntry>(16, 0.75f, true)

    @Synchronized
    fun put(catalogId: Long, entry: OpdsEntry) {
        val key = key(catalogId, entry.id)
        cache[key] = entry
        if (cache.size > maxEntries) {
            val iter = cache.keys.iterator()
            iter.next()
            iter.remove()
        }
    }

    @Synchronized
    fun get(catalogId: Long, entryId: String): OpdsEntry? {
        return cache[key(catalogId, entryId)]
    }

    private fun key(catalogId: Long, entryId: String) = "${catalogId}_$entryId"
}
