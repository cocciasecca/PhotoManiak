package app.inphotomaniak
import org.junit.Test
import org.junit.Assert.*
class FolderRulesTest {
    private val rules=FolderRules(setOf("/Foto/"),setOf("/Foto/Privato/"))
    @Test fun unlimitedDepth() { assertTrue(rules.accepts("/Foto/DCIM/Camera/2026/10/a.jpg")) }
    @Test fun noSiblingPrefixMatch() { assertFalse(rules.accepts("/Fotografie/a.jpg")) }
    @Test fun exclusionsAreRecursive() { assertFalse(rules.accepts("/Foto/Privato/2026/a.jpg")) }
    @Test fun exclusionWins() { assertFalse(FolderRules(setOf("/Foto/Privato/"),setOf("/Foto/")).accepts("/Foto/Privato/a.jpg")) }
    @Test fun encodedNamesAreNotDecodedTwice() { assertTrue(FolderRules(setOf("/Foto%20PC/" )).accepts("/Foto%20PC/a%252Fb.jpg")) }
    @Test fun rootIncludesEverything() { assertTrue(FolderRules(setOf("/")).accepts("/Foto/a.jpg")) }
}
