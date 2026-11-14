package app.marlboroadvance.mpvex.utils.update

internal const val LOEMBYA_PROJECT_URL = "https://github.com/NPCDW/loembyA"
internal const val LOEMBYA_RELEASE_API = "https://api.github.com/repos/NPCDW/loembyA/releases/latest"

/** Published assets must belong to this project and the requesting release, never an arbitrary APK. */
internal fun selectLoembyAReleaseAsset(
  assets: List<Asset>,
  tag: String,
  supportedAbis: List<String>,
): Asset? {
  if (!tag.matches(Regex("v[0-9]+\\.[0-9]+\\.[0-9]+(?:-[0-9A-Za-z]+(?:[.-][0-9A-Za-z]+)*)?"))) return null
  return supportedAbis.firstNotNullOfOrNull { abi ->
    assets.singleOrNull { asset ->
      val name = "loembyA-$abi-$tag.apk"
      asset.name == name && asset.size > 0 &&
        asset.downloadUrl == "$LOEMBYA_PROJECT_URL/releases/download/$tag/$name"
    }
  }
}
