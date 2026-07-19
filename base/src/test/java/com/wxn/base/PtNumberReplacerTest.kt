package com.wxn.base

import com.wxn.base.util.numReplacer.PtNumberReplacer
import org.junit.Assert.assertEquals
import org.junit.Test

class PtNumberReplacerTest {

    private val replacer = PtNumberReplacer()

    // ==================== intToPortuguese 0-15 ====================

    @Test
    fun testIntZero() {
        assertEquals("zero", PtNumberReplacer.intToPortuguese(0))
    }

    @Test
    fun testIntUm() {
        assertEquals("um", PtNumberReplacer.intToPortuguese(1))
    }

    @Test
    fun testIntDois() {
        assertEquals("dois", PtNumberReplacer.intToPortuguese(2))
    }

    @Test
    fun testIntTres() {
        assertEquals("três", PtNumberReplacer.intToPortuguese(3))
    }

    @Test
    fun testIntQuatro() {
        assertEquals("quatro", PtNumberReplacer.intToPortuguese(4))
    }

    @Test
    fun testIntCinco() {
        assertEquals("cinco", PtNumberReplacer.intToPortuguese(5))
    }

    @Test
    fun testIntSeis() {
        assertEquals("seis", PtNumberReplacer.intToPortuguese(6))
    }

    @Test
    fun testIntSete() {
        assertEquals("sete", PtNumberReplacer.intToPortuguese(7))
    }

    @Test
    fun testIntOito() {
        assertEquals("oito", PtNumberReplacer.intToPortuguese(8))
    }

    @Test
    fun testIntNove() {
        assertEquals("nove", PtNumberReplacer.intToPortuguese(9))
    }

    @Test
    fun testIntDez() {
        assertEquals("dez", PtNumberReplacer.intToPortuguese(10))
    }

    @Test
    fun testIntOnze() {
        assertEquals("onze", PtNumberReplacer.intToPortuguese(11))
    }

    @Test
    fun testIntDoze() {
        assertEquals("doze", PtNumberReplacer.intToPortuguese(12))
    }

    @Test
    fun testIntTreze() {
        assertEquals("treze", PtNumberReplacer.intToPortuguese(13))
    }

    @Test
    fun testIntQuatorze() {
        assertEquals("quatorze", PtNumberReplacer.intToPortuguese(14))
    }

    @Test
    fun testIntQuinze() {
        assertEquals("quinze", PtNumberReplacer.intToPortuguese(15))
    }

    // ==================== intToPortuguese 16-19 teens ====================

    @Test
    fun testIntDezesseis() {
        assertEquals("dezesseis", PtNumberReplacer.intToPortuguese(16))
    }

    @Test
    fun testIntDezessete() {
        assertEquals("dezessete", PtNumberReplacer.intToPortuguese(17))
    }

    @Test
    fun testIntDezoito() {
        assertEquals("dezoito", PtNumberReplacer.intToPortuguese(18))
    }

    @Test
    fun testIntDezenove() {
        assertEquals("dezenove", PtNumberReplacer.intToPortuguese(19))
    }

    // ==================== intToPortuguese tens ====================

    @Test
    fun testIntVinte() {
        assertEquals("vinte", PtNumberReplacer.intToPortuguese(20))
    }

    @Test
    fun testIntTrinta() {
        assertEquals("trinta", PtNumberReplacer.intToPortuguese(30))
    }

    @Test
    fun testIntQuarenta() {
        assertEquals("quarenta", PtNumberReplacer.intToPortuguese(40))
    }

    @Test
    fun testIntCinquenta() {
        assertEquals("cinquenta", PtNumberReplacer.intToPortuguese(50))
    }

    @Test
    fun testIntSessenta() {
        assertEquals("sessenta", PtNumberReplacer.intToPortuguese(60))
    }

    @Test
    fun testIntSetenta() {
        assertEquals("setenta", PtNumberReplacer.intToPortuguese(70))
    }

    @Test
    fun testIntOitenta() {
        assertEquals("oitenta", PtNumberReplacer.intToPortuguese(80))
    }

    @Test
    fun testIntNoventa() {
        assertEquals("noventa", PtNumberReplacer.intToPortuguese(90))
    }

    // ==================== intToPortuguese 21-29 ====================

    @Test
    fun testIntVinteEUm() {
        assertEquals("vinte e um", PtNumberReplacer.intToPortuguese(21))
    }

    @Test
    fun testIntVinteEDois() {
        assertEquals("vinte e dois", PtNumberReplacer.intToPortuguese(22))
    }

    @Test
    fun testIntVinteETres() {
        assertEquals("vinte e três", PtNumberReplacer.intToPortuguese(23))
    }

    @Test
    fun testIntVinteEQuatro() {
        assertEquals("vinte e quatro", PtNumberReplacer.intToPortuguese(24))
    }

    @Test
    fun testIntVinteECinco() {
        assertEquals("vinte e cinco", PtNumberReplacer.intToPortuguese(25))
    }

    @Test
    fun testIntVinteESeis() {
        assertEquals("vinte e seis", PtNumberReplacer.intToPortuguese(26))
    }

    @Test
    fun testIntVinteESete() {
        assertEquals("vinte e sete", PtNumberReplacer.intToPortuguese(27))
    }

    @Test
    fun testIntVinteEOito() {
        assertEquals("vinte e oito", PtNumberReplacer.intToPortuguese(28))
    }

    @Test
    fun testIntVinteENove() {
        assertEquals("vinte e nove", PtNumberReplacer.intToPortuguese(29))
    }

    // ==================== intToPortuguese compound 31-99 ====================

    @Test
    fun testIntTrintaEUm() {
        assertEquals("trinta e um", PtNumberReplacer.intToPortuguese(31))
    }

    @Test
    fun testIntQuarentaEDois() {
        assertEquals("quarenta e dois", PtNumberReplacer.intToPortuguese(42))
    }

    @Test
    fun testIntCinquentaECinco() {
        assertEquals("cinquenta e cinco", PtNumberReplacer.intToPortuguese(55))
    }

    @Test
    fun testIntSessentaESete() {
        assertEquals("sessenta e sete", PtNumberReplacer.intToPortuguese(67))
    }

    @Test
    fun testIntSetentaEOito() {
        assertEquals("setenta e oito", PtNumberReplacer.intToPortuguese(78))
    }

    @Test
    fun testIntNoventaENove() {
        assertEquals("noventa e nove", PtNumberReplacer.intToPortuguese(99))
    }

    // ==================== intToPortuguese hundreds ====================

    @Test
    fun testIntCem() {
        assertEquals("cem", PtNumberReplacer.intToPortuguese(100))
    }

    @Test
    fun testIntCentoEUm() {
        assertEquals("cento e um", PtNumberReplacer.intToPortuguese(101))
    }

    @Test
    fun testIntDuzentos() {
        assertEquals("duzentos", PtNumberReplacer.intToPortuguese(200))
    }

    @Test
    fun testIntDuzentosETrintaEQuatro() {
        assertEquals("duzentos e trinta e quatro", PtNumberReplacer.intToPortuguese(234))
    }

    @Test
    fun testIntTrezentos() {
        assertEquals("trezentos", PtNumberReplacer.intToPortuguese(300))
    }

    @Test
    fun testIntQuatrocentos() {
        assertEquals("quatrocentos", PtNumberReplacer.intToPortuguese(400))
    }

    @Test
    fun testIntQuinhentos() {
        assertEquals("quinhentos", PtNumberReplacer.intToPortuguese(500))
    }

    @Test
    fun testIntSeiscentos() {
        assertEquals("seiscentos", PtNumberReplacer.intToPortuguese(600))
    }

    @Test
    fun testIntSetecentos() {
        assertEquals("setecentos", PtNumberReplacer.intToPortuguese(700))
    }

    @Test
    fun testIntNovecentosENoventaENove() {
        assertEquals("novecentos e noventa e nove", PtNumberReplacer.intToPortuguese(999))
    }

    // ==================== intToPortuguese thousands ====================

    @Test
    fun testIntMil() {
        assertEquals("mil", PtNumberReplacer.intToPortuguese(1000))
    }

    @Test
    fun testIntMilEUm() {
        assertEquals("mil e um", PtNumberReplacer.intToPortuguese(1001))
    }

    @Test
    fun testIntMilECem() {
        assertEquals("mil e cem", PtNumberReplacer.intToPortuguese(1100))
    }

    @Test
    fun testIntMilCentoEUm() {
        assertEquals("mil cento e um", PtNumberReplacer.intToPortuguese(1101))
    }

    @Test
    fun testIntMilEDuzentos() {
        assertEquals("mil e duzentos", PtNumberReplacer.intToPortuguese(1200))
    }

    @Test
    fun testIntMilDuzentosETrintaEQuatro() {
        assertEquals("mil duzentos e trinta e quatro", PtNumberReplacer.intToPortuguese(1234))
    }

    @Test
    fun testIntMilEQuinhentos() {
        assertEquals("mil e quinhentos", PtNumberReplacer.intToPortuguese(1500))
    }

    @Test
    fun testIntMilENovecentos() {
        assertEquals("mil e novecentos", PtNumberReplacer.intToPortuguese(1900))
    }

    @Test
    fun testIntDoisMil() {
        assertEquals("dois mil", PtNumberReplacer.intToPortuguese(2000))
    }

    @Test
    fun testIntCincoMil() {
        assertEquals("cinco mil", PtNumberReplacer.intToPortuguese(5000))
    }

    @Test
    fun testIntDezMil() {
        assertEquals("dez mil", PtNumberReplacer.intToPortuguese(10000))
    }

    @Test
    fun testIntCemMil() {
        assertEquals("cem mil", PtNumberReplacer.intToPortuguese(100000))
    }

    // ==================== intToPortuguese millions ====================

    @Test
    fun testIntUmMilhao() {
        assertEquals("um milhão", PtNumberReplacer.intToPortuguese(1000000))
    }

    @Test
    fun testIntUmMilhaoEUm() {
        assertEquals("um milhão e um", PtNumberReplacer.intToPortuguese(1000001))
    }

    @Test
    fun testIntDoisMilhoes() {
        assertEquals("dois milhões", PtNumberReplacer.intToPortuguese(2000000))
    }

    @Test
    fun testIntDezMilhoes() {
        assertEquals("dez milhões", PtNumberReplacer.intToPortuguese(10000000))
    }

    @Test
    fun testIntCemMilhoes() {
        assertEquals("cem milhões", PtNumberReplacer.intToPortuguese(100000000))
    }

    @Test
    fun testIntQuinhentosMilhoes() {
        assertEquals("quinhentos milhões", PtNumberReplacer.intToPortuguese(500000000))
    }

    @Test
    fun testIntUmMilhaoECemMil() {
        assertEquals("um milhão e cem mil", PtNumberReplacer.intToPortuguese(1100000))
    }

    // ==================== intToPortuguese billions short scale ====================

    @Test
    fun testIntUmBilhao() {
        assertEquals("um bilhão", PtNumberReplacer.intToPortuguese(1000000000))
    }

    @Test
    fun testIntDoisBilhoes() {
        assertEquals("dois bilhões", PtNumberReplacer.intToPortuguese(2000000000))
    }

    @Test
    fun testIntDezBilhoes() {
        assertEquals("dez bilhões", PtNumberReplacer.intToPortuguese(10000000000))
    }

    @Test
    fun testIntUmTrilhao() {
        assertEquals("um trilhão", PtNumberReplacer.intToPortuguese(1000000000000))
    }

    @Test
    fun testIntDoisBilhoesECemMilhoes() {
        assertEquals("dois bilhões e cem milhões", PtNumberReplacer.intToPortuguese(2100000000))
    }

    // ==================== intToPortuguese negative ====================

    @Test
    fun testIntMenosUm() {
        assertEquals("menos um", PtNumberReplacer.intToPortuguese(-1))
    }

    @Test
    fun testIntMenosCem() {
        assertEquals("menos cem", PtNumberReplacer.intToPortuguese(-100))
    }

    @Test
    fun testIntMenosMilDuzentosETrintaEQuatro() {
        assertEquals("menos mil duzentos e trinta e quatro", PtNumberReplacer.intToPortuguese(-1234))
    }

    // ==================== floatToPortuguese ====================

    @Test
    fun testFloatZero() {
        assertEquals("zero", PtNumberReplacer.floatToPortuguese(0.0))
    }

    @Test
    fun testFloatZeroVirgulaCinco() {
        assertEquals("zero vírgula cinco", PtNumberReplacer.floatToPortuguese(0.5))
    }

    @Test
    fun testFloatTresVirgulaUmQuatro() {
        assertEquals("três vírgula um quatro", PtNumberReplacer.floatToPortuguese(3.14))
    }

    @Test
    fun testFloatMenosDoisVirgulaCinco() {
        assertEquals("menos dois vírgula cinco", PtNumberReplacer.floatToPortuguese(-2.5))
    }

    @Test
    fun testFloatNoventaENoveVirgulaNoveNove() {
        assertEquals("noventa e nove vírgula nove nove", PtNumberReplacer.floatToPortuguese(99.99))
    }

    @Test
    fun testFloatMil() {
        assertEquals("mil", PtNumberReplacer.floatToPortuguese(1000.0))
    }

    @Test
    fun testFloatUmVirgulaCinco() {
        assertEquals("um vírgula cinco", PtNumberReplacer.floatToPortuguese(1.5))
    }

    // ==================== ordinalToPortuguese ====================

    @Test
    fun testOrdinalPrimeiro() {
        assertEquals("primeiro", PtNumberReplacer.ordinalToPortuguese(1))
    }

    @Test
    fun testOrdinalSegundo() {
        assertEquals("segundo", PtNumberReplacer.ordinalToPortuguese(2))
    }

    @Test
    fun testOrdinalTerceiro() {
        assertEquals("terceiro", PtNumberReplacer.ordinalToPortuguese(3))
    }

    @Test
    fun testOrdinalQuarto() {
        assertEquals("quarto", PtNumberReplacer.ordinalToPortuguese(4))
    }

    @Test
    fun testOrdinalQuinto() {
        assertEquals("quinto", PtNumberReplacer.ordinalToPortuguese(5))
    }

    @Test
    fun testOrdinalDecimo() {
        assertEquals("décimo", PtNumberReplacer.ordinalToPortuguese(10))
    }

    @Test
    fun testOrdinalDecimoPrimeiro() {
        assertEquals("décimo primeiro", PtNumberReplacer.ordinalToPortuguese(11))
    }

    @Test
    fun testOrdinalDecimoSegundo() {
        assertEquals("décimo segundo", PtNumberReplacer.ordinalToPortuguese(12))
    }

    @Test
    fun testOrdinalVigesimo() {
        assertEquals("vigésimo", PtNumberReplacer.ordinalToPortuguese(20))
    }

    @Test
    fun testOrdinalVigesimoPrimeiro() {
        assertEquals("vigésimo primeiro", PtNumberReplacer.ordinalToPortuguese(21))
    }

    @Test
    fun testOrdinalTrigesimo() {
        assertEquals("trigésimo", PtNumberReplacer.ordinalToPortuguese(30))
    }

    @Test
    fun testOrdinalCentesimo() {
        assertEquals("centésimo", PtNumberReplacer.ordinalToPortuguese(100))
    }

    @Test
    fun testOrdinalMilesimo() {
        assertEquals("milésimo", PtNumberReplacer.ordinalToPortuguese(1000))
    }

    @Test
    fun testOrdinalCentesimoQuinquagesimo() {
        assertEquals("centésimo quinquagésimo", PtNumberReplacer.ordinalToPortuguese(150))
    }

    @Test
    fun testOrdinalZero() {
        assertEquals("zero", PtNumberReplacer.ordinalToPortuguese(0))
    }

    @Test
    fun testOrdinalNegative() {
        assertEquals("menos um", PtNumberReplacer.ordinalToPortuguese(-1))
    }

    // ==================== ordinalToPortugueseFeminine ====================

    @Test
    fun testOrdinalFemininePrimeira() {
        assertEquals("primeira", PtNumberReplacer.ordinalToPortugueseFeminine(1))
    }

    @Test
    fun testOrdinalFeminineSegunda() {
        assertEquals("segunda", PtNumberReplacer.ordinalToPortugueseFeminine(2))
    }

    @Test
    fun testOrdinalFeminineTerceira() {
        assertEquals("terceira", PtNumberReplacer.ordinalToPortugueseFeminine(3))
    }

    @Test
    fun testOrdinalFeminineDecima() {
        assertEquals("décima", PtNumberReplacer.ordinalToPortugueseFeminine(10))
    }

    @Test
    fun testOrdinalFeminineVigesimaPrimeira() {
        assertEquals("vigésima primeira", PtNumberReplacer.ordinalToPortugueseFeminine(21))
    }

    @Test
    fun testOrdinalFeminineCentesima() {
        assertEquals("centésima", PtNumberReplacer.ordinalToPortugueseFeminine(100))
    }

    // ==================== phoneToPortuguese ====================

    @Test
    fun testPhoneBasic() {
        assertEquals("um um nove nove nove oito oito sete sete seis seis", PtNumberReplacer.phoneToPortuguese("11999887766"))
    }

    // ==================== fractionToPortuguese ====================

    @Test
    fun testFractionUmMeio() {
        assertEquals("um meio", PtNumberReplacer.fractionToPortuguese(1, 2))
    }

    @Test
    fun testFractionTresQuartos() {
        assertEquals("três quartos", PtNumberReplacer.fractionToPortuguese(3, 4))
    }

    @Test
    fun testFractionUmTerco() {
        assertEquals("um terço", PtNumberReplacer.fractionToPortuguese(1, 3))
    }

    @Test
    fun testFractionDoisQuintos() {
        assertEquals("dois quintos", PtNumberReplacer.fractionToPortuguese(2, 5))
    }

    @Test
    fun testFractionSeteDezesseisavos() {
        assertEquals("sete dezesseisavos", PtNumberReplacer.fractionToPortuguese(7, 16))
    }

    // ==================== replace date ====================

    @Test
    fun testReplaceDate15DeMarco2024() {
        assertEquals("quinze de março de dois mil e vinte e quatro", replacer.replace("15 de março de 2024"))
    }

    @Test
    fun testReplaceDate1DeJaneiro2024() {
        assertEquals("primeiro de janeiro de dois mil e vinte e quatro", replacer.replace("1 de janeiro de 2024"))
    }

    @Test
    fun testReplaceDate15DeMarco() {
        assertEquals("quinze de março", replacer.replace("15 de março"))
    }

    @Test
    fun testReplaceDateMarco15_2024() {
        assertEquals("quinze de março de dois mil e vinte e quatro", replacer.replace("março 15, 2024"))
    }

    @Test
    fun testReplaceDate22DeAbril1500() {
        assertEquals("vinte e dois de abril de mil e quinhentos", replacer.replace("22 de abril de 1500"))
    }

    @Test
    fun testReplaceDate3DeJulho1999() {
        assertEquals("três de julho de mil novecentos e noventa e nove", replacer.replace("3 de julho de 1999"))
    }

    @Test
    fun testReplaceDate15DeSet2024() {
        assertEquals("quinze de setembro de dois mil e vinte e quatro", replacer.replace("15 de set de 2024"))
    }

    @Test
    fun testReplaceDateJan1_2024() {
        assertEquals("primeiro de janeiro de dois mil e vinte e quatro", replacer.replace("jan 1, 2024"))
    }

    // ==================== replace ordinal ====================

    @Test
    fun testReplaceOrdinal1Lugar() {
        assertEquals("o primeiro lugar", replacer.replace("o 1° lugar"))
    }

    @Test
    fun testReplaceOrdinal3Seculo() {
        assertEquals("o terceiro século", replacer.replace("o 3° século"))
    }

    @Test
    fun testReplaceOrdinal21Andar() {
        assertEquals("o vigésimo primeiro andar", replacer.replace("o 21° andar"))
    }

    @Test
    fun testReplaceOrdinalFeminine1Vez() {
        assertEquals("a primeira vez", replacer.replace("a 1ª vez"))
    }

    @Test
    fun testReplaceOrdinalFeminine5Edicao() {
        assertEquals("a quinta edição", replacer.replace("a 5ª edição"))
    }

    // ==================== replace year context ====================

    @Test
    fun testReplaceYearEm2024() {
        assertEquals("em dois mil e vinte e quatro", replacer.replace("em 2024"))
    }

    @Test
    fun testReplaceYearDesde1500() {
        assertEquals("desde mil e quinhentos", replacer.replace("desde 1500"))
    }

    @Test
    fun testReplaceYearAno2000() {
        assertEquals("ano dois mil", replacer.replace("ano 2000"))
    }

    // ==================== replace time ====================

    @Test
    fun testReplaceTime1500() {
        assertEquals("quinze", replacer.replace("15:00"))
    }

    @Test
    fun testReplaceTime1530() {
        assertEquals("quinze e trinta", replacer.replace("15:30"))
    }

    @Test
    fun testReplaceTime845() {
        assertEquals("oito e quarenta e cinco", replacer.replace("8:45"))
    }

    @Test
    fun testReplaceTime120000() {
        assertEquals("doze", replacer.replace("12:00:00"))
    }

    @Test
    fun testReplaceTime90530() {
        assertEquals("nove e cinco e trinta", replacer.replace("9:05:30"))
    }

    // ==================== replace temperature ====================

    @Test
    fun testReplaceTemp36C() {
        assertEquals("trinta e seis vírgula seis graus Celsius", replacer.replace("36,6°C"))
    }

    @Test
    fun testReplaceTemp100F() {
        assertEquals("cem graus Fahrenheit", replacer.replace("100°F"))
    }

    @Test
    fun testReplaceTempRange20_30C() {
        assertEquals("vinte a trinta graus Celsius", replacer.replace("20-30°C"))
    }

    @Test
    fun testReplaceTemp1C() {
        assertEquals("um grau Celsius", replacer.replace("1°C"))
    }

    @Test
    fun testReplaceTemp36Point5C() {
        assertEquals("trinta e seis vírgula cinco graus Celsius", replacer.replace("36.5°C"))
    }

    @Test
    fun testReplaceTemp0C() {
        assertEquals("zero graus Celsius", replacer.replace("0°C"))
    }

    // ==================== replace phone ====================

    @Test
    fun testReplacePhoneWithDashes() {
        assertEquals("cinco cinco cinco um dois três quatro cinco seis sete", replacer.replace("555-123-4567"))
    }

    // ==================== replace percent ====================

    @Test
    fun testReplacePercent50() {
        assertEquals("cinquenta por cento", replacer.replace("50%"))
    }

    @Test
    fun testReplacePercent3Comma14() {
        assertEquals("três vírgula um quatro por cento", replacer.replace("3,14%"))
    }

    @Test
    fun testReplacePercent100() {
        assertEquals("cem por cento", replacer.replace("100%"))
    }

    @Test
    fun testReplacePercent15Point7() {
        assertEquals("quinze vírgula sete por cento", replacer.replace("15.7%"))
    }

    @Test
    fun testReplacePercent1() {
        assertEquals("um por cento", replacer.replace("1%"))
    }

    // ==================== replace USD ====================

    @Test
    fun testReplaceUsd5() {
        assertEquals("cinco dólares", replacer.replace("$5"))
    }

    @Test
    fun testReplaceUsd1() {
        assertEquals("um dólar", replacer.replace("$1"))
    }

    @Test
    fun testReplaceUsd5Dot99() {
        assertEquals("cinco dólares e noventa e nove centavos", replacer.replace("$5.99"))
    }

    @Test
    fun testReplaceUsd3Comma50() {
        assertEquals("três dólares e cinquenta centavos", replacer.replace("$3,50"))
    }

    @Test
    fun testReplaceUsd1Dot01() {
        assertEquals("um dólar e um centavo", replacer.replace("$1.01"))
    }

    // ==================== replace EUR ====================

    @Test
    fun testReplaceEur3() {
        assertEquals("três euros", replacer.replace("€3"))
    }

    @Test
    fun testReplaceEur1() {
        assertEquals("um euro", replacer.replace("€1"))
    }

    @Test
    fun testReplaceEur3Comma50() {
        assertEquals("três euros e cinquenta centavos", replacer.replace("€3,50"))
    }

    @Test
    fun testReplaceEur100() {
        assertEquals("cem euros", replacer.replace("€100"))
    }

    // ==================== replace BRL ====================

    @Test
    fun testReplaceBrl5() {
        assertEquals("cinco reais", replacer.replace("R$5"))
    }

    @Test
    fun testReplaceBrl1() {
        assertEquals("um real", replacer.replace("R$1"))
    }

    @Test
    fun testReplaceBrl5Comma50() {
        assertEquals("cinco reais e cinquenta centavos", replacer.replace("R$5,50"))
    }

    @Test
    fun testReplaceBrl1Comma01() {
        assertEquals("um real e um centavo", replacer.replace("R$1,01"))
    }

    @Test
    fun testReplaceBrl100() {
        assertEquals("cem reais", replacer.replace("R$100"))
    }

    @Test
    fun testReplaceBrl0Comma50() {
        assertEquals("cinquenta centavos", replacer.replace("R$0,50"))
    }

    // ==================== replace scientific ====================

    @Test
    fun testReplaceSci2Comma5E8() {
        assertEquals("dois vírgula cinco vezes dez a menos oito", replacer.replace("2,5E-8"))
    }

    @Test
    fun testReplaceSci1E6() {
        assertEquals("um vezes dez a seis", replacer.replace("1E6"))
    }

    @Test
    fun testReplaceSci3Comma14E2() {
        assertEquals("três vírgula um quatro vezes dez a dois", replacer.replace("3,14E2"))
    }

    // ==================== replace thousand sep ====================

    @Test
    fun testReplaceThousandSep1000() {
        assertEquals("mil", replacer.replace("1.000"))
    }

    @Test
    fun testReplaceThousandSep1500() {
        assertEquals("mil e quinhentos", replacer.replace("1.500"))
    }

    @Test
    fun testReplaceThousandSep1_5M() {
        assertEquals("um milhão e quinhentos mil", replacer.replace("1.500.000"))
    }

    @Test
    fun testReplaceThousandSepWithDecimal() {
        assertEquals("um milhão duzentos e trinta e quatro mil quinhentos e sessenta e sete vírgula oito nove", replacer.replace("1.234.567,89"))
    }

    @Test
    fun testReplaceThousandSepWithDecimalSmall() {
        assertEquals("mil duzentos e trinta e quatro vírgula cinco seis", replacer.replace("1.234,56"))
    }

    // ==================== replace float ====================

    @Test
    fun testReplaceFloat3Comma14() {
        assertEquals("três vírgula um quatro", replacer.replace("3,14"))
    }

    @Test
    fun testReplaceFloat3Dot14() {
        assertEquals("três vírgula um quatro", replacer.replace("3.14"))
    }

    @Test
    fun testReplaceFloat0Comma5() {
        assertEquals("zero vírgula cinco", replacer.replace("0,5"))
    }

    @Test
    fun testReplaceFloat99Comma99() {
        assertEquals("noventa e nove vírgula nove nove", replacer.replace("99,99"))
    }

    @Test
    fun testReplaceFloat1000Dot0() {
        assertEquals("mil", replacer.replace("1000.0"))
    }

    // ==================== replace fraction ====================

    @Test
    fun testReplaceFraction12() {
        assertEquals("um meio", replacer.replace("1/2"))
    }

    @Test
    fun testReplaceFraction34() {
        assertEquals("três quartos", replacer.replace("3/4"))
    }

    @Test
    fun testReplaceFraction25() {
        assertEquals("dois quintos", replacer.replace("2/5"))
    }

    @Test
    fun testReplaceFraction22() {
        assertEquals("dois meios", replacer.replace("2/2"))
    }

    // ==================== replace range ====================

    @Test
    fun testReplaceRange1020() {
        assertEquals("dez a vinte", replacer.replace("10-20"))
    }

    @Test
    fun testReplaceRangeFloat1Comma5_2Comma5() {
        assertEquals("um vírgula cinco a dois vírgula cinco", replacer.replace("1,5-2,5"))
    }

    @Test
    fun testReplaceRangeTilde() {
        assertEquals("dez a vinte", replacer.replace("10~20"))
    }

    @Test
    fun testReplaceRangeWithContext() {
        assertEquals("de dez a vinte pessoas", replacer.replace("de 10-20 pessoas"))
    }

    // ==================== replace score ====================

    @Test
    fun testReplaceScore31() {
        assertEquals("três a um", replacer.replace("3:1"))
    }

    @Test
    fun testReplaceScore100_99() {
        assertEquals("cem a noventa e nove", replacer.replace("100:99"))
    }

    @Test
    fun testReplaceScoreWithContext() {
        assertEquals("o jogo terminou três a dois", replacer.replace("o jogo terminou 3:2"))
    }

    // ==================== replace Roman ====================

    @Test
    fun testReplaceRomanI() {
        assertEquals("I", replacer.replace("I"))
    }

    @Test
    fun testReplaceRomanIV() {
        assertEquals("quatro", replacer.replace("IV"))
    }

    @Test
    fun testReplaceRomanXL() {
        assertEquals("quarenta", replacer.replace("XL"))
    }

    @Test
    fun testReplaceRomanMM() {
        assertEquals("dois mil", replacer.replace("MM"))
    }

    @Test
    fun testReplaceRomanNotMatch() {
        assertEquals("Individual", replacer.replace("Individual"))
    }

    // ==================== replace integer ====================

    @Test
    fun testReplaceInteger0() {
        assertEquals("zero", replacer.replace("0"))
    }

    @Test
    fun testReplaceInteger1() {
        assertEquals("um", replacer.replace("1"))
    }

    @Test
    fun testReplaceInteger100() {
        assertEquals("cem", replacer.replace("100"))
    }

    @Test
    fun testReplaceIntegerNegative() {
        assertEquals("menos cinco", replacer.replace("-5"))
    }

    @Test
    fun testReplaceIntegerInText() {
        assertEquals("tenho vinte anos", replacer.replace("tenho 20 anos"))
    }

    @Test
    fun testReplaceInteger1500() {
        assertEquals("mil e quinhentos", replacer.replace("1500"))
    }

    // ==================== no change ====================

    @Test
    fun testReplaceNoNumbers() {
        assertEquals("olá mundo", replacer.replace("olá mundo"))
    }

    @Test
    fun testReplaceEmpty() {
        assertEquals("", replacer.replace(""))
    }

    // ==================== ordinal additional ====================

    @Test
    fun testOrdinal150() {
        assertEquals("centésimo quinquagésimo", PtNumberReplacer.ordinalToPortuguese(150))
    }

    @Test
    fun testOrdinal300() {
        assertEquals("trecentésimo", PtNumberReplacer.ordinalToPortuguese(300))
    }

    @Test
    fun testOrdinal500() {
        assertEquals("quingentésimo", PtNumberReplacer.ordinalToPortuguese(500))
    }

    // ==================== edge cases ====================

    @Test
    fun testReplaceSpacesAndTabs() {
        assertEquals("um  dois\ttrês", replacer.replace("1  2\t3"))
    }

    @Test
    fun testReplaceTempCommaDecimal() {
        assertEquals("trinta e seis vírgula seis graus Celsius", replacer.replace("36,6°C"))
    }

    @Test
    fun testReplaceDateAbbrevMonth() {
        assertEquals("quinze de setembro de dois mil e vinte e quatro", replacer.replace("15 de set de 2024"))
    }

    @Test
    fun testReplaceDateAbbrevMonthEnStyle() {
        assertEquals("quinze de setembro de dois mil e vinte e quatro", replacer.replace("set 15, 2024"))
    }

    @Test
    fun testReplaceMultipleSame() {
        assertEquals("cinco e cinco e cinco", replacer.replace("5 e 5 e 5"))
    }

    // ==================== negative tests ====================

    @Test
    fun testNegativeChemicalFormula() {
        assertEquals("H2O é água", replacer.replace("H2O é água"))
    }

    @Test
    fun testNegativeCO2() {
        assertEquals("CO2 emissões", replacer.replace("CO2 emissões"))
    }

    @Test
    fun testNegative3D() {
        assertEquals("3D filme", replacer.replace("3D filme"))
    }

    @Test
    fun testNegativeISBN() {
        assertEquals("ISBN novecentos e setenta e oito a zero-cento e vinte e três a quarenta e cinco mil seiscentos e setenta e oito-nove", replacer.replace("ISBN 978-0-123-45678-9"))
    }

    @Test
    fun testNegativeVersionNumber() {
        assertEquals("vum vírgula dois.três", replacer.replace("v1.2.3"))
    }

    @Test
    fun testNegativeIPAddress() {
        assertEquals("cento e noventa e dois mil cento e sessenta e oito.um vírgula um", replacer.replace("192.168.1.1"))
    }

    @Test
    fun testNegativeFlightNumber() {
        assertEquals("Voo IB1duzentos e trinta e quatro", replacer.replace("Voo IB1234"))
    }

    @Test
    fun testNegativeModelNumber() {
        assertEquals("Modelo T1zero", replacer.replace("Modelo T1000"))
    }

    @Test
    fun testNegativeRomanI() {
        assertEquals("I", replacer.replace("I"))
    }

    @Test
    fun testNegativeFeminineOrdinal() {
        assertEquals("primeira classe", replacer.replace("1ª classe"))
    }

    // ==================== production tests ====================

    @Test
    fun testProductionNews() {
        val input = "Em 2024, a população atingiu 8.000 milhões."
        val expected = "Em dois mil e vinte e quatro, a população atingiu oito mil milhões."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionSports() {
        val input = "O jogo terminou 3:1 com 50.000 espectadores."
        val expected = "O jogo terminou três a um com cinquenta mil espectadores."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionScience() {
        val input = "A velocidade da luz é 300.000 km/s."
        val expected = "A velocidade da luz é trezentos mil km/s."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionRecipe() {
        val input = "Aqueça o forno a 180°C. Misture 500 gramas com 1/2 colher."
        val expected = "Aqueça o forno a cento e oitenta graus Celsius. Misture quinhentos gramas com um meio colher."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionHistory() {
        val input = "Em 22 de abril de 1500, Pedro chegou ao Brasil."
        val expected = "Em vinte e dois de abril de mil e quinhentos, Pedro chegou ao Brasil."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionMixed() {
        val input = "Nasceu em 3 de julho de 1999. O telefone é 555-123-4567 e mede 1,75 metros."
        val expected = "Nasceu em três de julho de mil novecentos e noventa e nove. O telefone é cinco cinco cinco um dois três quatro cinco seis sete e mede um vírgula sete cinco metros."
        assertEquals(expected, replacer.replace(input))
    }

    // ==================== yearToPortuguese ====================

    @Test
    fun testYearToPortuguese2024() {
        assertEquals("dois mil e vinte e quatro", PtNumberReplacer.yearToPortuguese(2024))
    }

    @Test
    fun testYearToPortuguese1999() {
        assertEquals("mil novecentos e noventa e nove", PtNumberReplacer.yearToPortuguese(1999))
    }

    @Test
    fun testYearToPortuguese2000() {
        assertEquals("dois mil", PtNumberReplacer.yearToPortuguese(2000))
    }

    @Test
    fun testYearToPortuguese1500() {
        assertEquals("mil e quinhentos", PtNumberReplacer.yearToPortuguese(1500))
    }

    @Test
    fun testYearToPortuguese1900() {
        assertEquals("mil e novecentos", PtNumberReplacer.yearToPortuguese(1900))
    }

    @Test
    fun testYearToPortuguese100() {
        assertEquals("cem", PtNumberReplacer.yearToPortuguese(100))
    }

    @Test
    fun testYearToPortuguese500() {
        assertEquals("quinhentos", PtNumberReplacer.yearToPortuguese(500))
    }

    // ==================== edge: large number ====================

    @Test
    fun testIntLarge() {
        assertEquals("quinhentos trilhões", PtNumberReplacer.intToPortuguese(500000000000000L))
    }

    // ==================== edge: already spelled out ====================

    @Test
    fun testReplaceAlreadySpelledOut() {
        assertEquals("um dois três", replacer.replace("um dois três"))
    }

    // ==================== edge: standalone ordinal replace ====================

    @Test
    fun testReplaceOrdinal1Standalone() {
        assertEquals("primeiro", replacer.replace("1°"))
    }

    @Test
    fun testReplaceOrdinal5Standalone() {
        assertEquals("quinto", replacer.replace("5°"))
    }

    @Test
    fun testReplaceOrdinalFeminine1Standalone() {
        assertEquals("primeira", replacer.replace("1ª"))
    }

    @Test
    fun testReplaceOrdinalFeminine3Standalone() {
        assertEquals("terceira", replacer.replace("3ª"))
    }

    // ==================== edge: phone international ====================

    @Test
    fun testReplacePhoneInternational() {
        assertEquals("cinco cinco um um nove nove nove oito oito sete sete seis seis", replacer.replace("+55 11 999887766"))
    }

    // ==================== edge: negative temp range ====================

    @Test
    fun testReplaceTempRangeNegative() {
        assertEquals("menos dez a zero graus Celsius", replacer.replace("-10-0°C"))
    }

    // ==================== edge: plus sign temperature ====================

    @Test
    fun testReplaceTempPlus() {
        assertEquals("+cinco graus Celsius", replacer.replace("+5°C"))
    }

    // ==================== edge: 1°C singular ====================

    @Test
    fun testReplaceTemp1CSingular() {
        assertEquals("um grau Celsius", replacer.replace("1°C"))
    }

    // ==================== edge: rules no conflict ====================

    @Test
    fun testReplaceRulesNoConflict() {
        val input = "Em 15 de março de 2024, às 14:30, a temperatura era 25°C com 50% de humidade."
        val expected = "Em quinze de março de dois mil e vinte e quatro, às quatorze e trinta, a temperatura era vinte e cinco graus Celsius com cinquenta por cento de humidade."
        assertEquals(expected, replacer.replace(input))
    }

    // ==================== negative: accented character boundary ====================

    @Test
    fun testNegativeAccentedDaMe() {
        assertEquals("Dá-me cinco minutos", replacer.replace("Dá-me 5 minutos"))
    }

    @Test
    fun testNegativeAccentedCafe() {
        assertEquals("Tomamos café às três horas", replacer.replace("Tomamos café às 3 horas"))
    }

    @Test
    fun testNegativeAccentedSaoPaulo() {
        assertEquals("São Paulo tem doze milhões", replacer.replace("São Paulo tem 12 milhões"))
    }

    @Test
    fun testNegativeAccentedPais() {
        assertEquals("O país tem duzentos e dez milhões", replacer.replace("O país tem 210 milhões"))
    }

    @Test
    fun testNegativeAccentedAte() {
        assertEquals("Até dez pessoas", replacer.replace("Até 10 pessoas"))
    }
}
