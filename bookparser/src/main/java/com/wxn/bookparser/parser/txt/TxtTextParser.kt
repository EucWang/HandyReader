package com.wxn.bookparser.parser.txt

import android.util.Log
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.domain.reader.ReaderText
import com.wxn.bookparser.exts.clearAllMarkdown
import com.wxn.bookparser.parser.base.MarkdownParser
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.yield
import javax.inject.Inject


private const val TXT_TAG = "TXT Parser"

class TxtTextParser @Inject constructor(
    private val markdownParser: MarkdownParser
) : TextParser {

    override suspend fun parse(cachedFile: CachedFile): List<ReaderText> {
        Log.i(TXT_TAG, "Started TXT parsing: ${cachedFile.name}.")

        return try {
            val readerText = mutableListOf<ReaderText>()
            var chapterAdded = false

            withContext(Dispatchers.IO) {
                cachedFile.openInputStream()?.bufferedReader()?.use { reader ->
                    reader.forEachLine { line ->
                        if (line.isNotBlank()) {
                            when (line) {
                                "***", "---" -> readerText.add(
                                    ReaderText.Separator
                                )

                                else -> {
                                    if (!chapterAdded && line.clearAllMarkdown().isNotBlank()) {
                                        readerText.add(
                                            0, ReaderText.Chapter(
                                                title = line.clearAllMarkdown(),
                                                nested = false
                                            )
                                        )
                                        chapterAdded = true
                                    } else readerText.add(
                                        ReaderText.Text(
                                            line = markdownParser.parse(line)
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            }

            yield()

            if (
                readerText.filterIsInstance<ReaderText.Text>().isEmpty() ||
                readerText.filterIsInstance<ReaderText.Chapter>().isEmpty()
            ) {
                Log.e(TXT_TAG, "Could not extract text from TXT.")
                return emptyList()
            }

            Log.i(TXT_TAG, "Successfully finished TXT parsing.")
            readerText
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}