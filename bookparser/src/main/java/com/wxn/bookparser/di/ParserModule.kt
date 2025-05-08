package com.wxn.bookparser.di

import android.app.Application
import android.content.Context
import com.wxn.bookparser.FileParser
import com.wxn.bookparser.impl.FileParserImpl
import com.wxn.bookparser.impl.TextParserImpl
import com.wxn.bookparser.parser.base.DocumentParser
import com.wxn.bookparser.parser.base.MarkdownParser
import com.wxn.bookparser.parser.epub.EpubFileParser
import com.wxn.bookparser.parser.epub.EpubTextParser
import com.wxn.bookparser.parser.fb2.Fb2FileParser
import com.wxn.bookparser.parser.html.HtmlFileParser
import com.wxn.bookparser.parser.html.HtmlTextParser
import com.wxn.bookparser.parser.pdf.PdfFileParser
import com.wxn.bookparser.parser.pdf.PdfTextParser
import com.wxn.bookparser.parser.txt.TxtFileParser
import com.wxn.bookparser.parser.txt.TxtTextParser
import com.wxn.bookparser.parser.xml.XmlTextParser
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ParserModule {

    @Provides
    @Singleton
    fun provideCommonmarkParser(): org.commonmark.parser.Parser = org.commonmark.parser.Parser.builder().build();

    @Provides
    @Singleton
    fun provideMarkdownParser(parser: org.commonmark.parser.Parser): MarkdownParser = MarkdownParser(parser)

    @Provides
    @Singleton
    fun provideDocumentParser(markdownParser: MarkdownParser): DocumentParser = DocumentParser(markdownParser)

    @Provides
    @Singleton
    fun provideTxtFileParser(context: Context): TxtFileParser = TxtFileParser(context)

    @Provides
    @Singleton
    fun provideTxtTextParser(markdownParser: MarkdownParser): TxtTextParser = TxtTextParser(markdownParser)

    @Provides
    @Singleton
    fun provideXmlTextParser(documentParser: DocumentParser): XmlTextParser = XmlTextParser(documentParser)

    @Provides
    @Singleton
    fun providePdfTextParser(markdownParser: MarkdownParser, application: Application): PdfTextParser = PdfTextParser(markdownParser, application)

    @Provides
    @Singleton
    fun providePdfFileParser(application: Application): PdfFileParser = PdfFileParser(application)

    @Provides
    @Singleton
    fun provideHtmlFileParser(context: Context): HtmlFileParser = HtmlFileParser(context)

    @Provides
    @Singleton
    fun provideHtmlTextParser(documentParser: DocumentParser): HtmlTextParser = HtmlTextParser(documentParser)

    @Provides
    @Singleton
    fun provideFb2FileParser(context: Context): Fb2FileParser = Fb2FileParser(context)


    @Provides
    @Singleton
    fun provideEpubFileParser(context: Context): EpubFileParser = EpubFileParser(context)


    @Provides
    @Singleton
    fun provideEpubTextParser(documentParser: DocumentParser): EpubTextParser = EpubTextParser(documentParser)


    @Provides
    @Singleton
    fun provideFileParser(
        txtFileParser: TxtFileParser,
        pdfFileParser: PdfFileParser,
        epubFileParser: EpubFileParser,
        fb2FileParser: Fb2FileParser,
        htmlFileParser: HtmlFileParser,
    ): FileParser = FileParserImpl(
        txtFileParser,
        pdfFileParser,
        epubFileParser,
        fb2FileParser,
        htmlFileParser
    )

    @Provides
    @Singleton
    fun provideTextParser(
        // Markdown parser (Markdown)
        txtTextParser: TxtTextParser,
        pdfTextParser: PdfTextParser,
        // Document parser (HTML+Markdown)
        epubTextParser: EpubTextParser,
        htmlTextParser: HtmlTextParser,
        xmlTextParser: XmlTextParser
    ): TextParserImpl = TextParserImpl(txtTextParser, pdfTextParser, epubTextParser, htmlTextParser, xmlTextParser)

}