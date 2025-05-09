package com.wxn.bookparser.parser.epub

import android.content.Context
import android.net.Uri
import android.util.Log
import androidx.compose.ui.res.stringResource
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.baseName
import com.anggrayudi.storage.file.getAbsolutePath
import com.anggrayudi.storage.file.toRawFile
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.R
import com.wxn.bookparser.domain.book.Book
import com.wxn.bookparser.domain.book.BookWithCover
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.util.FileUtil
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.jsoup.Jsoup
import java.io.File
import java.util.UUID
import java.util.zip.ZipFile
import javax.inject.Inject
import kotlin.text.replace

/**
 * EPUB 文件结构，本质是一个zip压缩文件
mimetype
│
├─META-INF
│      container.xml
│
└─OEBPS
│  chapter0001.xhtml
│  chapter0002.xhtml
│  chapter0003.xhtml
│  chapter0004.xhtml
│  chapter0005.xhtml
│  chapter0006.xhtml
|  ...
│  content.opf  必须
│  toc.ncx      必须
│
└─images
39655.jpg
39656.jpg
39657.jpg
...
固定文件：
------------------------------------------------------------------------
mimetype
mimetype是一个文本文件，内容固定为:application/epub+zip
------------------------------------------------------------------------
META-INF/container.xml
其中rootfile的属性full-path指定了此书的OPF文件路径。

<?xml version="1.0" encoding="UTF-8"?>
<container xmlns="urn:oasis:names:tc:opendocument:xmlns:container" version="1.0">
<rootfiles>
<rootfile full-path="OEBPS/content.opf" media-type="application/oebps-package+xml"/>
</rootfiles>
</container>
------------------------------------------------------------------------
OPF： Open Package Format(OPF)，即包文件格式，其主要功能是用于组织 OPS 文档和提供相应的导航机制，并形成一个开放式的基于 XML 的打包文档，该文档的后缀名为 “.opf” 。

<?xml version="1.0" encoding="UTF-8"?>
<package xmlns="http://www.idpf.org/2007/opf" unique-identifier="bookId" version="2.0">
<metadata xmlns:dc="http://purl.org/dc/elements/1.1/" xmlns:opf="http://www.idpf.org/2007/opf">
<dc:identifier id="bookId">urn:uuid:9bfb698f-dfa3-45ca-bea4-d0fbc2ead4f3</dc:identifier>
<dc:language>en</dc:language>
<dc:title>第一卷 虚伪的王国</dc:title>
<meta content="cover-image" name="cover"/>
</metadata>
<manifest>
<item href="toc.ncx" id="ncx" media-type="application/x-dtbncx+xml"/>
<item href="chapter0001.xhtml" id="chapter0001.xhtml" media-type="application/xhtml+xml"/>
<item href="chapter0002.xhtml" id="chapter0002.xhtml" media-type="application/xhtml+xml"/>
<item href="chapter0003.xhtml" id="chapter0003.xhtml" media-type="application/xhtml+xml"/>
<item href="chapter0004.xhtml" id="chapter0004.xhtml" media-type="application/xhtml+xml"/>
<item href="chapter0005.xhtml" id="chapter0005.xhtml" media-type="application/xhtml+xml"/>
<item href="chapter0006.xhtml" id="chapter0006.xhtml" media-type="application/xhtml+xml"/>
...
<item href="images/39655.jpg" id="cover-image" media-type="image/jpeg"/>
</manifest>
<spine toc="ncx">
<itemref idref="chapter0001.xhtml"/>
<itemref idref="chapter0002.xhtml"/>
<itemref idref="chapter0003.xhtml"/>
<itemref idref="chapter0004.xhtml"/>
<itemref idref="chapter0005.xhtml"/>
<itemref idref="chapter0006.xhtml"/>
...
</spine>
</package>
--------------------------------------
metadata EPUB文件元数据：
dc:identifier   书本唯一标识,这个字段一般包括 ISBN 或者 Library of Congress 编号；也可以使用 URL 或者随机生成的唯一用户 ID。
dc:language     书本使用的语言
dc:title        书名
meta:           设置的书的封面
manifest 整本书的清单文件，一般会列出ncx文件和小说正文文件以及封面。ncx文件是必须的，它定义了书籍的目录，具体格式查看下文。封面是可选的
item            清单文件项目
href        文件的相对路径
id          项目ID
media-type  文件的MIME类型
正文网页文件 一般格式都为XHTML，可以使用样式图片音频各种各样的资源。
指定封面图片：
1. metadata中包含name为cover的meta标签    <meta content="cover-image" name="cover"/>
2. manifest中包含id为上述meta的content属性的item
通过设置item的href即可指定封面路径
<item href="images/39655.jpg" id="cover-image" media-type="image/jpeg"/>
spine 翻译成中文就是书脊，通过引用的顺序来指定阅读顺序。
itemref         引用的manifest中的文件，只需要引入正文网页文件。
idref       需要与manifest中itme的id对应 代表此文件
linear      表明该项是作为线性阅读顺序中的一项，与先后顺序无关，默认为yes

------------------------------------------------------------------------
toc.ncx NCX是Navigation Content eXtended的缩写，用于表示本书的目录。NCX 中有章节内容，OPF spine 主要描述章节顺序。NCX 更详细。

<?xml version="1.0" encoding="UTF-8" standalone="no"?>
<ncx xmlns="http://www.daisy.org/z3986/2005/ncx/">
<head>
<meta content="urn:uuid:9bfb698f-dfa3-45ca-bea4-d0fbc2ead4f3" name="dtb:uid"/>
<meta content="1" name="dtb:depth"/>
<meta content="0" name="dtb:totalPageCount"/>
<meta content="0" name="dtb:maxPageNumber"/>
</head>
<docTitle>
<text>第一卷</text>
</docTitle>
<navMap>
<navPoint id="navPoint-1" playOrder="1">
<navLabel>
<text>插图</text>
</navLabel>
<content src="chapter0001.xhtml"/>
</navPoint>
<navPoint id="navPoint-2" playOrder="2">
<navLabel>
<text>序章</text>
</navLabel>
<content src="chapter0002.xhtml"/>
</navPoint>
<navPoint id="navPoint-3" playOrder="3">
<navLabel>
<text>第一章 前世</text>
</navLabel>
<content src="chapter0003.xhtml"/>
</navPoint>
<navPoint id="navPoint-4" playOrder="4">
<navLabel>
<text>第二章 异世界</text>
</navLabel>
<content src="chapter0004.xhtml"/>
</navPoint>
...
</navMap>
</ncx>
--------------------------------------
meta 元数据
dtb:uid             和opf中的dc:identifier应该保持一致，不一致也没问题
dtb:depth           反映目录表中层次的深度。对于电子书不需要进行修改，使用这几个值就OK。
dtb:totalPageCount  仅用于纸质图书，保留 0 即可。
dtb:maxPageNumber   仅用于纸质图书，保留 0 即可。
docTitle 应该和opf中的dc:title一致，书名以opf中的为准
navMap   定义图书目录，navMap 包含一个或多个 navPoint 元素
navPoint 每个navPoint代表目录中的一项，每个 navPoint都要包含下列元素：
playOrder:          说明文档的阅读顺序。和 OPF spine 中 itemref 元素的顺序相同。
navLabel/text:      该章节的标题。通常是本章的标题或者数字。
content:            其中的 src 属性指向包含这些内容的物理资源。为 OPF manifest 中声明的文件。
 */


class EpubFileParser @Inject constructor(val context: Context) : FileParser {

    override suspend fun parse(file: DocumentFile): BookWithCover? {
        val absolutePath = file.getAbsolutePath(context)
        val title = file.baseName
        val rawFile = file.toRawFile(context)
        return innerParserFile(rawFile, title, absolutePath)
    }

    override suspend fun parse(cachedFile: CachedFile): BookWithCover? {
        val rawFile = cachedFile.rawFile
        val absolutePath = cachedFile.path
        val title = cachedFile.name.substringBeforeLast(".").trim()
        return innerParserFile(rawFile, title, absolutePath)
    }

    private fun extractCoverImage(file: File, coverImagePath: String?): String? {
        if (coverImagePath.isNullOrBlank()) {
            return null
        }

        ZipFile(file).use { zip ->
            zip.entries().asSequence().forEach { entry ->
                if (entry.name.endsWith(coverImagePath)) {
                    return try {
                        val inputStream = zip.getInputStream(entry)
                        val targetPath = getCoverPath(coverImagePath)
                        FileUtil.writeStreamToFile(inputStream, targetPath)
                        targetPath
                    } catch (ex: Exception) {
                        null
                    }
//                    val imageBytes = zip.getInputStream(entry).readBytes()
//                    return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
                }
            }
        }

        return null
    }

    fun getCoverPath(coverImageName: String) =
       context.filesDir.absolutePath + File.separator + "covers" + File.separator + UUID.randomUUID().toString() + "_" + coverImageName.replace(File.separator, "_")

    private suspend fun innerParserFile(rawFile: File?, baseName: String, absolutePath: String): BookWithCover? {
        return try {
            var book: BookWithCover? = null

            if (rawFile == null || !rawFile.exists() || !rawFile.canRead()) return null

            withContext(Dispatchers.IO) {
                ZipFile(rawFile).use { zip ->
                    val opfEntry = zip.entries().asSequence().find { entry ->
                        entry.name.endsWith(".opf", ignoreCase = true)
                    } ?: return@withContext

                    val opfContent = zip
                        .getInputStream(opfEntry)
                        .bufferedReader()
                        .use { it.readText() }
                    val document = Jsoup.parse(opfContent)

                    //解析得到书名
                    val title = document.select("metadata > dc|title").text().trim().run {
                        ifBlank {
                            baseName
                        }
                    }

                    val author = document.select("metadata > dc|creator").text().trim().run {
                        if (isBlank()) {
                            "" // stringResource(R.string.unknown_author)
                        } else {
                            this
                        }
                    }

                    val description = Jsoup.parse(
                        document.select("metadata > dc|description").text()
                    ).text().run {
                        ifBlank {
                            null
                        }
                    }

                    val coverImage = document
                        .select("metadata > meta[name=cover]")
                        .attr("content")
                        .run {
                            if (isNotBlank()) {
                                document
                                    .select("manifest > item[id=$this]")
                                    .attr("href")
                                    .apply { if (isNotBlank()) return@run this }
                            }

                            document
                                .select("manifest > item[media-type*=image]")
                                .firstOrNull()?.attr("href")
                        }
                    val coverImagePath = extractCoverImage(rawFile, coverImage)
                    Log.d("EpubFileParser", "coverImage=${coverImage}, coverImagePath=${coverImagePath},book.name=${title},author=${author}")

                    book = BookWithCover(
                        book = Book(
                            title = title,
                            author = author,
                            description = description,
                            scrollIndex = 0,
                            scrollOffset = 0,
                            progress = 0f,
                            filePath = Uri.fromFile(rawFile).toString(),
                            lastOpened = null,
                            category = "",
                            coverImage = coverImagePath,

                            fileType = "epub",
                        ),
                        coverImage = coverImagePath
                    )
                }
            }
            book
        } catch (e: Exception) {
            e.printStackTrace()
            null
        }
    }
}