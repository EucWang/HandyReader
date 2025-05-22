package com.wxn.bookparser.parser.fb2

import android.content.Context
import android.os.Build
import android.util.Log
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.baseName
import com.anggrayudi.storage.file.openInputStream
import com.wxn.base.bean.Book
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.domain.book.BookWithCover
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.util.FileUtil
import com.wxn.bookparser.util.getCoverPath
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.ByteArrayInputStream
import java.io.InputStream
import java.util.Base64
import java.util.UUID
import javax.inject.Inject

class Fb2FileParser @Inject constructor(val context: Context) : FileParser {

    override suspend fun parse(file: DocumentFile): BookWithCover? {
        return try {
            val inputStream: InputStream? = file.openInputStream(context)
            val title = file.baseName
            innerParse(inputStream, title, file.uri.toString())
        }catch (ex : Exception) {
            null
        }
    }

    override suspend fun parse(cachedFile: CachedFile): BookWithCover? {
        return try {
            val inputStream: InputStream? = cachedFile.openInputStream()
            val title = cachedFile.name.substringBeforeLast(".").trim()
            innerParse(inputStream, title, cachedFile.uri.toString())
        } catch (ex: Exception) {
            null
        }
    }

    private fun innerParse(inputStream: InputStream?, baseName: String, uri: String): BookWithCover? {
        return try {
            val document = inputStream?.use {
                Jsoup.parse(it, null, "", Parser.xmlParser())
            }

            val title = document?.selectFirst("book-title")?.text()?.trim().run {
                if (isNullOrBlank()) {
                    return@run baseName
                }
                this
            }

            var uuid : String = ""
            var version : String = ""
            var date: String = ""
            document?.selectFirst("document-info")?.let { ele ->
                uuid = ele.select("id").text().trim()
                version = ele.select("version").text().trim()
                date = ele.select("date").text().trim()
            }

            var publisher : String = ""
            var isbn : String = ""
            document?.selectFirst("publish-info")?.let { ele ->
                publisher = ele.select("publisher").text().trim()
                isbn = ele.select("isbn").text().trim()
                val year = ele.select("year").text().trim()
                val month = ele.select("month").text().trim()
                val day = ele.select("day").text().trim()

                if (date.isEmpty()) {
                    date = "$year-$month-$day"
                }
            }

            var author : String = ""
            document?.selectFirst("author")?.let { ele ->
                val text = ele.text().trim()
                val nickname = ele.select("nickname").html()
                val firstName = ele.select("first-name").html()
                val lastName = ele.select("last-name").html()
                author = (if (text.isNotBlank()) {
                    text
                } else if (nickname.isNotBlank()) {
                    nickname
                } else if (firstName.isNotBlank() || lastName.isNotBlank()) {
                    "$firstName $lastName"
                } else "").trim()
            }

            val language = document?.selectFirst("lang")?.text()?.trim()

//            val author = document?.selectFirst("author")?.text()?.trim().run {
//                if (isNullOrBlank()) {
//
//                    return@run "" // stringResource(R.string.unknown_author)
//                }
//                this.trim()
//            }

            val description = document?.selectFirst("annotation")?.text()?.trim().run {
                if (isNullOrBlank()) {
                    return@run null
                }
                this
            }

            var coverPath = ""
            document?.selectFirst("coverpage")?.let { ele ->
                val eleImage = ele.select("image")
                val imageRef : String = if (eleImage.hasAttr("xlink:href")) {
                     eleImage.attr("xlink:href")
                } else if (eleImage.hasAttr("href")){
                     eleImage.attr("href")
                } else if (eleImage.hasAttr("l:href")){
                    eleImage.attr("l:href")
                } else { "" }

                Log.d("Fb2FileParser", "innerParse:imageRef=$imageRef")
                if (imageRef.startsWith("#")) {
                    document.selectFirst("binary[id=${imageRef.substring(1)}]")?.let { binary ->
                        val base64Data = binary.text().trim().replace("\\s+", "")  //清理所有空白字符（包括换行、缩进、空格）
                        val inputStream = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                            ByteArrayInputStream(Base64.getDecoder().decode(base64Data))
                        } else {
                            ByteArrayInputStream(android.util.Base64.decode(base64Data, android.util.Base64.DEFAULT))
                        }
                        coverPath  = getCoverPath(context, UUID.randomUUID().toString() + ".jpg")
                        FileUtil.writeStreamToFile(inputStream, coverPath)
                        Log.d("Fb2FileParser", "innerParse::coverPath=$coverPath")
                    }
                }
            }

            BookWithCover(
                book = Book(
                    title = title,
                    author = author,
                    language = language,
                    description = description,
                    scrollIndex = 0,
                    scrollOffset = 0,
                    progress = 0f,
                    filePath = uri,
                    lastOpened = null,
                    category = "",
                    coverImage = coverPath,
                    fileType = "fb2",
                    publisher = publisher,
                    publishDate = date
                ),
                coverImage = coverPath
            )
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}