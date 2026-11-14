package app.marlboroadvance.mpvex.ui.preferences

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AboutProjectContractTest {
  private fun source(path: String): String {
    val moduleFile = File(path)
    val file = if (moduleFile.exists()) moduleFile else File("app", path)
    return file.readText()
  }

  @Test fun ownProjectLinkIsAboveAcknowledgmentsAndLicensesLiveBelowIt() {
    val about = source("src/main/java/app/marlboroadvance/mpvex/ui/preferences/AboutScreen.kt")
    val project = about.indexOf("R.string.i18n_about_project_source")
    val acknowledgments = about.indexOf("R.string.i18n_ack_title")
    val libraries = about.indexOf("backstack.add(LibrariesScreen)")
    assertTrue(project >= 0 && project < acknowledgments)
    assertTrue(libraries > acknowledgments)
    assertTrue(about.contains("val githubUrl = LOEMBYA_PROJECT_URL"))
    assertTrue(about.contains("https://github.com/nu1lkali/CineIsle"))
    assertTrue(about.contains("https://github.com/marlboro-advance/mpvEx"))
    assertFalse(about.contains("i18n_about_upstream_project"))
  }

  @Test fun gradleRetainsRealVersionAndSigningConfigurationWithoutOldReleaseJournal() {
    val gradle = source("build.gradle.kts")
    assertTrue(gradle.contains("versionCode = 237"))
    assertTrue(gradle.contains("versionName = \"0.2.0\""))
    assertTrue(gradle.contains("externalReleaseSigning"))
    assertFalse(gradle.contains("修复轮不涨名"))
    assertFalse(gradle.contains("versionCode 62"))
    assertFalse(gradle.contains("2026-10-19.md"))
  }
}
