package com.wxn.reader.data.model

import androidx.compose.ui.graphics.toArgb
import androidx.core.graphics.alpha
import androidx.core.graphics.blue
import androidx.core.graphics.green
import androidx.core.graphics.red
import androidx.paging.Config
import androidx.compose.ui.text.style.TextAlign as ComposeTextAlign
//import org.readium.r2.navigator.preferences.TextAlign as RediumTextAlign
//import org.readium.r2.navigator.preferences.Color as RediumColor
//import org.readium.r2.navigator.preferences.ReadingProgression as ReadingProgression
import com.wxn.bookread.data.model.preference.ReaderPreferences
//import org.readium.r2.navigator.epub.EpubPreferences
import com.wxn.bookread.data.model.config.ConfigReadingProgression
//import org.readium.r2.shared.ExperimentalReadiumApi

import androidx.compose.ui.graphics.Color as ComposeColor

// Extension function to convert ReaderPreferences to EpubPreferences
//@OptIn(ExperimentalReadiumApi::class)
//fun ReaderPreferences.toRediumEpubPreferences(): EpubPreferences {
//
//    return EpubPreferences(
//        fontSize = this.fontSize.toDouble(),
////        fontWeight = this.fontWeight,
//        letterSpacing = this.letterSpacing.toDouble(),
//        lineHeight = this.lineHeight.toDouble(),
//        pageMargins = this.pageHorizontalMargins.toDouble(),
//        paragraphIndent = this.paragraphIndent.toDouble(),
//        paragraphSpacing = this.paragraphSpacing.toDouble(),
//        wordSpacing = this.wordSpacing.toDouble(),
//        textAlign = this.textAlign.toRedium(),
//        //ui Settings
//        backgroundColor = RediumColor(this.backgroundColor),
//        textColor =RediumColor(this.textColor),
//        //Reader Settings
//        scroll = this.scroll,
//        readingProgression = this.readingProgression.toRedium(),
//        verticalText = this.verticalText,
//        publisherStyles = this.publisherStyles,
//        textNormalization = this.textNormalization,
//    )
//}
//
//fun ComposeTextAlign.toRedium() : RediumTextAlign =
//    when(this) {
//        ComposeTextAlign.Left ->  RediumTextAlign.LEFT
//        ComposeTextAlign.Right ->  RediumTextAlign.RIGHT
//        ComposeTextAlign.Center ->  RediumTextAlign.CENTER
//        ComposeTextAlign.Justify ->  RediumTextAlign.JUSTIFY
//        ComposeTextAlign.Start ->  RediumTextAlign.START
//        ComposeTextAlign.End ->  RediumTextAlign.END
//        else -> RediumTextAlign.LEFT
//    }
//
//fun RediumTextAlign.toCompose() : ComposeTextAlign =
//    when(this) {
//        RediumTextAlign.LEFT -> ComposeTextAlign.Left
//        RediumTextAlign.RIGHT -> ComposeTextAlign.Right
//        RediumTextAlign.CENTER -> ComposeTextAlign.Center
//        RediumTextAlign.JUSTIFY -> ComposeTextAlign.Justify
//        RediumTextAlign.START -> ComposeTextAlign.Start
//        RediumTextAlign.END -> ComposeTextAlign.End
//        else -> ComposeTextAlign.Left
//    }
//
//fun ConfigReadingProgression.toRedium(): ReadingProgression =
//    when(this) {
//        ConfigReadingProgression.LTR -> ReadingProgression.LTR
//        ConfigReadingProgression.RTL -> ReadingProgression.RTL
//        else -> ReadingProgression.LTR
//    }
//
//fun ReadingProgression.toConfig(): ConfigReadingProgression =
//    when(this){
//        ReadingProgression.RTL -> ConfigReadingProgression.RTL
//        ReadingProgression.LTR -> ConfigReadingProgression.LTR
//        else -> ConfigReadingProgression.LTR
//    }