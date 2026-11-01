package app.petdance.media

import android.content.Context
import androidx.media3.database.StandaloneDatabaseProvider
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.cache.CacheDataSource
import androidx.media3.datasource.cache.LeastRecentlyUsedCacheEvictor
import androidx.media3.datasource.cache.SimpleCache
import java.io.File

class VideoCache(context: Context) {
    private val cache = SimpleCache(
        File(context.cacheDir, "video-cache"),
        LeastRecentlyUsedCacheEvictor(256L * 1024 * 1024),
        StandaloneDatabaseProvider(context),
    )

    val dataSourceFactory: CacheDataSource.Factory = CacheDataSource.Factory()
        .setCache(cache)
        .setUpstreamDataSourceFactory(DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true))
        .setCacheKeyFactory { spec -> spec.uri.buildUpon().clearQuery().build().toString() }
        .setFlags(CacheDataSource.FLAG_IGNORE_CACHE_ON_ERROR)
}
