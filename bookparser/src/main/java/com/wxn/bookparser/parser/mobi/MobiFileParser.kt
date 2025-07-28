package com.wxn.bookparser.parser.mobi

import android.content.Context
import androidx.documentfile.provider.DocumentFile
import com.anggrayudi.storage.file.baseName
import com.anggrayudi.storage.file.extension
import com.wxn.base.bean.Book
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.domain.file.CachedFile
import com.wxn.bookparser.exts.rawFile
import com.wxn.mobi.MobiParser
import com.wxn.mobi.data.model.MetaInfo
import java.io.File
import javax.inject.Inject


/****
MOBI文件本质上是Palm数据库(PDB)的变种，包含多个数据记录(records)，主要结构如下： <br/>
[PDB Header]        32字节    <br/>
[PalmDOC Header]    16字节    <br/>
[MOBI Header]       可变长度    <br/>
[EXTH Header]   <br/>
[Text Records]  <br/>
[Image Records] <br/>
[FLIS Record]   <br/>
[FCIS Record]   <br/>
[Additional Records]    <br/>

------------------------------
PDB Header (32字节)   <br/>
Offset	Length	Description <br/>
0x00	32	Database name (null-terminated) <br/>
0x20	2	Attributes  <br/>
0x22	2	Version <br/>
0x24	4	Creation time (Palm OS timestamp)<br/>
0x28	4	Modification time<br/>
0x2C	4	Last backup time<br/>
0x30	4	Modification number<br/>
0x34	4	App info ID<br/>
0x38	4	Sort info ID<br/>
...	...	...<br/>

------------------------------<br/>
PalmDOC Header (16字节)<br/>
Offset	Length	Description<br/>
+0	2	Compression type (1=PalmDOC,2=HUFF/CDIC)<br/>
+2	2	Unused<br/>
+4	4	Text length (uncompressed)<br/>
+8	2	Record count<br/>
+10	2	Record size (usually 4096)<br/>
+12	4	Encryption type (0=none,1=Old Mobipocket,2=Mobipocket)<br/>

------------------------------<br/>
MOBI Header (可变长度)<br/>
关键字段包括：<br/>
Header length:         4 bytes<br/>
Mobi type:             4 bytes (0x2=BOOK,0x3=BOOKMOBI)<br/>
Text encoding:         4 bytes (1252=Latin1,65001=UTF8)<br/>
Unique ID:             4 bytes<br/>
File version:          4 bytes<br/>
...<br/>
First image index:     4 bytes<br/>
...<br/>
EXTH flags:            4 bytes<br/>
...<br/>
Title offset:          4 bytes<br/>
Title length:          4 bytes<br/>
...<br/>

------------------------------<br/>
EXTH Header<br/>
扩展头包含丰富的元数据：<br/>

Header identifier:     "EXTH"<br/>
Header length:         4 bytes<br/>
Record count:         4 bytes<br/>
Records:              [Record type][Record length][Record data]...<br/>

常见记录类型：<br/>
100: Author<br/>
101: Publisher<br/>
102: Publishing date<br/>
103: ISBN<br/>
104: Copyright<br/>
105: Subject<br/>
106: Description<br/>
108: Contributor<br/>
109: Rights<br/>
...<br/>
202: Cover offset<br/>
203: Thumbnail offset<br/>
205: Language<br/>
206: Writing mode<br/>
207: Creator software<br/>
208: Creator major version<br/>
209: Creator minor version<br/>
...<br/>
501: ASIN (Amazon Standard Identification Number)<br/>
503: cdecontenttype<br/>
504: updated title<br/>
508: Unknown but important for Kindle<br/>
...<br/>

------------------------------<br/>
Text内容存储<br/>
文本内容采用以下方式之一存储：<br/>
1. PalmDOC压缩(LZ77变种)<br/>
2. HUFF/CDIC压缩(字典压缩)<br/>
3. Uncompressed<br/>

典型HTML结构：<br/>
<html><br/>
<mbp:frameset><br/>
<mbp:pagebreak/><br/>
<center><h2>Chapter One</h2></center><br/>
<p>This is the first paragraph...</p><br/>
<img src="image0001.png"/><br/>
</mbp:frameset><br/>
</html><br/>

------------------------------<br/>
FLIS和FCIS记录<br/>
FLIS(Flow Information Structure):<br/>
<br/>
"FLIS" magic number<br/>
8 byte header<br/>
Flow count<br/>
Flow offsets array<br/>

FCIS(Fragment Control Information Structure):<br/>
<br/>
"FCIS" magic number<br/>
28 byte header<br/>
Page count<br/>
Fragment information<br/>
<br/>
------------------------------<br/>
DRM保护机制<br/>
MOBI DRM有两种形式：<br/>
1. Old Mobipocket DRM:<br/>
User PID based encryption<br/>
Serial number绑定<br/>
2. Amazon DRM:<br/>
ASIN绑定<br/>
Kindle设备授权<br/>
<br/>
DRM移除通常需要PID或通过逆向工程。<br/>
<br/>
------------------------------<br/>
MOBI变种<br/>
1. KF7(MOBI7):<br/>
Basic Kindle format<br/>
Limited HTML support<br/>
2. KF8(MOBI8/AZW3):<br/>
Enhanced format with better HTML/CSS support<br/>
Fixed layout capabilities<br/>
3. Print Replica:<br/>
PDF-like fixed layout format<br/>
<br/>
------------------------------<br/>
MOBI与AZW的关系<br/>
AZW本质上是带有Amazon特定元数据的MOBI文件：<br/>
1. ASIN必须存在<br/>
2. Amazon特定的EXTH记录(508等)<br/>
3. DRM方式不同<br/>
 *
 */
class MobiFileParser @Inject constructor(val context: Context) : FileParser {

    override suspend fun parse(file: DocumentFile): Book? {
        val rawFile = file.rawFile(context)
        val title = file.baseName
        val path = file.uri.toString()
        val format = file.extension

        return innerParse(rawFile, title, path, format)
    }

    override suspend fun parse(cachedFile: CachedFile): Book? {
        val rawFile = cachedFile.rawFile
        val title = cachedFile.name.substringBeforeLast(".").trim()
        val path = cachedFile.uri.toString()
        val format = cachedFile.extension
        return innerParse(rawFile, title, path, format)
    }

    private suspend fun innerParse(
        rawFile: File?,
        title: String,
        uriPath: String,
        format: String
    ): Book? {
        if (rawFile == null || !rawFile.isFile || !rawFile.exists() || !rawFile.canRead()) {
            return null
        }
        val path = rawFile.absolutePath

        val metaInfo: MetaInfo = MobiParser.getMobiInfo(context, path) ?: return null

        return Book(
            title = metaInfo.title ?: title ?: "",
            author = metaInfo.author.orEmpty(),

            publisher = metaInfo.publisher.orEmpty(),
            description = metaInfo.description.orEmpty(),
            language = metaInfo.language.orEmpty(),
            review = metaInfo.review.orEmpty(),

            scrollIndex = 0,
            scrollOffset = 0,

            progress = 0f,
            filePath = uriPath,
            lastOpened = null,
            category = metaInfo.subject.orEmpty(),
            coverImage = metaInfo.coverPath.orEmpty(),
            fileType = format
        )
    }
}