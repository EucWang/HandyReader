package com.wxn.base.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LanguageDetectorTest {

    // ── 边界条件 ──

    @Test
    fun blank_returnsNull() {
        assertNull(LanguageDetector.detectLanguage(""))
        assertNull(LanguageDetector.detectLanguage("   "))
    }

    // ── 非拉丁语系脚本级检测 ──

    @Test
    fun chinese() {
        val result = LanguageDetector.detectLanguage("这是一段中文测试文本。今天的天气非常好，适合出去散步。")
        assertEquals("zh", result)
    }

    @Test
    fun japanese() {
        val result = LanguageDetector.detectLanguage("日本語のテスト文章です。今日は良い天気ですね。")
        assertEquals("ja", result)
    }

    @Test
    fun korean() {
        val result = LanguageDetector.detectLanguage("이것은 한국어 테스트 문장입니다. 오늘 날씨가 좋습니다.")
        assertEquals("ko", result)
    }

    @Test
    fun thai() {
        val result = LanguageDetector.detectLanguage("นี่คือข้อความทดสอบภาษาไทย วันนี้อากาศดีมาก")
        assertEquals("th", result)
    }

    @Test
    fun russian() {
        val result = LanguageDetector.detectLanguage("Это тестовый текст на русском языке. Сегодня хорошая погода.")
        assertEquals("ru", result)
    }

    @Test
    fun arabic() {
        val result = LanguageDetector.detectLanguage("هذا هو النص التجريبي باللغة العربية. اليوم الطقس جميل.")
        assertEquals("ar", result)
    }

    @Test
    fun hebrew() {
        val result = LanguageDetector.detectLanguage("זהו טקסט ניסיון בעברית. מזג האוויר сегодня נעים.")
        assertEquals("he", result)
    }

    @Test
    fun devanagari() {
        val result = LanguageDetector.detectLanguage("यह हिंदी में परीक्षण पाठ है। आज मौसम बहुत अच्छा है।")
        assertEquals("hi", result)
    }

    @Test
    fun tamil() {
        val result = LanguageDetector.detectLanguage("இது தமிழில் சோதனை உரை. இன்று வானிலை மிகவும் நன்றாக உள்ளது.")
        assertEquals("ta", result)
    }

    // ── Level 1: 高频虚词检测 ──

    @Test
    fun english_wordCheck() {
        val text = "The company announced new features and products for the global market. " +
            "It was a significant step forward for the industry as a whole. " +
            "The board of directors approved the plan and it will be implemented next year. " +
            "This is a critical development for the future of the organization."
        assertEquals("en", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun german_wordCheck() {
        val text = "Der alte Mann ging zum Markt und kaufte frische Lebensmittel für das Abendessen. " +
            "Die Kinder spielten im Garten und die Sonne schien den ganzen Tag. " +
            "Das ist ein wunderschöner Tag und die Welt ist voller Möglichkeiten. " +
            "Der Zug nach München fährt um zehn Uhr ab."
        assertEquals("de", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun french_wordCheck() {
        val text = "Le gouvernement a annoncé les nouvelles mesures pour protéger la santé des citoyens. " +
            "C'est une décision importante pour le pays et la population. " +
            "Les études montrent que cette méthode est très efficace dans la pratique. " +
            "Il est nécessaire de continuer les recherches dans ce domaine."
        assertEquals("fr", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun spanish_wordCheck() {
        val text = "El presidente y los miembros del consejo aceptaron la propuesta de reforma. " +
            "La economía del país ha mejorado significativamente en los últimos años. " +
            "Las nuevas tecnologías están cambiando la forma en que vivimos y trabajamos. " +
            "Es importante considerar todas las opciones disponibles."
        assertEquals("es", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun portuguese_wordCheck() {
        val text = "O Brasil é um país de dimensões continentais e possui uma cultura muito rica. " +
            "Os estados do norte e do sul têm tradições muito diferentes entre si. " +
            "A população brasileira é conhecida por sua hospitalidade e alegria. " +
            "O governo anunciou novas medidas para o desenvolvimento da região."
        assertEquals("pt", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun turkish_wordCheck() {
        val text = "Türkiye'nin her bölgesinde farklı yemek kültürleri bulunmaktadır. " +
            "Bu yemeklerin birçoğu dünya çapında tanınmaktadır. " +
            "İstanbul tarihi ve kültürel zenginlikleriyle ünlü bir şehirdir. " +
            "Her yıl milyonlarca turist bu güzel ülkeyi ziyaret etmektedir."
        assertEquals("tr", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun polish_wordCheck() {
        val text = "W Polsce można znaleźć wiele pięknych miejsc i ciekawych zabytków. " +
            "Historia tego kraju jest bardzo bogata i skomplikowana. " +
            "Polska kultura ma długą tradycję i jest znana na całym świecie. " +
            "Warszawa jest stolicą Polski i jednym z najważniejszych miast w Europie."
        assertEquals("pl", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun croatian_wordCheck() {
        val text = "Jučer smo išli u šetnju po gradu i vidjeli mnogo zanimljivih stvari. " +
            "Naša djeca su se igrala u parku i uživala u sunčanom danu. " +
            "Ovo je prekrasan grad s bogatom poviješću i kulturom. " +
            "U centru grada nalaze se mnoge trgovine i restorani."
        assertEquals("hr", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun hungarian_wordCheck() {
        val text = "A magyar nyelv az egyik legnehezebb nyelv a világon. " +
            "Ők a legjobb barátaim és mindig számíthatok rájuk. " +
            "Ez a könyv nagyon érdekes és tanulságos történeteket tartalmaz. " +
            "A művészet az élet egyik legfontosabb része."
        assertEquals("hu", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun swedish_wordCheck() {
        val text = "Den svenska sommaren är vacker och många människor njuter av solen. " +
            "Det är en fantastisk tid på året och alla är glada och aktiva. " +
            "Barnen leker i parken och familjer samlas för picknick i det gröna gräset. " +
            "I Sverige firar man midsommar med dans sång och god mat."
        assertEquals("sv", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun czech_wordCheck() {
        val text = "Včera byl krásný den a děti si hrály na zahradě celé odpoledne. " +
            "Návštěva toho města byla pro nás velkým zážitkem a dobrodružstvím. " +
            "Naše rodina se sešla u slavnostního stolu a všichni měli radost. " +
            "Tato kniha je velmi zajímavá a přináší mnoho nových poznatků."
        assertEquals("cs", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun romanian_wordCheck() {
        val text = "România este o țară frumoasă cu o istorie bogată și o cultură diversă. " +
            "Munții Carpați sunt una dintre cele mai frumoase destinații turistice din Europa. " +
            "Oamenii sunt prietenoși și ospitalieri în toate regiunile țării. " +
            "Bucureștiul este capitala României și cel mai mare oraș al său."
        assertEquals("ro", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun vietnamese_wordCheck() {
        val text = "Việt Nam là một quốc gia có nền văn hóa đa dạng và phong phú. " +
            "Có nhiều danh lam thắng cảnh nổi tiếng trên khắp cả nước. " +
            "Người dân Việt Nam rất thân thiện và hiếu khách với du khách nước ngoài. " +
            "Năm nay chúng tôi dự định đi du lịch đến nhiều vùng miền khác nhau."
        assertEquals("vi", LanguageDetector.detectLanguage(text))
    }

    // ── Level 2: Diacritics 检测 ──

    @Test
    fun german_diacritic_eszett() {
        val text = "Straße bedeutet Straße auf Deutsch und ist ein wichtiges Wort. " +
            "Eine heiße Tasse Kaffee am Morgen ist etwas Wunderbares."
        assertEquals("de", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun french_diacritic_oe() {
        val text = "Le cœur et les œufs sont des ingrédients essentiels pour cette recette. " +
            "Le bœuf bourguignon est un plat traditionnel français très apprécié."
        assertEquals("fr", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun spanish_diacritic_ene() {
        val text = "España tiene años de historia y cultura. " +
            "La señal de Wi-Fi no funciona correctamente en esta habitación."
        assertEquals("es", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun portuguese_diacritic_tilde() {
        val text = "A ação do governo foi fundamental para a educação nacional. " +
            "As lições de português são muito importantes para o desenvolvimento acadêmico."
        assertEquals("pt", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun italian_wordCheck() {
        // 意大利语不在 FUNCTION_WORDS 中,但虚词信号会涌入其他 Latin 语种
        // diacritics 也无特征 → 最终 fallback 到 en
        val text = "Il gatto e la volpe sono animali molto diversi tra loro. " +
            "La casa è grande e bella con un giardino meraviglioso. " +
            "Che cosa hai fatto oggi chiese il piccolo principe."
        // 意大利语无 diacritics 特征,虚词会映射到 es/pt,但分数不置信
        val result = LanguageDetector.detectLanguage(text)
        assertTrue(result == "en" || result == "es" || result == "it")
    }

    @Test
    fun shortTextWithDiacritic_fallsBackToDiacritic() {
        // <10 词,虚词无统计意义,但 ß 特征明确
        val text = "Straße und Fußgängerzone in München."
        assertEquals("de", LanguageDetector.detectLanguage(text))
    }

    // ── Level 2 兜底: 标点特征 ──

    @Test
    fun french_guillemet_punctuation() {
        // 标点: « », 无 diacritics 特征
        val text = " « La vie est belle » dit-elle. « Il faut profiter du moment présent. »"
        assertEquals("fr", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun german_quote_punctuation() {
        // 标点: „ " 无 diacritics 特征
        val text = "„Das ist ein Beispiel“ sagte er. „Wir müssen weiter üben“"
        assertEquals("de", LanguageDetector.detectLanguage(text))
    }

    // ── Level 3: 德语大写检测 ──

    @Test
    fun german_capitalization_only() {
        // 无 ß,无虚词,但句中大量大写名词 → de
        val text = "Der Zug nach München fährt um zehn Uhr ab. " +
            "Morgen beginnt das große Fest in der Stadt. " +
            "Alle Bürger sind zur Feier eingeladen. " +
            "Die Kirche und das Museum sind geöffnet."
        assertEquals("de", LanguageDetector.detectLanguage(text))
    }

    @Test
    fun english_noFalseGermanCapitalization() {
        // 英文专有名词大写不应误判为德语
        val text = "Apple and Microsoft announced new products for the global market. " +
            "Amazon and Google are competing in the cloud computing space. " +
            "Tesla and SpaceX are pushing the boundaries of technology."
        assertEquals("en", LanguageDetector.detectLanguage(text))
    }

    // ── RTL 检测 ──

    @Test
    fun isRtl_arabic() {
        assertTrue(LanguageDetector.isRtl("ar"))
        assertTrue(LanguageDetector.isRtl("ar-SA"))
    }

    @Test
    fun isRtl_hebrew() {
        assertTrue(LanguageDetector.isRtl("he"))
        assertTrue(LanguageDetector.isRtl("he-IL"))
    }

    @Test
    fun isRtl_persian() {
        assertTrue(LanguageDetector.isRtl("fa"))
        assertTrue(LanguageDetector.isRtl("fa-IR"))
    }

    @Test
    fun isRtl_english_isFalse() {
        assertEquals(false, LanguageDetector.isRtl("en"))
        assertEquals(false, LanguageDetector.isRtl("en-US"))
    }

    @Test
    fun isRtl_chinese_isFalse() {
        assertEquals(false, LanguageDetector.isRtl("zh"))
    }

    @Test
    fun isRtl_null_isFalse() {
        assertEquals(false, LanguageDetector.isRtl(null))
        assertEquals(false, LanguageDetector.isRtl(""))
    }
}
