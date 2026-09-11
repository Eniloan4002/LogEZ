package com.enil.logez.feature.activity.map

import android.content.Context
import com.enil.logez.core.common.MapStyleDarkMode
import java.io.File
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * M21b. MapLibre's `mbtiles://` loader needs a filesystem path, not an APK-asset reference — the
 * bundled Metro Manila tile database is copied to internal storage once, then referenced by its
 * real path. Re-copies if the bundled asset's size no longer matches the installed copy (an app
 * update shipped a newer region file); a plain `!dest.exists()` check alone would never notice.
 */
object OfflineMapAssets {
    private const val MBTILES_ASSET_PATH = "map/region.mbtiles"
    private const val STYLE_ASSET_PATH = "map/style.json"
    private const val MBTILES_FILENAME = "region.mbtiles"
    private const val MBTILES_PATH_TOKEN = "__MBTILES_ABSOLUTE_PATH__"

    suspend fun ensureMbtilesInstalled(context: Context): File = withContext(Dispatchers.IO) {
        val dest = File(context.filesDir, MBTILES_FILENAME)
        val assetSize = context.assets.openFd(MBTILES_ASSET_PATH).use { it.length }
        if (!dest.exists() || dest.length() != assetSize) {
            context.assets.open(MBTILES_ASSET_PATH).use { input ->
                dest.outputStream().use { output -> input.copyTo(output) }
            }
        }
        dest
    }

    /**
     * Loads the bundled style JSON with the mbtiles source's placeholder token filled in with a
     * real filesystem path, then darkened via [MapStyleDarkMode] to match this dark-only app's
     * theme (LogEzTheme) -- the bundled style itself stays an untouched, light "OSM Bright" style
     * on disk; only the in-memory copy fed to MapLibre is transformed.
     */
    suspend fun loadStyleJson(context: Context, mbtilesAbsolutePath: String): String = withContext(Dispatchers.IO) {
        val raw = context.assets.open(STYLE_ASSET_PATH).bufferedReader().use { it.readText() }
        MapStyleDarkMode.darken(raw.replace(MBTILES_PATH_TOKEN, mbtilesAbsolutePath))
    }
}
