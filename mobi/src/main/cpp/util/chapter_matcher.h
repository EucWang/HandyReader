//
// 章节标题手写 matcher（替代 std::regex）
// 从 book_util.h 抽取为独立头文件，供 EPUB/MOBI/TXT 共享使用。
// 所有函数包装在 namespace chapter_matcher 中。
//

#ifndef HANDYREADER_CHAPTER_MATCHER_H
#define HANDYREADER_CHAPTER_MATCHER_H

#include <string>
#include <vector>
#include <cstdint>
#include <cctype>

namespace chapter_matcher {

// ──────────────────────────────────────────────────────────────────
// 章节标题手写 matcher（替代 std::regex）
//
// 背景：scanSplitPoints 对 ~15000 个 <p> 段落逐段调用 std::regex，
//   NDK libstdc++ 的 std::regex 极慢（~1.8ms/次），实测 detectAndSplit
//   耗时 135 秒。手写 matcher 把每次匹配降到 O(标题长度) 字节比较。
//
// 语义等价性铁律（评审要求，改动时必须遵守）：
//   1. matcher 只返回 matched，不回填 isVol；isVol 保持现状 clean.find("卷")。
//   2. chapterRe(13 中文数字) 与 jpRe(9 中文数字) 用两套独立字符集，禁止合并。
//   3. enRe 用 regex_match 语义（整串耗尽），末尾 return pos==size；其余 4 个用
//      regex_search 语义（前缀匹配），禁止加整串判断。
//   4. 每次字节访问前必须判 p+N<=size（防越界崩溃）；中文 +3 / ASCII +1 步长不混用。
//   5. 严格从 clean[0] 起判，不跳过全角空格，与 ^ 锚 + trim 现状等价。
//   6. ASCII 判定用 unsigned char 严格字节范围，不用 isdigit()/isspace()（locale 敏感）。
//   7. 数字循环后必须校验「至少推进 1 次」，对标 +（≥1）。
//
// UTF-8 字节表（python 验证）：
//   第=E7ACAC  章=E7ABA0  节=E88A82  卷=E58DB7
//   零=E99BB6 一=E4B800 二=E4BA8C 三=E4B889 四=E59B9B 五=E4BA94
//   六=E585AD 七=E4B883 八=E585AB 九=E4B99D 十=E58D81 百=E799BE 千=E58D83 万=E4B887 两=E4B8A4
//   序=E5BA8F 楔=E6A594 前=E5898D 言=E8A880 后=E5908E 记=E8AEB0 尾=E5B0BE 声=E5A3B0
//   引=E5BC95 子=E5AD90 跋=E8B78B  全角空格　=E38080
//   제=ECA09C  장=EC9EA5
// ──────────────────────────────────────────────────────────────────

// 判断 s[p..] 是否为指定 3 字节序列（内置边界检查，防越界）
static inline bool eq3(const std::string &s, size_t p, uint8_t a, uint8_t b, uint8_t c) {
    return p + 3 <= s.size() &&
           (uint8_t) s[p] == a && (uint8_t) s[p + 1] == b && (uint8_t) s[p + 2] == c;
}

// ASCII 空白判定（对标正则 \s，严格单字节，参数 unsigned char 防符号扩展）
static inline bool isWs(unsigned char c) {
    return c == ' ' || c == '\t' || c == '\n' || c == '\r' || c == '\f' || c == '\v';
}

// ASCII 数字判定（对标 [0-9]，严格 0x30-0x39，禁用 isdigit）
static inline bool isAsciiDigit(unsigned char c) {
    return c >= '0' && c <= '9';
}

// chapterRe 中文数字字符集：零一二三四五六七八九十百千万两（13 个）
static bool isCjkDigitFull(const std::string &s, size_t p) {
    return eq3(s, p, 0xE9, 0x9B, 0xB6)  // 零
           || eq3(s, p, 0xE4, 0xB8, 0x80) // 一
           || eq3(s, p, 0xE4, 0xBA, 0x8C) // 二
           || eq3(s, p, 0xE4, 0xB8, 0x89) // 三
           || eq3(s, p, 0xE5, 0x9B, 0x9B) // 四
           || eq3(s, p, 0xE4, 0xBA, 0x94) // 五
           || eq3(s, p, 0xE5, 0x85, 0xAD) // 六
           || eq3(s, p, 0xE4, 0xB8, 0x83) // 七
           || eq3(s, p, 0xE5, 0x85, 0xAB) // 八
           || eq3(s, p, 0xE4, 0xB9, 0x9D) // 九
           || eq3(s, p, 0xE5, 0x8D, 0x81) // 十
           || eq3(s, p, 0xE7, 0x99, 0xBE) // 百
           || eq3(s, p, 0xE5, 0x8D, 0x83) // 千
           || eq3(s, p, 0xE4, 0xB8, 0x87) // 万
           || eq3(s, p, 0xE4, 0xB8, 0xA4); // 两
}

// jpRe 中文数字字符集：一二三四五六七八九十（9 个，无零百千万两）
static bool isCjkDigitJp(const std::string &s, size_t p) {
    return eq3(s, p, 0xE4, 0xB8, 0x80) // 一
           || eq3(s, p, 0xE4, 0xBA, 0x8C) // 二
           || eq3(s, p, 0xE4, 0xB8, 0x89) // 三
           || eq3(s, p, 0xE5, 0x9B, 0x9B) // 四
           || eq3(s, p, 0xE4, 0xBA, 0x94) // 五
           || eq3(s, p, 0xE5, 0x85, 0xAD) // 六
           || eq3(s, p, 0xE4, 0xB8, 0x83) // 七
           || eq3(s, p, 0xE5, 0x85, 0xAB) // 八
           || eq3(s, p, 0xE4, 0xB9, 0x9D) // 九
           || eq3(s, p, 0xE5, 0x8D, 0x81); // 十
}

// 判定 s[p..] 是否为「空白 / 全角空格 / 结尾」（对标正则 (\s|[　]|$)）
static bool isWsOrFwspOrEnd(const std::string &s, size_t p) {
    if (p >= s.size()) return true;                       // $ 结尾
    if (isWs((unsigned char) s[p])) return true;          // \s（ASCII 空白）
    return eq3(s, p, 0xE3, 0x80, 0x80);                   // 全角空格　(U+3000)
}

// (1) 替换 ^第[零一二三四五六七八九十百千万两0-9]+[章节卷](\s|[　]|$)  (regex_search 语义)
static bool matchChapterRe(const std::string &s) {
    if (!eq3(s, 0, 0xE7, 0xAC, 0xAC)) return false; // 不以「第」开头
    size_t p = 3;
    size_t numStart = p;
    // 数字部分对标 [零...0-9]+：中文数字与 ASCII 数字可任意顺序混合（如「第3十2章」）
    while (true) {
        if (p + 3 <= s.size() && isCjkDigitFull(s, p)) { p += 3; continue; }
        if (p < s.size() && isAsciiDigit((unsigned char) s[p])) { p += 1; continue; }
        break;
    }
    if (p == numStart) return false;                 // + 至少 1 个数字
    if (p + 3 > s.size()) return false;              // 不够放单位字符
    bool isZhang = eq3(s, p, 0xE7, 0xAB, 0xA0);     // 章
    bool isJie = eq3(s, p, 0xE8, 0x8A, 0x82);       // 节
    bool isJuan = eq3(s, p, 0xE5, 0x8D, 0xB7);      // 卷
    if (!isZhang && !isJie && !isJuan) return false;
    p += 3;
    return isWsOrFwspOrEnd(s, p);                    // 尾部约束
}

// (2) 替换 ^(序章|楔子|前言|后记|尾声|引子|序言|跋)(\s|[　]|$)  (regex_search 语义)
static bool matchSpecialRe(const std::string &s) {
    // 7 个 6 字节候选前缀（每词 2 个 CJK）+ 「跋」单字 3 字节
    static const struct { uint8_t b[6]; } cands[] = {
            {{0xE5, 0xBA, 0x8F, 0xE7, 0xAB, 0xA0}}, // 序章
            {{0xE6, 0xA5, 0x94, 0xE5, 0xAD, 0x90}}, // 楔子
            {{0xE5, 0x89, 0x8D, 0xE8, 0xA8, 0x80}}, // 前言
            {{0xE5, 0x90, 0x8E, 0xE8, 0xAE, 0xB0}}, // 后记
            {{0xE5, 0xB0, 0xBE, 0xE5, 0xA3, 0xB0}}, // 尾声
            {{0xE5, 0xBC, 0x95, 0xE5, 0xAD, 0x90}}, // 引子
            {{0xE5, 0xBA, 0x8F, 0xE8, 0xA8, 0x80}}, // 序言
    };
    for (const auto &c : cands) {
        if (s.size() < 6) break;
        if ((uint8_t) s[0] == c.b[0] && (uint8_t) s[1] == c.b[1] && (uint8_t) s[2] == c.b[2] &&
            (uint8_t) s[3] == c.b[3] && (uint8_t) s[4] == c.b[4] && (uint8_t) s[5] == c.b[5]) {
            return isWsOrFwspOrEnd(s, 6);
        }
    }
    if (eq3(s, 0, 0xE8, 0xB7, 0x8B)) {              // 跋（单字）
        return isWsOrFwspOrEnd(s, 3);
    }
    if (eq3(s, 0, 0xE5, 0xBA, 0x8F)) {              // 序（单字）
        return isWsOrFwspOrEnd(s, 3);
    }
    return false;
}

// (3) 替换 ^([Cc]apter|[Cc]hap|CHAPTER|Section|Scene|Part|Introduction)\s+[0-9IVXLCDM]+
// (regex_match 语义：整串耗尽)
static bool matchEnRe(const std::string &s) {
    size_t p = 0;
    size_t n = s.size();
    auto lower = [](unsigned char c) { return (c >= 'A' && c <= 'Z') ? (unsigned char)(c + 32) : c; };
    unsigned char c0 = n > 0 ? lower((unsigned char) s[0]) : 0;
    // 前缀词表（按首字节剪枝）：
    //   CHAPTER/Chapter/chapter (7B, c)  / Chap/chap (4B, c)
    //   Section (7B, s) / Scene (5B, s)
    //   Part (4B, p)
    //   Introduction (12B, i)
    if (c0 == 'c') {
        if (n >= 7 && s[0] == 'C' && s[1] == 'H' && s[2] == 'A' && s[3] == 'P' &&
            s[4] == 'T' && s[5] == 'E' && s[6] == 'R') {
            p = 7;                                    // CHAPTER 全大写
        } else if (n >= 7 && lower((unsigned char) s[0]) == 'c' && lower((unsigned char) s[1]) == 'h' &&
                   lower((unsigned char) s[2]) == 'a' && lower((unsigned char) s[3]) == 'p' &&
                   lower((unsigned char) s[4]) == 't' && lower((unsigned char) s[5]) == 'e' &&
                   lower((unsigned char) s[6]) == 'r') {
            p = 7;                                    // Chapter/chapter
        } else if (n >= 4 && lower((unsigned char) s[0]) == 'c' && lower((unsigned char) s[1]) == 'h' &&
                   lower((unsigned char) s[2]) == 'a' && lower((unsigned char) s[3]) == 'p') {
            p = 4;                                    // Chap/chap
        } else {
            return false;
        }
    } else if (c0 == 's') {
        if (n >= 7 && lower((unsigned char) s[1]) == 'e' && lower((unsigned char) s[2]) == 'c' &&
            lower((unsigned char) s[3]) == 't' && lower((unsigned char) s[4]) == 'i' &&
            lower((unsigned char) s[5]) == 'o' && lower((unsigned char) s[6]) == 'n') {
            p = 7;                                    // Section/section
        } else if (n >= 5 && lower((unsigned char) s[1]) == 'c' && lower((unsigned char) s[2]) == 'e' &&
                   lower((unsigned char) s[3]) == 'n' && lower((unsigned char) s[4]) == 'e') {
            p = 5;                                    // Scene/scene
        } else {
            return false;
        }
    } else if (c0 == 'p') {
        if (n >= 4 && lower((unsigned char) s[1]) == 'a' && lower((unsigned char) s[2]) == 'r' &&
            lower((unsigned char) s[3]) == 't') {
            p = 4;                                    // Part/part
        } else {
            return false;
        }
    } else if (c0 == 'i') {
        if (n >= 12 && lower((unsigned char) s[1]) == 'n' && lower((unsigned char) s[2]) == 't' &&
            lower((unsigned char) s[3]) == 'r' && lower((unsigned char) s[4]) == 'o' &&
            lower((unsigned char) s[5]) == 'd' && lower((unsigned char) s[6]) == 'u' &&
            lower((unsigned char) s[7]) == 'c' && lower((unsigned char) s[8]) == 't' &&
            lower((unsigned char) s[9]) == 'i' && lower((unsigned char) s[10]) == 'o' &&
            lower((unsigned char) s[11]) == 'n') {
            p = 12;                                   // Introduction/introduction
        } else {
            return false;
        }
    } else {
        return false;
    }
    size_t wsStart = p;
    while (p < n && isWs((unsigned char) s[p])) p += 1;  // \s+
    if (p == wsStart) return false;
    // 数字字符集补全 D=500/M=1000（原 [0-9IVXLC] 漏 D/M，导致 Chapter IV 后期不识别）
    auto isEnDig = [](unsigned char c) {
        return (c >= '0' && c <= '9') ||
               c == 'I' || c == 'V' || c == 'X' || c == 'L' ||
               c == 'C' || c == 'D' || c == 'M';
    };
    size_t digStart = p;
    while (p < n && isEnDig((unsigned char) s[p])) p += 1; // [0-9IVXLCDM]+
    if (p == digStart) return false;
    return p == n;                                   // regex_match：整串耗尽
}

// (3.5) 替换 ^(Prologue|Epilogue|Preface|Foreword|Afterword)(\s|[　]|$)
// 整词约束：词完全匹配后 s[wordLen] 满足 \s / 全角空格 / end
// 首字节剪枝：p/e/f/a
// 不含 introduction（避免 "Introduction to X" 误匹配；Introduction 走 matchEnRe 的带数字形式）
static bool matchEnSpecialRe(const std::string &s) {
    if (s.empty()) return false;
    unsigned char c = (unsigned char) s[0];
    unsigned char cl = (c >= 'A' && c <= 'Z') ? (unsigned char)(c + 32) : c;
    if (cl != 'p' && cl != 'e' && cl != 'f' && cl != 'a') return false;  // 首字节剪枝

    static const struct { const char *word; size_t len; } cands[] = {
            {"prologue", 8}, {"epilogue", 8}, {"preface", 7},
            {"foreword", 8}, {"afterword", 9}
    };
    const size_t n = s.size();
    for (const auto &w : cands) {
        if (n < w.len) continue;
        bool ok = true;
        for (size_t i = 0; i < w.len; ++i) {
            if (tolower((unsigned char) s[i]) != (unsigned char) w.word[i]) {
                ok = false; break;
            }
        }
        if (ok && isWsOrFwspOrEnd(s, w.len)) return true;
    }
    return false;
}

// (4) 替换 ^第[0-9一二三四五六七八九十]+章  (regex_search 语义，无尾部约束)
static bool matchJpRe(const std::string &s) {
    if (!eq3(s, 0, 0xE7, 0xAC, 0xAC)) return false; // 「第」
    size_t p = 3;
    size_t numStart = p;
    // 数字部分对标 [0-9一二...]+：ASCII 与中文数字可任意顺序混合
    while (true) {
        if (p + 3 <= s.size() && isCjkDigitJp(s, p)) { p += 3; continue; }
        if (p < s.size() && isAsciiDigit((unsigned char) s[p])) { p += 1; continue; }
        break;
    }
    if (p == numStart) return false;                 // + 至少 1 个数字
    return eq3(s, p, 0xE7, 0xAB, 0xA0);             // 「章」（无尾部约束）
}

// (5) 替换 ^제\s*[0-9]+장  (regex_search 语义)
static bool matchKrRe(const std::string &s) {
    if (!eq3(s, 0, 0xEC, 0xA0, 0x9C)) return false; // 제
    size_t p = 3;
    size_t n = s.size();
    while (p < n && isWs((unsigned char) s[p])) p += 1;  // \s*
    size_t digStart = p;
    while (p < n && isAsciiDigit((unsigned char) s[p])) p += 1; // [0-9]+
    if (p == digStart) return false;                 // + 至少 1 个数字
    return eq3(s, p, 0xEC, 0x9E, 0xA5);             // 장
}

// ──────────────────────────────────────────────────────────────────
// 多语种章节标题 matcher（FR/DE/PT/ES/RU/HI/AR）
//
// 背景：原 matcher 仅覆盖中/英/日/韩。应用支持法/德/葡/西/俄/印地/阿拉伯，
//   这些语种的 EPUB 大章节无法识别标题边界，降级为均匀切分（纯数字序号）。
//
// 七语种章节标题只有两种形态：
//   形态A「序号词 + 数字」：[Word] \s+ [Digits]+ [ws|end]
//   形态B「特殊章词」：    [Word] [ws|end]
// 差异仅 3 维：词的字节表 / 数字字符集 / 大小写规则。
// 故用两个泛型 helper + 4 个入口，避免 14 个重复函数。
//
// 评审铁律（改动时必须遵守）：
//   1. 每个入口首行必须做首字节剪枝（对标 matchJpRe 判「第」/matchKrRe 判 제），
//      否则 4 个 matcher 对 ~15000 个 <p> 都遍历词表，性能回退。
//   2. helper 词匹配必须「整词约束」：词完全匹配后检查 s[wordLen] 满足下一约束
//      （numbered 需 \s；special 需 \s/end）。绝不返回"前缀即可"——
//      否则 Prolog 会误吞 Prologue（Prolog 是 Prologue 的前缀）。
//   3. 每次字节访问前 p+N<=size（防越界）；ASCII 1B / Arabic-Indic 2B / Devanagari 3B 步长不混用。
//   4. ASCII 判定用 unsigned char；tolower 仅对 ASCII A-Z 有效，重音/Cyrillic 字节直接比。
//   5. 数字 ≥1 位校验（对标 matchEnRe digStart）。
//
// UTF-8 字节表（Python 验证，2026-07-04）：
//   Chapitre=43 68 61 70 69 74 72 65    Prologue=50 72 6F 6C 6F 67 75 65
//   Épilogue=C3 89 70 69 6C 6F 67 75 65 (É=C3 89)   Epilogue=45 70 69 6C 6F 67 75 65
//   Kapitel=4B 61 70 69 74 65 6C        Prolog=50 72 6F 6C 6F 67    Epilog=45 70 69 6C 6F 67
//   Capítulo=43 61 70 C3 AD 74 75 6C 6F (í=C3 AD)   Capitulo=43 61 70 69 74 75 6C 6F
//   Prólogo=50 72 C3 B3 6C 6F 67 6F (ó=C3 B3)       Prologo=50 72 6F 6C 6F 67 6F
//   Epílogo=45 70 C3 AD 6C 6F 67 6F                  Epilogo=45 70 6F 6C 6F 67 6F
//   Глава  =D0 93 D0 BB D0 B0 D0 B2 D0 B0            ГЛАВА  =D0 93 D0 9B D0 90 D0 92 D0 90
//   Пролог =D0 9F D1 80 D0 BE D0 BB D0 BE D0 B3      ПРОЛОГ=D0 9F D0 A0 D0 9E D0 9B D0 9E D0 93
//   Эпилог =D0 AD D0 BF D0 B8 D0 BB D0 BE D0 B3      ЭПИЛОГ=D0 AD D0 9F D0 98 D0 9B D0 9E D0 93
//     注意 Cyrillic 大小写 lead byte 不同（Г=D0 93，г=D0 B3；Р=D0 A0，р=D1 80），不能靠 tolower
//   अध्याय   =E0 A4 85 E0 A4 A7 E0 A5 8D E0 A4 AF E0 A4 BE E0 A4 AF
//   प्रस्तावना=E0 A4 AA E0 A5 8D E0 A4 B0 E0 A4 B8 E0 A5 8D E0 A4 A4 E0 A4 BE E0 A4 B5 E0 A4 A8 E0 A4 BE
//   الفصل=D8 A7 D9 84 D9 81 D8 B5 D9 84   باب=D8 A8 D8 A7 D8 B8   مقدمة=D9 85 D9 82 D8 AF D9 85 D8 A9
//   ASCII 0-9=0x30..0x39  Roman=IVXLCDM(0x49,0x56,0x58,0x4C,0x43,0x44,0x4D)/小写(0x69...)
//   Arabic-Indic ٠-٩=D9 A0..A9 (2B)   Devanagari ०-९=E0 A5 A6..AF (3B)
// ──────────────────────────────────────────────────────────────────

// 数字字符集（决定 digitAdvance 的步长与字符范围）
enum DigitClass {
    DIGIT_LATIN,         // 0-9 + 罗马数字 IVXLCDM（大小写不敏感）→ FR/DE/PT/ES
    DIGIT_ASCII,         // 0-9 → RU
    DIGIT_ARABIC_INDIC,  // 0-9 + ٠-٩ U+0660..0669 → AR
    DIGIT_DEVANAGARI     // 0-9 + ०-९ U+0966..096F → HI
};

// 数字推进 helper：返回消费字节数（0=非数字）。
// 每次访问前已做边界检查；ASCII 1B / Arabic-Indic 2B / Devanagari 3B 步长严格区分不混用。
static size_t digitAdvance(const std::string &s, size_t p, DigitClass dc) {
    const size_t n = s.size();
    if (p >= n) return 0;
    const unsigned char c = (unsigned char) s[p];
    // ASCII 数字（所有 dc 通用）
    if (c >= '0' && c <= '9') return 1;
    switch (dc) {
        case DIGIT_LATIN: {
            // 罗马数字大小写不敏感
            if (c == 'I' || c == 'V' || c == 'X' || c == 'L' ||
                c == 'C' || c == 'D' || c == 'M' ||
                c == 'i' || c == 'v' || c == 'x' || c == 'l' ||
                c == 'c' || c == 'd' || c == 'm') return 1;
            return 0;
        }
        case DIGIT_ASCII:
            return 0;
        case DIGIT_ARABIC_INDIC: {
            // ٠-٩ = U+0660..0669 = D9 A0..A9（2B）
            if (p + 2 <= n && (unsigned char) s[p] == 0xD9 &&
                (unsigned char) s[p + 1] >= 0xA0 && (unsigned char) s[p + 1] <= 0xA9) {
                return 2;
            }
            return 0;
        }
        case DIGIT_DEVANAGARI: {
            // ०-९ = U+0966..096F = E0 A5 A6..AF（3B）
            if (p + 3 <= n && (unsigned char) s[p] == 0xE0 &&
                (unsigned char) s[p + 1] == 0xA5 &&
                (unsigned char) s[p + 2] >= 0xA6 && (unsigned char) s[p + 2] <= 0xAF) {
                return 3;
            }
            return 0;
        }
    }
    return 0;
}

// 形态A helper：词(大小写不敏感) + \s+ + 数字(≥1，按 dc) + [ws|end]
// 调用方入口已做首字节剪枝，helper 不重复剪枝。
// 整词约束：词完全匹配后必须 s[wordLen] 是 \s（否则试下一个词），杜绝前缀误匹配。
static bool matchNumberedWordRe(const std::string &s,
                                const std::vector<std::string> &words,
                                DigitClass dc) {
    const size_t n = s.size();
    for (const auto &word : words) {
        const size_t wl = word.size();
        if (n < wl) continue;                          // 越界保护
        // 逐字节比 word：ASCII 字节用 tolower，非 ASCII 字节直接比
        bool ok = true;
        for (size_t i = 0; i < wl; ++i) {
            unsigned char sc = (unsigned char) s[i];
            unsigned char wc = (unsigned char) word[i];
            // 仅当两者都是 ASCII 字母时做大小写不敏感
            if (sc < 0x80 && wc < 0x80) {
                if (tolower(sc) != tolower(wc)) { ok = false; break; }
            } else {
                if (sc != wc) { ok = false; break; }
            }
        }
        if (!ok) continue;
        // 词完全匹配后，必须紧跟 \s（整词约束）
        if (wl >= n) continue;                          // 词耗尽整个串，无数字位
        if (!isWs((unsigned char) s[wl])) continue;
        size_t p = wl;
        while (p < n && isWs((unsigned char) s[p])) p += 1;   // \s+
        size_t digStart = p;
        size_t adv;
        while ((adv = digitAdvance(s, p, dc)) > 0) p += adv;  // [digits]+
        if (p == digStart) continue;                   // + 至少 1 个数字
        return isWsOrFwspOrEnd(s, p);                  // 尾部约束
    }
    return false;
}

// 形态B helper：词(大小写不敏感) + [ws|end]
static bool matchSpecialWordRe(const std::string &s,
                               const std::vector<std::string> &words) {
    const size_t n = s.size();
    for (const auto &word : words) {
        const size_t wl = word.size();
        if (n < wl) continue;
        bool ok = true;
        for (size_t i = 0; i < wl; ++i) {
            unsigned char sc = (unsigned char) s[i];
            unsigned char wc = (unsigned char) word[i];
            if (sc < 0x80 && wc < 0x80) {
                if (tolower(sc) != tolower(wc)) { ok = false; break; }
            } else {
                if (sc != wc) { ok = false; break; }
            }
        }
        if (!ok) continue;
        // 词完全匹配后，尾部必须 ws/end（整词约束）
        if (isWsOrFwspOrEnd(s, wl)) return true;
    }
    return false;
}

// (6) 法/德/葡/西 + 罗马数字（如 Chapitre 1 / Chapitre IV / Capítulo II / Prolog 3）
// 首字节剪枝：Latin 词首字母分布 c/p/e/k + 重音 É(C3 89)。剪掉绝大多数非西语 <p>。
static bool matchLatinRe(const std::string &s) {
    if (s.empty()) return false;
    unsigned char c = (unsigned char) s[0];
    unsigned char cl = (c >= 'A' && c <= 'Z') ? (unsigned char)(c + 32) : c;
    if (cl != 'c' && cl != 'p' && cl != 'e' && cl != 'k' && c != 0xC3) return false;
    // numbered 词表（形态A）
    static const std::vector<std::string> numbered = {
            "Chapitre", "Kapitel", "Capítulo", "Capitulo"
    };
    // special 词表（形态B）：FR Prologue/Épilogue/Epilogue；DE Prolog/Epilog；
    //                    PT/ES Prólogo/Prologo/Epílogo/Epilogo
    static const std::vector<std::string> special = {
            "Prologue", "Épilogue", "Epilogue",
            "Prolog", "Epilog",
            "Prólogo", "Prologo", "Epílogo", "Epilogo"
    };
    if (matchNumberedWordRe(s, numbered, DIGIT_LATIN)) return true;
    return matchSpecialWordRe(s, special);
}

// (7) 俄语 + ASCII/罗马数字（如 Глава 1 / ГЛАВА I / Глава IV / Пролог）
// 首字节剪枝：所有 RU 词（Title Case + ALL CAPS）首字符都是 0xD0。
//   Глава/ГЛАВА 首 Г=D0 93；Пролог/ПРОЛОГ 首 П=D0 9F；Эпилог/ЭПИЛОГ 首 Э=D0 AD。
// Cyrillic 大小写 lead byte 不同，词表显式存 Title Case + ALL CAPS，不存全小写。
// 数字用 DIGIT_LATIN（0-9 + 罗马数字）：风格化俄语 EPUB 常见「Глава IV」「ГЛАВА I」。
//   Cyrillic І 不与拉丁 I 冲突（前者 2B，后者 1B）；散文「Главное」无「Глава + 空格 + 数字」结构。
static bool matchRussianRe(const std::string &s) {
    if (s.empty() || (unsigned char) s[0] != 0xD0) return false;
    static const std::vector<std::string> numbered = {
            "Глава", "ГЛАВА"
    };
    static const std::vector<std::string> special = {
            "Пролог", "ПРОЛОГ", "Эпилог", "ЭПИЛОГ"
    };
    if (matchNumberedWordRe(s, numbered, DIGIT_LATIN)) return true;
    return matchSpecialWordRe(s, special);
}

// (8) 印地语 + Devanagari/ASCII 数字（如 अध्याय १ / अध्याय 1）
// 首字节剪枝：Devanagari 字符都是 0xE0 开头。
static bool matchHindiRe(const std::string &s) {
    if (s.empty() || (unsigned char) s[0] != 0xE0) return false;
    static const std::vector<std::string> numbered = {
            "अध्याय"
    };
    static const std::vector<std::string> special = {
            "प्रस्तावना"
    };
    if (matchNumberedWordRe(s, numbered, DIGIT_DEVANAGARI)) return true;
    return matchSpecialWordRe(s, special);
}

// (9) 阿拉伯语 + Arabic-Indic/ASCII 数字（如 الفصل ١ / الفصل 1 / مقدمة）
// 首字节剪枝：Arabic 字符 0xD8 或 0xD9 开头。
//   الفصل/باب 以 D8 开头；مقدمة 以 D9 开头。
// 注：UTF-8 字节序与显示方向无关，字节级比较无 RTL 问题。
static bool matchArabicRe(const std::string &s) {
    if (s.empty()) return false;
    unsigned char c = (unsigned char) s[0];
    if (c != 0xD8 && c != 0xD9) return false;
    static const std::vector<std::string> numbered = {
            "الفصل", "باب"
    };
    static const std::vector<std::string> special = {
            "مقدمة"
    };
    if (matchNumberedWordRe(s, numbered, DIGIT_ARABIC_INDIC)) return true;
    return matchSpecialWordRe(s, special);
}

} // namespace chapter_matcher

#endif // HANDYREADER_CHAPTER_MATCHER_H
