import kotlin.test.Test
import kotlin.test.assertEquals

// Mirrors TextKit/fixtures/text-kit-cases.json → foldDiacritics. Kept in sync by hand with the
// JS suite in scripts/lib/diacritics.test.js, which loads that file directly — TextKit has no
// JVM/JS test target able to read a repo file at test time. See ADR 0026.
class DiacriticFoldingTest {

    @Test
    fun foldDiacritics_matchesSharedFixture() {
        assertEquals("Plain text", foldDiacritics("Plain text"))
        assertEquals("acdeenorstuuyz", foldDiacritics("áčďěéňóřšťúůýž"))
        assertEquals("ACDEENORSTUUYZ", foldDiacritics("ÁČĎĚÉŇÓŘŠŤÚŮÝŽ"))
        assertEquals("rohlik", foldDiacritics("rohlík"))
        assertEquals("Polotucne mleko", foldDiacritics("Polotučné mléko"))
    }
}
