package app.marlboroadvance.mpvex.di

import android.app.Application
import app.marlboroadvance.mpvex.domain.emby.playback.LoopbackHttpPool
import app.marlboroadvance.mpvex.domain.emby.playback.LoopbackPlaybackServer
import app.marlboroadvance.mpvex.domain.postgres.AutomaticStorage
import app.marlboroadvance.mpvex.domain.postgres.JdbcPostgresExecutor
import app.marlboroadvance.mpvex.domain.postgres.PlaybackConfigurationRepository
import app.marlboroadvance.mpvex.domain.postgres.PostgresConnectionFactory
import app.marlboroadvance.mpvex.utils.update.UpdateManager
import java.nio.file.Files
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertSame
import org.junit.Test
import org.koin.core.Koin
import org.koin.core.context.startKoin
import org.koin.core.context.stopKoin
import org.koin.dsl.module

class DomainModuleTest {
  private fun withDomainModule(block: (Koin) -> Unit) {
    val directory = Files.createTempDirectory("loemby-domain-module-test").toFile()
    val server = LoopbackPlaybackServer(directory, { it }, { it }, { error("Unexpected playback configuration read") })
    val configurations = PlaybackConfigurationRepository(
      JdbcPostgresExecutor(
        AutomaticStorage(),
        PostgresConnectionFactory { _, _ -> error("Dependency construction must not connect to PostgreSQL") },
      ),
    )
    try {
      val application = startKoin {
        allowOverride(true)
        modules(
          domainModule,
          module {
            single { server }
            single { configurations }
          },
        )
      }
      block(application.koin)
    } finally {
      try {
        stopKoin()
      } finally {
        try {
          runBlocking { server.stop() }
        } finally {
          directory.deleteRecursively()
        }
      }
    }
  }

  @Test fun httpPoolResolvesBeforePlaybackServerAndSharesItsInstance() = withDomainModule { koin ->
    val http = koin.get<LoopbackHttpPool>()
    assertSame(koin.get<LoopbackPlaybackServer>().http, http)
    assertSame(http, koin.get<LoopbackHttpPool>())
  }

  @Test fun httpPoolResolvesAfterPlaybackServerStartupConstruction() = withDomainModule { koin ->
    val server = koin.get<LoopbackPlaybackServer>()
    assertSame(server.http, koin.get<LoopbackHttpPool>())
    assertSame(server, koin.get<LoopbackPlaybackServer>())
  }

  @Test fun updateManagerConstructsWithProductionHttpPoolBinding() = withDomainModule { koin ->
    koin.get<LoopbackPlaybackServer>()
    assertNotNull(UpdateManager(Application()))
    assertSame(koin.get<LoopbackPlaybackServer>().http, koin.get<LoopbackHttpPool>())
  }
}
