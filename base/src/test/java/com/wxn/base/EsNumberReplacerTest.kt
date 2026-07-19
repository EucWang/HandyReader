package com.wxn.base

import com.wxn.base.util.numReplacer.EsNumberReplacer
import org.junit.Assert.assertEquals
import org.junit.Test

class EsNumberReplacerTest {

    private val replacer = EsNumberReplacer()

    // ==================== intToSpanish: 0~15 ====================

    @Test
    fun testIntCero() {
        assertEquals("cero", EsNumberReplacer.intToSpanish(0))
    }

    @Test
    fun testIntUno() {
        assertEquals("uno", EsNumberReplacer.intToSpanish(1))
    }

    @Test
    fun testIntDos() {
        assertEquals("dos", EsNumberReplacer.intToSpanish(2))
    }

    @Test
    fun testIntTres() {
        assertEquals("tres", EsNumberReplacer.intToSpanish(3))
    }

    @Test
    fun testIntCuatro() {
        assertEquals("cuatro", EsNumberReplacer.intToSpanish(4))
    }

    @Test
    fun testIntCinco() {
        assertEquals("cinco", EsNumberReplacer.intToSpanish(5))
    }

    @Test
    fun testIntSeis() {
        assertEquals("seis", EsNumberReplacer.intToSpanish(6))
    }

    @Test
    fun testIntSiete() {
        assertEquals("siete", EsNumberReplacer.intToSpanish(7))
    }

    @Test
    fun testIntOcho() {
        assertEquals("ocho", EsNumberReplacer.intToSpanish(8))
    }

    @Test
    fun testIntNueve() {
        assertEquals("nueve", EsNumberReplacer.intToSpanish(9))
    }

    @Test
    fun testIntDiez() {
        assertEquals("diez", EsNumberReplacer.intToSpanish(10))
    }

    @Test
    fun testIntOnce() {
        assertEquals("once", EsNumberReplacer.intToSpanish(11))
    }

    @Test
    fun testIntDoce() {
        assertEquals("doce", EsNumberReplacer.intToSpanish(12))
    }

    @Test
    fun testIntTrece() {
        assertEquals("trece", EsNumberReplacer.intToSpanish(13))
    }

    @Test
    fun testIntCatorce() {
        assertEquals("catorce", EsNumberReplacer.intToSpanish(14))
    }

    @Test
    fun testIntQuince() {
        assertEquals("quince", EsNumberReplacer.intToSpanish(15))
    }

    // ==================== intToSpanish: 16~19 (teens) ====================

    @Test
    fun testIntDieciseis() {
        assertEquals("dieciséis", EsNumberReplacer.intToSpanish(16))
    }

    @Test
    fun testIntDiecisiete() {
        assertEquals("diecisiete", EsNumberReplacer.intToSpanish(17))
    }

    @Test
    fun testIntDieciocho() {
        assertEquals("dieciocho", EsNumberReplacer.intToSpanish(18))
    }

    @Test
    fun testIntDiecinueve() {
        assertEquals("diecinueve", EsNumberReplacer.intToSpanish(19))
    }

    // ==================== intToSpanish: 20~29 (veinte/veinti-) ====================

    @Test
    fun testIntVeinte() {
        assertEquals("veinte", EsNumberReplacer.intToSpanish(20))
    }

    @Test
    fun testIntVeintiuno() {
        assertEquals("veintiuno", EsNumberReplacer.intToSpanish(21))
    }

    @Test
    fun testIntVeintidos() {
        assertEquals("veintidós", EsNumberReplacer.intToSpanish(22))
    }

    @Test
    fun testIntVeintitres() {
        assertEquals("veintitrés", EsNumberReplacer.intToSpanish(23))
    }

    @Test
    fun testIntVeinticuatro() {
        assertEquals("veinticuatro", EsNumberReplacer.intToSpanish(24))
    }

    @Test
    fun testIntVeinticinco() {
        assertEquals("veinticinco", EsNumberReplacer.intToSpanish(25))
    }

    @Test
    fun testIntVeintiseis() {
        assertEquals("veintiséis", EsNumberReplacer.intToSpanish(26))
    }

    @Test
    fun testIntVeintisiete() {
        assertEquals("veintisiete", EsNumberReplacer.intToSpanish(27))
    }

    @Test
    fun testIntVeintiocho() {
        assertEquals("veintiocho", EsNumberReplacer.intToSpanish(28))
    }

    @Test
    fun testIntVeintinueve() {
        assertEquals("veintinueve", EsNumberReplacer.intToSpanish(29))
    }

    // ==================== intToSpanish: 30~99 (tens + y) ====================

    @Test
    fun testIntTreinta() {
        assertEquals("treinta", EsNumberReplacer.intToSpanish(30))
    }

    @Test
    fun testIntTreintaYUno() {
        assertEquals("treinta y uno", EsNumberReplacer.intToSpanish(31))
    }

    @Test
    fun testIntCuarenta() {
        assertEquals("cuarenta", EsNumberReplacer.intToSpanish(40))
    }

    @Test
    fun testIntCuarentaYCinco() {
        assertEquals("cuarenta y cinco", EsNumberReplacer.intToSpanish(45))
    }

    @Test
    fun testIntCincuenta() {
        assertEquals("cincuenta", EsNumberReplacer.intToSpanish(50))
    }

    @Test
    fun testIntSesenta() {
        assertEquals("sesenta", EsNumberReplacer.intToSpanish(60))
    }

    @Test
    fun testIntSetenta() {
        assertEquals("setenta", EsNumberReplacer.intToSpanish(70))
    }

    @Test
    fun testIntOchenta() {
        assertEquals("ochenta", EsNumberReplacer.intToSpanish(80))
    }

    @Test
    fun testIntNoventa() {
        assertEquals("noventa", EsNumberReplacer.intToSpanish(90))
    }

    @Test
    fun testIntNoventaYNueve() {
        assertEquals("noventa y nueve", EsNumberReplacer.intToSpanish(99))
    }

    // ==================== intToSpanish: 100~999 (hundreds) ====================

    @Test
    fun testIntCien() {
        assertEquals("cien", EsNumberReplacer.intToSpanish(100))
    }

    @Test
    fun testIntCientoUno() {
        assertEquals("ciento uno", EsNumberReplacer.intToSpanish(101))
    }

    @Test
    fun testIntCientoDiez() {
        assertEquals("ciento diez", EsNumberReplacer.intToSpanish(110))
    }

    @Test
    fun testIntCientoVeintitres() {
        assertEquals("ciento veintitrés", EsNumberReplacer.intToSpanish(123))
    }

    @Test
    fun testIntDoscientos() {
        assertEquals("doscientos", EsNumberReplacer.intToSpanish(200))
    }

    @Test
    fun testIntDoscientosUno() {
        assertEquals("doscientos uno", EsNumberReplacer.intToSpanish(201))
    }

    @Test
    fun testIntTrescientos() {
        assertEquals("trescientos", EsNumberReplacer.intToSpanish(300))
    }

    @Test
    fun testIntCuatrocientos() {
        assertEquals("cuatrocientos", EsNumberReplacer.intToSpanish(400))
    }

    @Test
    fun testIntQuinientos() {
        assertEquals("quinientos", EsNumberReplacer.intToSpanish(500))
    }

    @Test
    fun testIntSeiscientos() {
        assertEquals("seiscientos", EsNumberReplacer.intToSpanish(600))
    }

    @Test
    fun testIntSetecientos() {
        assertEquals("setecientos", EsNumberReplacer.intToSpanish(700))
    }

    @Test
    fun testIntOchocientos() {
        assertEquals("ochocientos", EsNumberReplacer.intToSpanish(800))
    }

    @Test
    fun testIntNovecientos() {
        assertEquals("novecientos", EsNumberReplacer.intToSpanish(900))
    }

    @Test
    fun testIntNovecientosNoventaYNueve() {
        assertEquals("novecientos noventa y nueve", EsNumberReplacer.intToSpanish(999))
    }

    // ==================== intToSpanish: 1000+ (thousands) ====================

    @Test
    fun testIntMil() {
        assertEquals("mil", EsNumberReplacer.intToSpanish(1000))
    }

    @Test
    fun testIntMilUno() {
        assertEquals("mil uno", EsNumberReplacer.intToSpanish(1001))
    }

    @Test
    fun testIntMilCien() {
        assertEquals("mil cien", EsNumberReplacer.intToSpanish(1100))
    }

    @Test
    fun testIntMilDoscientosTreintaYCuatro() {
        assertEquals("mil doscientos treinta y cuatro", EsNumberReplacer.intToSpanish(1234))
    }

    @Test
    fun testIntDosMil() {
        assertEquals("dos mil", EsNumberReplacer.intToSpanish(2000))
    }

    @Test
    fun testIntDiezMil() {
        assertEquals("diez mil", EsNumberReplacer.intToSpanish(10000))
    }

    @Test
    fun testIntCienMil() {
        assertEquals("cien mil", EsNumberReplacer.intToSpanish(100000))
    }

    @Test
    fun testIntQuinientosMil() {
        assertEquals("quinientos mil", EsNumberReplacer.intToSpanish(500000))
    }

    @Test
    fun testIntNovecientosNoventaYNueveMilNovecientosNoventaYNueve() {
        assertEquals("novecientos noventa y nueve mil novecientos noventa y nueve", EsNumberReplacer.intToSpanish(999999))
    }

    // ==================== intToSpanish: millions ====================

    @Test
    fun testIntUnMillon() {
        assertEquals("un millón", EsNumberReplacer.intToSpanish(1000000))
    }

    @Test
    fun testIntUnMillonUno() {
        assertEquals("un millón uno", EsNumberReplacer.intToSpanish(1000001))
    }

    @Test
    fun testIntDosMillones() {
        assertEquals("dos millones", EsNumberReplacer.intToSpanish(2000000))
    }

    @Test
    fun testIntDiezMillones() {
        assertEquals("diez millones", EsNumberReplacer.intToSpanish(10000000))
    }

    @Test
    fun testIntCienMillones() {
        assertEquals("cien millones", EsNumberReplacer.intToSpanish(100000000))
    }

    @Test
    fun testIntQuinientosMillones() {
        assertEquals("quinientos millones", EsNumberReplacer.intToSpanish(500000000))
    }

    // ==================== intToSpanish: mil millones (10^9) ====================

    @Test
    fun testIntMilMillones() {
        assertEquals("mil millones", EsNumberReplacer.intToSpanish(1000000000))
    }

    @Test
    fun testIntMilMillonesUno() {
        assertEquals("mil millones uno", EsNumberReplacer.intToSpanish(1000000001))
    }

    @Test
    fun testIntDosMilMillones() {
        assertEquals("dos mil millones", EsNumberReplacer.intToSpanish(2000000000))
    }

    @Test
    fun testIntMilCienMillones() {
        assertEquals("mil cien millones", EsNumberReplacer.intToSpanish(1100000000))
    }

    // ==================== intToSpanish: billón (10^12) ====================

    @Test
    fun testIntUnBillon() {
        assertEquals("un billón", EsNumberReplacer.intToSpanish(1000000000000))
    }

    @Test
    fun testIntDosBillones() {
        assertEquals("dos billones", EsNumberReplacer.intToSpanish(2000000000000))
    }

    // ==================== intToSpanish: negative ====================

    @Test
    fun testIntMenosCinco() {
        assertEquals("menos cinco", EsNumberReplacer.intToSpanish(-5))
    }

    @Test
    fun testIntMenosCien() {
        assertEquals("menos cien", EsNumberReplacer.intToSpanish(-100))
    }

    // ==================== floatToSpanish ====================

    @Test
    fun testFloatCero() {
        assertEquals("cero", EsNumberReplacer.floatToSpanish(0.0))
    }

    @Test
    fun testFloatCeroComaCinco() {
        assertEquals("cero coma cinco", EsNumberReplacer.floatToSpanish(0.5))
    }

    @Test
    fun testFloatUnoComaCinco() {
        assertEquals("uno coma cinco", EsNumberReplacer.floatToSpanish(1.5))
    }

    @Test
    fun testFloatTresComaCatorce() {
        assertEquals("tres coma uno cuatro", EsNumberReplacer.floatToSpanish(3.14))
    }

    @Test
    fun testFloatNoventaYNueveComa() {
        assertEquals("noventa y nueve coma nueve nueve", EsNumberReplacer.floatToSpanish(99.99))
    }

    @Test
    fun testFloatMilComaCinco() {
        assertEquals("mil coma cinco", EsNumberReplacer.floatToSpanish(1000.5))
    }

    @Test
    fun testFloatMenosDosComaCinco() {
        assertEquals("menos dos coma cinco", EsNumberReplacer.floatToSpanish(-2.5))
    }

    @Test
    fun testFloatDiezComaCero() {
        assertEquals("diez", EsNumberReplacer.floatToSpanish(10.0))
    }

    // ==================== ordinalToSpanish: 1~10 ====================

    @Test
    fun testOrdinalPrimero() {
        assertEquals("primero", EsNumberReplacer.ordinalToSpanish(1))
    }

    @Test
    fun testOrdinalSegundo() {
        assertEquals("segundo", EsNumberReplacer.ordinalToSpanish(2))
    }

    @Test
    fun testOrdinalTercero() {
        assertEquals("tercero", EsNumberReplacer.ordinalToSpanish(3))
    }

    @Test
    fun testOrdinalCuarto() {
        assertEquals("cuarto", EsNumberReplacer.ordinalToSpanish(4))
    }

    @Test
    fun testOrdinalQuinto() {
        assertEquals("quinto", EsNumberReplacer.ordinalToSpanish(5))
    }

    @Test
    fun testOrdinalSexto() {
        assertEquals("sexto", EsNumberReplacer.ordinalToSpanish(6))
    }

    @Test
    fun testOrdinalSeptimo() {
        assertEquals("séptimo", EsNumberReplacer.ordinalToSpanish(7))
    }

    @Test
    fun testOrdinalOctavo() {
        assertEquals("octavo", EsNumberReplacer.ordinalToSpanish(8))
    }

    @Test
    fun testOrdinalNoveno() {
        assertEquals("noveno", EsNumberReplacer.ordinalToSpanish(9))
    }

    @Test
    fun testOrdinalDecimo() {
        assertEquals("décimo", EsNumberReplacer.ordinalToSpanish(10))
    }

    // ==================== ordinalToSpanish: 11~19 ====================

    @Test
    fun testOrdinalUndecimo() {
        assertEquals("undécimo", EsNumberReplacer.ordinalToSpanish(11))
    }

    @Test
    fun testOrdinalDuodecimo() {
        assertEquals("duodécimo", EsNumberReplacer.ordinalToSpanish(12))
    }

    @Test
    fun testOrdinalDecimotercero() {
        assertEquals("decimotercero", EsNumberReplacer.ordinalToSpanish(13))
    }

    @Test
    fun testOrdinalDecimocuarto() {
        assertEquals("decimocuarto", EsNumberReplacer.ordinalToSpanish(14))
    }

    @Test
    fun testOrdinalDecimoquinto() {
        assertEquals("decimoquinto", EsNumberReplacer.ordinalToSpanish(15))
    }

    @Test
    fun testOrdinalDecimosexto() {
        assertEquals("decimosexto", EsNumberReplacer.ordinalToSpanish(16))
    }

    @Test
    fun testOrdinalDecimoseptimo() {
        assertEquals("decimoséptimo", EsNumberReplacer.ordinalToSpanish(17))
    }

    @Test
    fun testOrdinalDecimoctavo() {
        assertEquals("decimoctavo", EsNumberReplacer.ordinalToSpanish(18))
    }

    @Test
    fun testOrdinalDecimonoveno() {
        assertEquals("decimonoveno", EsNumberReplacer.ordinalToSpanish(19))
    }

    // ==================== ordinalToSpanish: tens ====================

    @Test
    fun testOrdinalVigesimo() {
        assertEquals("vigésimo", EsNumberReplacer.ordinalToSpanish(20))
    }

    @Test
    fun testOrdinalTrigesimo() {
        assertEquals("trigésimo", EsNumberReplacer.ordinalToSpanish(30))
    }

    @Test
    fun testOrdinalCuadragesimo() {
        assertEquals("cuadragésimo", EsNumberReplacer.ordinalToSpanish(40))
    }

    @Test
    fun testOrdinalQuincuagesimo() {
        assertEquals("quincuagésimo", EsNumberReplacer.ordinalToSpanish(50))
    }

    @Test
    fun testOrdinalSexagesimo() {
        assertEquals("sexagésimo", EsNumberReplacer.ordinalToSpanish(60))
    }

    @Test
    fun testOrdinalSeptuagesimo() {
        assertEquals("septuagésimo", EsNumberReplacer.ordinalToSpanish(70))
    }

    @Test
    fun testOrdinalOctogesimo() {
        assertEquals("octogésimo", EsNumberReplacer.ordinalToSpanish(80))
    }

    @Test
    fun testOrdinalNonagesimo() {
        assertEquals("nonagésimo", EsNumberReplacer.ordinalToSpanish(90))
    }

    // ==================== ordinalToSpanish: compound ====================

    @Test
    fun testOrdinalVigesimoPrimero() {
        assertEquals("vigésimo primero", EsNumberReplacer.ordinalToSpanish(21))
    }

    @Test
    fun testOrdinalVigesimoSegundo() {
        assertEquals("vigésimo segundo", EsNumberReplacer.ordinalToSpanish(22))
    }

    @Test
    fun testOrdinalTrigesimoQuinto() {
        assertEquals("trigésimo quinto", EsNumberReplacer.ordinalToSpanish(35))
    }

    @Test
    fun testOrdinalCuadragesimoSegundo() {
        assertEquals("cuadragésimo segundo", EsNumberReplacer.ordinalToSpanish(42))
    }

    @Test
    fun testOrdinalNonagesimoNoveno() {
        assertEquals("nonagésimo noveno", EsNumberReplacer.ordinalToSpanish(99))
    }

    // ==================== ordinalToSpanish: hundreds/thousands ====================

    @Test
    fun testOrdinalCentesimo() {
        assertEquals("centésimo", EsNumberReplacer.ordinalToSpanish(100))
    }

    @Test
    fun testOrdinalCentesimoPrimero() {
        assertEquals("centésimo primero", EsNumberReplacer.ordinalToSpanish(101))
    }

    @Test
    fun testOrdinalDucentesimo() {
        assertEquals("ducentésimo", EsNumberReplacer.ordinalToSpanish(200))
    }

    @Test
    fun testOrdinalMilesimo() {
        assertEquals("milésimo", EsNumberReplacer.ordinalToSpanish(1000))
    }

    // ==================== ordinalToSpanish: edge cases ====================

    @Test
    fun testOrdinalZero() {
        assertEquals("cero", EsNumberReplacer.ordinalToSpanish(0))
    }

    @Test
    fun testOrdinalNegative() {
        assertEquals("menos uno", EsNumberReplacer.ordinalToSpanish(-1))
    }

    // ==================== yearToSpanish ====================

    @Test
    fun testYear1800() {
        assertEquals("mil ochocientos", EsNumberReplacer.yearToSpanish(1800))
    }

    @Test
    fun testYear1900() {
        assertEquals("mil novecientos", EsNumberReplacer.yearToSpanish(1900))
    }

    @Test
    fun testYear2000() {
        assertEquals("dos mil", EsNumberReplacer.yearToSpanish(2000))
    }

    @Test
    fun testYear2001() {
        assertEquals("dos mil uno", EsNumberReplacer.yearToSpanish(2001))
    }

    @Test
    fun testYear2009() {
        assertEquals("dos mil nueve", EsNumberReplacer.yearToSpanish(2009))
    }

    @Test
    fun testYear2010() {
        assertEquals("dos mil diez", EsNumberReplacer.yearToSpanish(2010))
    }

    @Test
    fun testYear2024() {
        assertEquals("dos mil veinticuatro", EsNumberReplacer.yearToSpanish(2024))
    }

    @Test
    fun testYear1999() {
        assertEquals("mil novecientos noventa y nueve", EsNumberReplacer.yearToSpanish(1999))
    }

    @Test
    fun testYear1492() {
        assertEquals("mil cuatrocientos noventa y dos", EsNumberReplacer.yearToSpanish(1492))
    }

    @Test
    fun testYear2100() {
        assertEquals("dos mil cien", EsNumberReplacer.yearToSpanish(2100))
    }

    @Test
    fun testYear50() {
        assertEquals("cincuenta", EsNumberReplacer.yearToSpanish(50))
    }

    @Test
    fun testYear100() {
        assertEquals("cien", EsNumberReplacer.yearToSpanish(100))
    }

    // ==================== phoneToSpanish ====================

    @Test
    fun testPhoneBasic() {
        assertEquals("cinco cinco cinco uno dos tres cuatro cinco seis siete", EsNumberReplacer.phoneToSpanish("5551234567"))
    }

    @Test
    fun testPhoneWithZeros() {
        assertEquals("nueve cero cero uno dos tres cuatro cinco seis", EsNumberReplacer.phoneToSpanish("900123456"))
    }

    // ==================== fractionToSpanish: special ====================

    @Test
    fun testFractionUnMedio() {
        assertEquals("un medio", EsNumberReplacer.fractionToSpanish(1, 2))
    }

    @Test
    fun testFractionUnTercio() {
        assertEquals("un tercio", EsNumberReplacer.fractionToSpanish(1, 3))
    }

    @Test
    fun testFractionUnCuarto() {
        assertEquals("un cuarto", EsNumberReplacer.fractionToSpanish(1, 4))
    }

    @Test
    fun testFractionDosMedios() {
        assertEquals("dos medios", EsNumberReplacer.fractionToSpanish(2, 2))
    }

    @Test
    fun testFractionDosTercios() {
        assertEquals("dos tercios", EsNumberReplacer.fractionToSpanish(2, 3))
    }

    @Test
    fun testFractionTresCuartos() {
        assertEquals("tres cuartos", EsNumberReplacer.fractionToSpanish(3, 4))
    }

    // ==================== fractionToSpanish: ordinal denominators ====================

    @Test
    fun testFractionUnQuinto() {
        assertEquals("un quinto", EsNumberReplacer.fractionToSpanish(1, 5))
    }

    @Test
    fun testFractionDosQuintos() {
        assertEquals("dos quintos", EsNumberReplacer.fractionToSpanish(2, 5))
    }

    @Test
    fun testFractionUnSexto() {
        assertEquals("un sexto", EsNumberReplacer.fractionToSpanish(1, 6))
    }

    @Test
    fun testFractionUnSeptimo() {
        assertEquals("un séptimo", EsNumberReplacer.fractionToSpanish(1, 7))
    }

    @Test
    fun testFractionUnOctavo() {
        assertEquals("un octavo", EsNumberReplacer.fractionToSpanish(1, 8))
    }

    @Test
    fun testFractionUnNoveno() {
        assertEquals("un noveno", EsNumberReplacer.fractionToSpanish(1, 9))
    }

    @Test
    fun testFractionUnDecimo() {
        assertEquals("un décimo", EsNumberReplacer.fractionToSpanish(1, 10))
    }

    // ==================== fractionToSpanish: -avo denominators ====================

    @Test
    fun testFractionUnOnceavo() {
        assertEquals("un onceavo", EsNumberReplacer.fractionToSpanish(1, 11))
    }

    @Test
    fun testFractionUnDoceavo() {
        assertEquals("un doceavo", EsNumberReplacer.fractionToSpanish(1, 12))
    }

    @Test
    fun testFractionUnVeinteavo() {
        assertEquals("un veinteavo", EsNumberReplacer.fractionToSpanish(1, 20))
    }

    @Test
    fun testFractionDosVeinteavos() {
        assertEquals("dos veinteavos", EsNumberReplacer.fractionToSpanish(2, 20))
    }

    // ==================== replace: ordinal (°) ====================

    @Test
    fun testReplaceOrdinal1() {
        assertEquals("primero", replacer.replace("1°"))
    }

    @Test
    fun testReplaceOrdinal2() {
        assertEquals("segundo", replacer.replace("2°"))
    }

    @Test
    fun testReplaceOrdinal3() {
        assertEquals("tercero", replacer.replace("3°"))
    }

    @Test
    fun testReplaceOrdinal4() {
        assertEquals("cuarto", replacer.replace("4°"))
    }

    @Test
    fun testReplaceOrdinal5() {
        assertEquals("quinto", replacer.replace("5°"))
    }

    @Test
    fun testReplaceOrdinal10() {
        assertEquals("décimo", replacer.replace("10°"))
    }

    @Test
    fun testReplaceOrdinal20() {
        assertEquals("vigésimo", replacer.replace("20°"))
    }

    @Test
    fun testReplaceOrdinal21() {
        assertEquals("vigésimo primero", replacer.replace("21°"))
    }

    @Test
    fun testReplaceOrdinal30() {
        assertEquals("trigésimo", replacer.replace("30°"))
    }

    @Test
    fun testReplaceOrdinal42() {
        assertEquals("cuadragésimo segundo", replacer.replace("42°"))
    }

    @Test
    fun testReplaceOrdinal100() {
        assertEquals("centésimo", replacer.replace("100°"))
    }

    @Test
    fun testReplaceOrdinalDotNotation() {
        assertEquals("primero", replacer.replace("1.º"))
    }

    @Test
    fun testReplaceOrdinalInContext() {
        assertEquals("el primero de mayo", replacer.replace("el 1° de mayo"))
    }

    // ==================== replace: dates ====================

    @Test
    fun testReplaceDateEnero15_2024() {
        assertEquals("quince de enero de dos mil veinticuatro", replacer.replace("enero 15, 2024"))
    }

    @Test
    fun testReplaceDate15DeEnero2024() {
        assertEquals("quince de enero de dos mil veinticuatro", replacer.replace("15 de enero de 2024"))
    }

    @Test
    fun testReplaceDate15DeEnero() {
        assertEquals("quince de enero", replacer.replace("15 de enero"))
    }

    @Test
    fun testReplaceDate1DeMayo2020() {
        assertEquals("primero de mayo de dos mil veinte", replacer.replace("1 de mayo de 2020"))
    }

    @Test
    fun testReplaceDateFebrero28_2023() {
        assertEquals("veintiocho de febrero de dos mil veintitrés", replacer.replace("febrero 28, 2023"))
    }

    @Test
    fun testReplaceDate29DeFebrero2024() {
        assertEquals("veintinueve de febrero de dos mil veinticuatro", replacer.replace("29 de febrero de 2024"))
    }

    @Test
    fun testReplaceDate15Del2024() {
        assertEquals("quince de enero de dos mil veinticuatro", replacer.replace("15 de enero del 2024"))
    }

    @Test
    fun testReplaceDateAbril() {
        assertEquals("quince de abril de dos mil veinticuatro", replacer.replace("15 de abril de 2024"))
    }

    @Test
    fun testReplaceDateSeptiembre() {
        assertEquals("quince de septiembre de dos mil veinticuatro", replacer.replace("15 de septiembre de 2024"))
    }

    @Test
    fun testReplaceDateWithText() {
        assertEquals("el quince de enero de dos mil veinticuatro fue un gran día", replacer.replace("el 15 de enero de 2024 fue un gran día"))
    }

    // ==================== replace: year in context ====================

    @Test
    fun testReplaceYearEn() {
        assertEquals("en dos mil veinticuatro", replacer.replace("en 2024"))
    }

    @Test
    fun testReplaceYearDesde() {
        assertEquals("desde mil novecientos noventa y nueve", replacer.replace("desde 1999"))
    }

    @Test
    fun testReplaceYearHasta() {
        assertEquals("hasta dos mil", replacer.replace("hasta 2000"))
    }

    @Test
    fun testReplaceYearEntre() {
        assertEquals("entre dos mil diez", replacer.replace("entre 2010"))
    }

    @Test
    fun testReplaceYearAnno() {
        assertEquals("año dos mil veinticuatro", replacer.replace("año 2024"))
    }

    @Test
    fun testReplaceYearEn1492() {
        assertEquals("en mil cuatrocientos noventa y dos", replacer.replace("en 1492"))
    }

    // ==================== replace: time ====================

    @Test
    fun testReplaceTime1430() {
        assertEquals("catorce treinta", replacer.replace("14:30"))
    }

    @Test
    fun testReplaceTime0000() {
        assertEquals("cero", replacer.replace("0:00"))
    }

    @Test
    fun testReplaceTime1200() {
        assertEquals("doce", replacer.replace("12:00"))
    }

    @Test
    fun testReplaceTime101() {
        assertEquals("uno uno", replacer.replace("1:01"))
    }

    @Test
    fun testReplaceTime2359() {
        assertEquals("veintitrés cincuenta y nueve", replacer.replace("23:59"))
    }

    @Test
    fun testReplaceTime235959() {
        assertEquals("veintitrés cincuenta y nueve cincuenta y nueve", replacer.replace("23:59:59"))
    }

    @Test
    fun testReplaceTime030() {
        assertEquals("cero treinta", replacer.replace("0:30"))
    }

    // ==================== replace: temperature ====================

    @Test
    fun testReplaceTemp36C() {
        assertEquals("treinta y seis grados Celsius", replacer.replace("36°C"))
    }

    @Test
    fun testReplaceTemp100F() {
        assertEquals("cien grados Fahrenheit", replacer.replace("100°F"))
    }

    @Test
    fun testReplaceTemp0C() {
        assertEquals("cero grados Celsius", replacer.replace("0°C"))
    }

    @Test
    fun testReplaceTempFloat() {
        assertEquals("treinta y seis coma seis grados Celsius", replacer.replace("36.6°C"))
    }

    @Test
    fun testReplaceTempUnicode() {
        assertEquals("treinta y seis grados Celsius", replacer.replace("36℃"))
    }

    // ==================== replace: temperature range ====================

    @Test
    fun testReplaceTempRange() {
        assertEquals("treinta y seis a cuarenta y dos grados Celsius", replacer.replace("36-42°C"))
    }

    @Test
    fun testReplaceTempRangeNegative() {
        assertEquals("menos diez a cero grados Celsius", replacer.replace("-10-0°C"))
    }

    // ==================== replace: phone ====================

    @Test
    fun testReplacePhoneWithDashes() {
        assertEquals("cinco cinco cinco uno dos tres cuatro cinco seis siete", replacer.replace("555-123-4567"))
    }

    @Test
    fun testReplacePhoneInternational() {
        assertEquals("tres cuatro cinco cinco cinco uno dos tres cuatro cinco seis", replacer.replace("+34 555 123 456"))
    }

    // ==================== replace: percentage ====================

    @Test
    fun testReplacePercent50() {
        assertEquals("cincuenta por ciento", replacer.replace("50%"))
    }

    @Test
    fun testReplacePercentFloat() {
        assertEquals("quince coma siete por ciento", replacer.replace("15,7%"))
    }

    @Test
    fun testReplacePercent100() {
        assertEquals("cien por ciento", replacer.replace("100%"))
    }

    @Test
    fun testReplacePercentPoint() {
        assertEquals("quince coma siete por ciento", replacer.replace("15.7%"))
    }

    // ==================== replace: currency USD ====================

    @Test
    fun testReplaceUsd5() {
        assertEquals("cinco dólares", replacer.replace("\$5"))
    }

    @Test
    fun testReplaceUsd1() {
        assertEquals("un dólar", replacer.replace("\$1"))
    }

    @Test
    fun testReplaceUsdWithCents() {
        assertEquals("cinco dólares con noventa y nueve centavos", replacer.replace("\$5.99"))
    }

    @Test
    fun testReplaceUsdCommaDecimal() {
        assertEquals("tres dólares con cincuenta centavos", replacer.replace("\$3,50"))
    }

    @Test
    fun testReplaceUsd1Cent() {
        assertEquals("un dólar con un centavo", replacer.replace("$1.01"))
    }

    @Test
    fun testReplaceUsdZeroInteger() {
        assertEquals("noventa y nueve centavos", replacer.replace("$0.99"))
    }

    @Test
    fun testReplaceEurZeroInteger() {
        assertEquals("cincuenta céntimos", replacer.replace("€0,50"))
    }

    // ==================== replace: currency EUR ====================

    @Test
    fun testReplaceEur3() {
        assertEquals("tres euros", replacer.replace("€3"))
    }

    @Test
    fun testReplaceEur1() {
        assertEquals("un euro", replacer.replace("€1"))
    }

    @Test
    fun testReplaceEurWithCents() {
        assertEquals("tres euros con cincuenta céntimos", replacer.replace("€3,50"))
    }

    @Test
    fun testReplaceEurWithCentsDot() {
        assertEquals("tres euros con cincuenta céntimos", replacer.replace("€3.50"))
    }

    @Test
    fun testReplaceEur100() {
        assertEquals("cien euros", replacer.replace("€100"))
    }

    // ==================== replace: scientific notation ====================

    @Test
    fun testReplaceSci() {
        assertEquals("dos coma cinco por diez a la menos ocho", replacer.replace("2.5E-8"))
    }

    @Test
    fun testReplaceSciPositive() {
        assertEquals("uno por diez a la seis", replacer.replace("1E6"))
    }

    @Test
    fun testReplaceSciComma() {
        assertEquals("tres coma uno cuatro por diez a la dos", replacer.replace("3,14E2"))
    }

    // ==================== replace: thousand separator ====================

    @Test
    fun testReplaceThousandSep1() {
        assertEquals("mil", replacer.replace("1.000"))
    }

    @Test
    fun testReplaceThousandSep1500() {
        assertEquals("mil quinientos", replacer.replace("1.500"))
    }

    @Test
    fun testReplaceThousandSep1_5M() {
        assertEquals("un millón quinientos mil", replacer.replace("1.500.000"))
    }

    @Test
    fun testReplaceThousandSepWithDecimal() {
        assertEquals("un millón doscientos treinta y cuatro mil quinientos sesenta y siete coma ocho nueve", replacer.replace("1.234.567,89"))
    }

    @Test
    fun testReplaceThousandSepWithDecimalWholeInt() {
        assertEquals("mil doscientos treinta y cuatro coma cinco seis", replacer.replace("1.234,56"))
    }

    @Test
    fun testReplaceThousandSepWithoutDecimal() {
        assertEquals("mil doscientos treinta y cuatro", replacer.replace("1.234"))
    }

    // ==================== replace: float ====================

    @Test
    fun testReplaceFloatComma() {
        assertEquals("tres coma uno cuatro", replacer.replace("3,14"))
    }

    @Test
    fun testReplaceFloatPoint() {
        assertEquals("tres coma uno cuatro", replacer.replace("3.14"))
    }

    @Test
    fun testReplaceFloat05() {
        assertEquals("cero coma cinco", replacer.replace("0,5"))
    }

    @Test
    fun testReplaceFloat9999() {
        assertEquals("noventa y nueve coma nueve nueve", replacer.replace("99,99"))
    }

    @Test
    fun testReplaceFloat1000_0() {
        assertEquals("mil", replacer.replace("1000.0"))
    }

    // ==================== replace: fraction ====================

    @Test
    fun testReplaceFraction12() {
        assertEquals("un medio", replacer.replace("1/2"))
    }

    @Test
    fun testReplaceFraction34() {
        assertEquals("tres cuartos", replacer.replace("3/4"))
    }

    @Test
    fun testReplaceFraction25() {
        assertEquals("dos quintos", replacer.replace("2/5"))
    }

    @Test
    fun testReplaceFraction22() {
        assertEquals("dos medios", replacer.replace("2/2"))
    }

    // ==================== replace: range ====================

    @Test
    fun testReplaceRange1020() {
        assertEquals("diez a veinte", replacer.replace("10-20"))
    }

    @Test
    fun testReplaceRangeFloat() {
        assertEquals("uno coma cinco a dos coma cinco", replacer.replace("1,5-2,5"))
    }

    @Test
    fun testReplaceRangeTilde() {
        assertEquals("diez a veinte", replacer.replace("10~20"))
    }

    @Test
    fun testReplaceRangeWithContext() {
        assertEquals("de diez a veinte personas", replacer.replace("de 10-20 personas"))
    }

    // ==================== replace: score ====================

    @Test
    fun testReplaceScore31() {
        assertEquals("tres a uno", replacer.replace("3:1"))
    }

    @Test
    fun testReplaceScore100_99() {
        assertEquals("cien a noventa y nueve", replacer.replace("100:99"))
    }

    @Test
    fun testReplaceScoreWithContext() {
        assertEquals("el partido terminó tres a dos", replacer.replace("el partido terminó 3:2"))
    }

    // ==================== replace: Roman numerals ====================

    @Test
    fun testReplaceRomanI() {
        assertEquals("I", replacer.replace("I"))
    }

    @Test
    fun testReplaceRomanIV() {
        assertEquals("cuatro", replacer.replace("IV"))
    }

    @Test
    fun testReplaceRomanXL() {
        assertEquals("cuarenta", replacer.replace("XL"))
    }

    @Test
    fun testReplaceRomanMM() {
        assertEquals("dos mil", replacer.replace("MM"))
    }

    @Test
    fun testReplaceRomanNotMatch() {
        assertEquals("Individual", replacer.replace("Individual"))
    }

    // ==================== replace: integer ====================

    @Test
    fun testReplaceInteger0() {
        assertEquals("cero", replacer.replace("0"))
    }

    @Test
    fun testReplaceInteger1() {
        assertEquals("uno", replacer.replace("1"))
    }

    @Test
    fun testReplaceInteger100() {
        assertEquals("cien", replacer.replace("100"))
    }

    @Test
    fun testReplaceInteger1000() {
        assertEquals("mil", replacer.replace("1000"))
    }

    @Test
    fun testReplaceIntegerNegative() {
        assertEquals("menos cinco", replacer.replace("-5"))
    }

    @Test
    fun testReplaceIntegerInText() {
        assertEquals("tengo veinte años", replacer.replace("tengo 20 años"))
    }

    // ==================== replace: no change needed ====================

    @Test
    fun testReplaceNoNumbers() {
        assertEquals("hola mundo", replacer.replace("hola mundo"))
    }

    @Test
    fun testReplaceEmpty() {
        assertEquals("", replacer.replace(""))
    }

    // ==================== replace: product-level scenarios ====================

    @Test
    fun testReplaceNewsParagraph() {
        val input = "En 2024, la población alcanzó 8.000 millones. Un crecimiento del 1,2% respecto a 2023."
        val expected = "En dos mil veinticuatro, la población alcanzó ocho mil millones. Un crecimiento del uno coma dos por ciento respecto a dos mil veintitrés."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceSportsReport() {
        val input = "El partido terminó 3:1 ante 50.000 espectadores."
        val expected = "El partido terminó tres a uno ante cincuenta mil espectadores."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceScienceText() {
        val input = "La velocidad de la luz es 299.792 km/s. La temperatura del Sol es 5.500°C."
        val expected = "La velocidad de la luz es doscientos noventa y nueve mil setecientos noventa y dos km/s. La temperatura del Sol es cinco mil quinientos grados Celsius."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceRecipe() {
        val input = "Agregar 250 gramos de harina y 1/2 litro de leche. Hornear a 180°C por 30 minutos."
        val expected = "Agregar doscientos cincuenta gramos de harina y un medio litro de leche. Hornear a ciento ochenta grados Celsius por treinta minutos."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceHistoryText() {
        val input = "El 12 de octubre de 1492, Colón llegó a América. En el siglo 15°, Europa se expandió."
        val expected = "El doce de octubre de mil cuatrocientos noventa y dos, Colón llegó a América. En el siglo decimoquinto, Europa se expandió."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testReplaceMixedContent() {
        val input = "Nació el 3 de julio de 1999. Su teléfono es 555-123-4567 y mide 1,75 metros."
        val expected = "Nació el tres de julio de mil novecientos noventa y nueve. Su teléfono es cinco cinco cinco uno dos tres cuatro cinco seis siete y mide uno coma siete cinco metros."
        assertEquals(expected, replacer.replace(input))
    }

    // ==================== replace: edge cases ====================

    @Test
    fun testReplacePlusSign() {
        assertEquals("+cinco grados Celsius", replacer.replace("+5°C"))
    }

    @Test
    fun testReplaceMultipleSame() {
        assertEquals("cinco y cinco y cinco", replacer.replace("5 y 5 y 5"))
    }

    @Test
    fun testReplaceRulesNoConflict() {
        assertEquals("cien por ciento de cien dólares a cincuenta euros", replacer.replace("100% de \$100 a €50"))
    }

    @Test
    fun testReplaceNumbersInSpanishWords() {
        assertEquals("tengo dos gatos", replacer.replace("tengo 2 gatos"))
    }

    @Test
    fun testReplaceSpacesAndTabs() {
        assertEquals("uno  dos\ttres", replacer.replace("1  2\t3"))
    }

    // ==================== replace: ordinal in phrases ====================

    @Test
    fun testReplaceOrdinalBeforeNoun() {
        assertEquals("el primero piso", replacer.replace("el 1° piso"))
    }

    @Test
    fun testReplaceOrdinal3BeforeCenturia() {
        assertEquals("el tercero siglo", replacer.replace("el 3° siglo"))
    }

    // ==================== ordinal: additional coverage ====================

    @Test
    fun testOrdinal150() {
        assertEquals("centésimo quincuagésimo", EsNumberReplacer.ordinalToSpanish(150))
    }

    @Test
    fun testOrdinal300() {
        assertEquals("tricentésimo", EsNumberReplacer.ordinalToSpanish(300))
    }

    @Test
    fun testOrdinal500() {
        assertEquals("quingentésimo", EsNumberReplacer.ordinalToSpanish(500))
    }

    // ==================== replace: comma decimal in temperature ====================

    @Test
    fun testReplaceTempCommaDecimal() {
        assertEquals("treinta y seis coma seis grados Celsius", replacer.replace("36,6°C"))
    }

    // ==================== replace: date abbreviation ====================

    @Test
    fun testReplaceDateAbbrevMonth() {
        assertEquals("quince de enero de dos mil veinticuatro", replacer.replace("15 de ene de 2024"))
    }

    @Test
    fun testReplaceDateAbbrevMonthEnStyle() {
        assertEquals("quince de enero de dos mil veinticuatro", replacer.replace("ene 15, 2024"))
    }

    // ==================== edge: large number ====================

    @Test
    fun testIntLarge() {
        assertEquals("quinientos mil millones", EsNumberReplacer.intToSpanish(500000000000L))
    }

    // ==================== NEGATIVE: patterns that should NOT be converted ====================

    @Test
    fun testNegativeISBN() {
        assertEquals("ISBN novecientos setenta y ocho a cero-ciento veintitrés a cuarenta y cinco mil seiscientos setenta y ocho-nueve", replacer.replace("ISBN 978-0-123-45678-9"))
    }

    @Test
    fun testNegativeVersionNumber() {
        assertEquals("vuno coma dos.tres", replacer.replace("v1.2.3"))
    }

    @Test
    fun testNegativeChemicalFormula() {
        assertEquals("H2O es agua", replacer.replace("H2O es agua"))
    }

    @Test
    fun testNegativeCO2() {
        assertEquals("CO2 emisiones", replacer.replace("CO2 emisiones"))
    }

    @Test
    fun testNegativeIPAddress() {
        assertEquals("ciento noventa y dos mil ciento sesenta y ocho.uno coma uno", replacer.replace("192.168.1.1"))
    }

    @Test
    fun testNegativeAlphanumeric() {
        assertEquals("3D película", replacer.replace("3D película"))
    }

    @Test
    fun testNegativeFlightNumber() {
        assertEquals("Vuelo IB1doscientos treinta y cuatro", replacer.replace("Vuelo IB1234"))
    }

    @Test
    fun testNegativeModelNumber() {
        assertEquals("Modelo T1cero", replacer.replace("Modelo T1000"))
    }

    @Test
    fun testNegativeAlreadySpelledOut() {
        assertEquals("uno dos tres", replacer.replace("uno dos tres"))
    }

    @Test
    fun testNegativeBracketedReference() {
        assertEquals("ver [tres] para detalles", replacer.replace("ver [3] para detalles"))
    }

    // ==================== PRODUCTION: real-world ebook paragraphs ====================

    @Test
    fun testProductionNovelOpening() {
        val input = "El 15 de marzo de 2024, la temperatura en Madrid alcanzó los 28°C. Más de 2 millones de personas participaron en el evento."
        val expected = "El quince de marzo de dos mil veinticuatro, la temperatura en Madrid alcanzó los veintiocho grados Celsius. Más de dos millones de personas participaron en el evento."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionScienceArticle() {
        val input = "La velocidad de la luz es aproximadamente 300.000 km/s. El agua hierve a 100°C al nivel del mar."
        val expected = "La velocidad de la luz es aproximadamente trescientos mil km/s. El agua hierve a cien grados Celsius al nivel del mar."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionCookingRecipe() {
        val input = "Precalentar el horno a 180°C. Mezclar 500 gramos de harina con 1/2 cucharadita de sal. Hornear durante 25-30 minutos."
        val expected = "Precalentar el horno a ciento ochenta grados Celsius. Mezclar quinientos gramos de harina con un medio cucharadita de sal. Hornear durante veinticinco a treinta minutos."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionFinancialNews() {
        val input = "El PIB creció un 2,5% en 2024. La inflación se mantuvo en 3,1%. El paro bajó al 11,7%."
        val expected = "El PIB creció un dos coma cinco por ciento en dos mil veinticuatro. La inflación se mantuvo en tres coma uno por ciento. El paro bajó al once coma siete por ciento."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionHistoryText() {
        val input = "El 12 de octubre de 1492, Colón llegó a América. En el siglo 16, España se expandió por el mundo."
        val expected = "El doce de octubre de mil cuatrocientos noventa y dos, Colón llegó a América. En el siglo dieciséis, España se expandió por el mundo."
        assertEquals(expected, replacer.replace(input))
    }

    @Test
    fun testProductionMixedContent() {
        val input = "En 2024, la población de España era de 48 millones. El salario medio es de 1.800€ al mes. El IVA es del 21%."
        val expected = "En dos mil veinticuatro, la población de España era de cuarenta y ocho millones. El salario medio es de mil ochocientos€ al mes. El IVA es del veintiuno por ciento."
        assertEquals(expected, replacer.replace(input))
    }

    // ==================== negative: accented character boundary ====================

    @Test
    fun testNegativeAccentedMas() {
        assertEquals("Más de cinco personas", replacer.replace("Más de 5 personas"))
    }

    @Test
    fun testNegativeAccentedCafe() {
        assertEquals("Tomamos café a las tres", replacer.replace("Tomamos café a las 3"))
    }

    @Test
    fun testNegativeAccentedCorazon() {
        assertEquals("Mi corazón late ochenta veces", replacer.replace("Mi corazón late 80 veces"))
    }

    @Test
    fun testNegativeAccentedPais() {
        assertEquals("El país tiene cuarenta y siete millones", replacer.replace("El país tiene 47 millones"))
    }

    @Test
    fun testNegativeAccentedDia() {
        assertEquals("El día quince de marzo", replacer.replace("El día 15 de marzo"))
    }
}
