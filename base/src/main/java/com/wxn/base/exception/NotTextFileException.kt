package com.wxn.base.exception

/**
 * TXT 解析时检测到文件内容为二进制格式（JPEG / PNG / PDF / ZIP / MOBI 等），不是文本。
 *
 * 抛出点：`com.wxn.bookparser.parser.txt.ChapterScanner.scan` 的二进制魔数守卫
 * （[com.wxn.bookparser.parser.txt.BinaryMagicNumberDetector] 命中后抛出）。
 *
 * 捕获点：
 * - `com.wxn.bookparser.parser.txt.TxtTextParser.parseChapterInfo` 重新抛出（不被吞成 emptyList）；
 * - `com.wxn.reader.presentation.mainReader.MainReadViewModel.bookload` 映射到
 *   `com.wxn.reader.presentation.bookReader.BookReaderUiState.Error`，向用户提示「不是文本文件」。
 *
 * 放在 `base` 模块（叶子模块，`bookparser` 与 `app` 均依赖）以避免反向依赖。
 *
 * 相关方案：`docs/plans/plan-txt-binary-guard.md`（如有）。
 *
 * @param fileName 文件名，仅用于日志与诊断信息，可为 null。
 * @param detectedType 命中的二进制类型名（如 `"JPEG"` / `"PNG"`），供 UI 与日志精确提示，可为 null。
 */
class NotTextFileException(
    val fileName: String? = null,
    val detectedType: String? = null,
) : Exception(buildMessage(fileName, detectedType)) {

    companion object {
        private fun buildMessage(fileName: String?, detectedType: String?): String {
            val name = fileName ?: "<unknown>"
            return if (detectedType != null) {
                "Not a text file: $name (detected: $detectedType)"
            } else {
                "Not a text file: $name"
            }
        }
    }
}
