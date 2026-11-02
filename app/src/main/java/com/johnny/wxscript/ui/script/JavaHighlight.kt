package com.johnny.wxscript.ui.script

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.input.OffsetMapping
import androidx.compose.ui.text.input.TransformedText
import androidx.compose.ui.text.input.VisualTransformation

/**
 * 编辑器语法高亮（Java / BeanShell 子集）。
 *
 * 只做**着色**，不改动任何字符，所以 OffsetMapping 用 Identity，
 * 光标位置与选区不会错位（这是很多「高亮后光标乱跳」的根因）。
 *
 * 词法按单遍扫描处理，正确跳过字符串/字符/行注释/块注释里的关键字，
 * 因此 `"class"` 这样的字符串不会被误认成关键字。
 */
data class HighlightColors(
    val keyword: Color,
    val comment: Color,
    val string: Color,
    val number: Color,
    val annotation: Color,
)

class JavaHighlightTransformation(private val colors: HighlightColors) : VisualTransformation {

    override fun filter(text: AnnotatedString): TransformedText {
        return TransformedText(highlight(text.text, colors), OffsetMapping.Identity)
    }

    override fun equals(other: Any?): Boolean {
        if (this === other) return true
        if (other !is JavaHighlightTransformation) return false
        return colors == other.colors
    }

    override fun hashCode(): Int = colors.hashCode()

    companion object {
        private val KEYWORDS = setOf(
            "abstract", "assert", "boolean", "break", "byte", "case", "catch", "char", "class",
            "const", "continue", "default", "do", "double", "else", "enum", "extends", "final",
            "finally", "float", "for", "goto", "if", "implements", "import", "instanceof", "int",
            "interface", "long", "native", "new", "package", "private", "protected", "public",
            "return", "short", "static", "strictfp", "super", "switch", "synchronized", "this",
            "throw", "throws", "transient", "try", "void", "volatile", "while", "true", "false",
            "null", "var", "record", "yield",
        )

        fun highlight(source: String, colors: HighlightColors): AnnotatedString {
            val builder = AnnotatedString.Builder(source)
            val n = source.length
            var i = 0
            while (i < n) {
                val c = source[i]
                when {
                    // 行注释
                    c == '/' && i + 1 < n && source[i + 1] == '/' -> {
                        var end = source.indexOf('\n', i)
                        if (end < 0) end = n
                        builder.addStyle(SpanStyle(color = colors.comment), i, end)
                        i = end
                    }
                    // 块注释（未闭合时一直染到末尾，编辑器里不会因此报错）
                    c == '/' && i + 1 < n && source[i + 1] == '*' -> {
                        var end = source.indexOf("*/", i + 2)
                        end = if (end < 0) n else end + 2
                        builder.addStyle(SpanStyle(color = colors.comment), i, end)
                        i = end
                    }
                    // 字符串
                    c == '"' -> {
                        var j = i + 1
                        while (j < n) {
                            val ch = source[j]
                            if (ch == '\\') {
                                j += 2
                                continue
                            }
                            if (ch == '"') {
                                j++
                                break
                            }
                            if (ch == '\n') break
                            j++
                        }
                        val end = j.coerceAtMost(n)
                        builder.addStyle(SpanStyle(color = colors.string), i, end)
                        i = end
                    }
                    // 字符字面量
                    c == '\'' -> {
                        var j = i + 1
                        while (j < n) {
                            val ch = source[j]
                            if (ch == '\\') {
                                j += 2
                                continue
                            }
                            if (ch == '\'') {
                                j++
                                break
                            }
                            if (ch == '\n') break
                            j++
                        }
                        val end = j.coerceAtMost(n)
                        builder.addStyle(SpanStyle(color = colors.string), i, end)
                        i = end
                    }
                    // 注解 / 类名引用
                    c == '@' -> {
                        var j = i + 1
                        while (j < n && (source[j].isLetterOrDigit() || source[j] == '_' || source[j] == '.')) j++
                        if (j > i + 1) builder.addStyle(SpanStyle(color = colors.annotation), i, j)
                        i = j
                    }
                    c.isDigit() -> {
                        var j = i + 1
                        while (j < n && (source[j].isLetterOrDigit() || source[j] == '_' || source[j] == '.')) j++
                        builder.addStyle(SpanStyle(color = colors.number), i, j)
                        i = j
                    }
                    c.isLetter() || c == '_' -> {
                        var j = i + 1
                        while (j < n && (source[j].isLetterOrDigit() || source[j] == '_')) j++
                        if (source.substring(i, j) in KEYWORDS) {
                            builder.addStyle(SpanStyle(color = colors.keyword), i, j)
                        }
                        i = j
                    }
                    else -> i++
                }
            }
            return builder.toAnnotatedString()
        }
    }
}

/** 当前光标所在行（1-based）。 */
fun lineOfOffset(text: CharSequence, offset: Int): Int {
    if (offset <= 0) return 1
    val end = offset.coerceAtMost(text.length)
    var line = 1
    for (index in 0 until end) {
        if (text[index] == '\n') line++
    }
    return line
}
