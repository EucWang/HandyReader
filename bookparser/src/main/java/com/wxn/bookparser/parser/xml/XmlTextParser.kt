package com.wxn.bookparser.parser.xml

import android.util.Log
import com.wxn.bookparser.TextParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.domain.reader.ReaderText
import com.wxn.bookparser.parser.base.DocumentParser
import kotlinx.coroutines.yield
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import javax.inject.Inject


private const val XML_TAG = "XML Parser"

class XmlTextParser @Inject constructor(
    private val documentParser: DocumentParser
) : TextParser {

    override suspend fun parse(cachedFile: CachedFile): List<ReaderText> {
        Log.i(XML_TAG, "Started XML parsing: ${cachedFile.name}.")

        return try {
            val readerText = cachedFile.openInputStream()?.use { stream ->
                documentParser.parseDocument(Jsoup.parse(stream, null, "", Parser.xmlParser()))
            }

            yield()

            if (
                readerText.isNullOrEmpty() ||
                readerText.filterIsInstance<ReaderText.Text>().isEmpty() ||
                readerText.filterIsInstance<ReaderText.Chapter>().isEmpty()
            ) {
                Log.e(XML_TAG, "Could not extract text from XML.")
                return emptyList()
            }

            Log.i(XML_TAG, "Successfully finished XML parsing.")
            readerText
        } catch (e: Exception) {
            e.printStackTrace()
            emptyList()
        }
    }
}