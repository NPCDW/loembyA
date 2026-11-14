package app.marlboroadvance.mpvex.utils.update

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LoembyAReleaseSourceTest {
  private val tag = "v0.1.1"
  private fun asset(abi: String = "arm64-v8a", releaseTag: String = tag): Asset {
    val name = "loembyA-$abi-$releaseTag.apk"
    return Asset("$LOEMBYA_PROJECT_URL/releases/download/$releaseTag/$name", name, 100, "application/octet-stream")
  }

  @Test fun projectAndReleaseSourcesBelongToLoembyA() {
    assertEquals("https://github.com/NPCDW/loembyA", LOEMBYA_PROJECT_URL)
    assertEquals("https://api.github.com/repos/NPCDW/loembyA/releases/latest", LOEMBYA_RELEASE_API)
  }

  @Test fun exactReleaseAndSupportedArchitectureAreRequired() {
    val current = asset()
    assertEquals(current, selectLoembyAReleaseAsset(listOf(asset("x86_64"), current), tag, listOf("arm64-v8a")))
    assertNull(selectLoembyAReleaseAsset(listOf(current), tag, listOf("x86_64")))
    assertNull(selectLoembyAReleaseAsset(listOf(asset(releaseTag = "v0.1.0")), tag, listOf("arm64-v8a")))
  }

  @Test fun upstreamAssetsAndArbitraryDownloadHostsAreRejected() {
    val current = asset()
    assertNull(selectLoembyAReleaseAsset(listOf(current.copy(name = "mpvEx-arm64-v8a.apk")), tag, listOf("arm64-v8a")))
    assertNull(selectLoembyAReleaseAsset(listOf(current.copy(downloadUrl = "https://example.test/file.apk")), tag,
      listOf("arm64-v8a")))
  }

  @Test fun ambiguousEmptyAndInvalidTagAssetsAreRejected() {
    val current = asset()
    assertNull(selectLoembyAReleaseAsset(listOf(current, current), tag, listOf("arm64-v8a")))
    assertNull(selectLoembyAReleaseAsset(listOf(current.copy(size = 0)), tag, listOf("arm64-v8a")))
    assertNull(selectLoembyAReleaseAsset(listOf(current), "../latest", listOf("arm64-v8a")))
  }

  @Test fun prereleasesFollowTheSamePublishedNameContract() {
    val prerelease = asset(releaseTag = "v0.1.1-beta.1")
    assertEquals(prerelease, selectLoembyAReleaseAsset(listOf(prerelease), "v0.1.1-beta.1", listOf("arm64-v8a")))
  }
}
