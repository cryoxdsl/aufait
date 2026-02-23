package com.aufait.alpha.data

import android.content.Context
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelChildren
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import java.io.File
import java.util.concurrent.atomic.AtomicBoolean

interface TorRuntime {
    val status: StateFlow<TorRuntimeStatus>
    suspend fun start()
    suspend fun stop()
}

class EmbeddedTorRuntime(
    context: Context,
    private val externalScope: CoroutineScope
) : TorRuntime {
    private val appContext = context.applicationContext
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _status = MutableStateFlow(TorRuntimeStatus(state = TorRuntimeState.DISABLED))
    override val status: StateFlow<TorRuntimeStatus> = _status.asStateFlow()

    private val starting = AtomicBoolean(false)
    @Volatile private var torProcess: Process? = null
    @Volatile private var socksPort: Int = DEFAULT_SOCKS_PORT

    override suspend fun start() {
        if (torProcess?.isAlive == true) return
        if (!starting.compareAndSet(false, true)) return
        _status.value = TorRuntimeStatus(state = TorRuntimeState.STARTING, bootstrapPercent = 1)

        externalScope.launch {
            runCatching {
                val files = prepareTorFiles()
                socksPort = chooseSocksPort()
                val torrc = writeTorRc(files.workDir, files.torrcTemplate, socksPort)
                val process = ProcessBuilder(
                    files.binary.absolutePath,
                    "--RunAsDaemon", "0",
                    "-f", torrc.absolutePath
                )
                    .directory(files.workDir)
                    .redirectErrorStream(true)
                    .start()
                torProcess = process
                observeTorProcess(process, socksPort)
            }.onFailure { error ->
                _status.value = TorRuntimeStatus(
                    state = TorRuntimeState.UNAVAILABLE,
                    lastError = error.message ?: error::class.java.simpleName
                )
            }
            starting.set(false)
        }
    }

    override suspend fun stop() {
        runCatching { torProcess?.destroy() }
        runCatching { torProcess?.destroyForcibly() }
        torProcess = null
        scope.coroutineContext.cancelChildren()
        _status.value = TorRuntimeStatus(state = TorRuntimeState.DISABLED)
        starting.set(false)
    }

    private fun observeTorProcess(process: Process, socksPort: Int) {
        scope.launch {
            process.inputStream.bufferedReader().useLines { lines ->
                lines.forEach { line ->
                    val bootstrap = parseBootstrapPercent(line)
                    if (bootstrap != null) {
                        _status.value = TorRuntimeStatus(
                            state = if (bootstrap >= 100) TorRuntimeState.READY else TorRuntimeState.STARTING,
                            bootstrapPercent = bootstrap.coerceIn(0, 100),
                            socksHost = if (bootstrap >= 100) "127.0.0.1" else null,
                            socksPort = if (bootstrap >= 100) socksPort else null
                        )
                    } else if (line.contains("Bootstrapped 100%", ignoreCase = true)) {
                        _status.value = TorRuntimeStatus(
                            state = TorRuntimeState.READY,
                            bootstrapPercent = 100,
                            socksHost = "127.0.0.1",
                            socksPort = socksPort
                        )
                    } else if (line.contains("[err]", ignoreCase = true) || line.contains("error", ignoreCase = true)) {
                        _status.value = _status.value.copy(
                            state = if (_status.value.state == TorRuntimeState.READY) TorRuntimeState.READY else TorRuntimeState.ERROR,
                            lastError = line.take(240)
                        )
                    }
                }
            }
        }

        scope.launch {
            val exitCode = runCatching { process.waitFor() }.getOrNull()
            if (torProcess === process) {
                torProcess = null
                _status.value = TorRuntimeStatus(
                    state = TorRuntimeState.ERROR,
                    lastError = "Tor process stopped${exitCode?.let { " ($it)" } ?: ""}"
                )
            }
        }
    }

    private fun parseBootstrapPercent(line: String): Int? {
        val marker = "Bootstrapped "
        val idx = line.indexOf(marker, ignoreCase = true)
        if (idx < 0) return null
        val tail = line.substring(idx + marker.length)
        val digits = tail.takeWhile { it.isDigit() }
        return digits.toIntOrNull()
    }

    private fun prepareTorFiles(): TorFiles {
        val workDir = File(appContext.noBackupFilesDir, "tor-runtime").apply { mkdirs() }
        val binary = File(workDir, "tor-bin")
        val geoip = File(workDir, "geoip")
        val geoip6 = File(workDir, "geoip6")
        val torrcTemplate = File(workDir, "torrc.template")

        extractAssetOrThrow("tor/tor", binary)
        extractAssetIfPresent("tor/geoip", geoip)
        extractAssetIfPresent("tor/geoip6", geoip6)
        extractAssetIfPresent("tor/torrc.template", torrcTemplate)

        binary.setExecutable(true)
        binary.setReadable(true)
        binary.setWritable(true)

        return TorFiles(
            workDir = workDir,
            binary = binary,
            geoip = geoip,
            geoip6 = geoip6,
            torrcTemplate = torrcTemplate
        )
    }

    private fun extractAssetOrThrow(assetPath: String, target: File) {
        runCatching {
            appContext.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }.getOrElse {
            throw IllegalStateException("Asset Tor manquant: $assetPath")
        }
    }

    private fun extractAssetIfPresent(assetPath: String, target: File) {
        runCatching {
            appContext.assets.open(assetPath).use { input ->
                target.outputStream().use { output -> input.copyTo(output) }
            }
        }
    }

    private fun writeTorRc(workDir: File, template: File, socksPort: Int): File {
        val torrc = File(workDir, "torrc")
        val templateContent = if (template.exists()) {
            template.readText()
        } else {
            defaultTorRcTemplate()
        }
        val content = templateContent
            .replace("\${SOCKS_PORT}", socksPort.toString())
            .replace("\${DATA_DIR}", File(workDir, "data").apply { mkdirs() }.absolutePath)
            .replace("\${CACHE_DIR}", File(workDir, "cache").apply { mkdirs() }.absolutePath)
            .replace("\${GEOIP_FILE_BLOCK}", geoipBlockIfPresent(File(workDir, "geoip"), "GeoIPFile"))
            .replace("\${GEOIP6_FILE_BLOCK}", geoipBlockIfPresent(File(workDir, "geoip6"), "GeoIPv6File"))
        torrc.writeText(content)
        return torrc
    }

    private fun geoipBlockIfPresent(file: File, directive: String): String {
        return if (file.exists()) "$directive ${file.absolutePath}" else ""
    }

    private fun defaultTorRcTemplate(): String {
        return """
            SocksPort 127.0.0.1:${'$'}{SOCKS_PORT}
            DataDirectory ${'$'}{DATA_DIR}
            CacheDirectory ${'$'}{CACHE_DIR}
            Log notice stdout
            AvoidDiskWrites 1
            ${'$'}{GEOIP_FILE_BLOCK}
            ${'$'}{GEOIP6_FILE_BLOCK}
        """.trimIndent()
    }

    private fun chooseSocksPort(): Int = DEFAULT_SOCKS_PORT

    private data class TorFiles(
        val workDir: File,
        val binary: File,
        val geoip: File,
        val geoip6: File,
        val torrcTemplate: File
    )

    companion object {
        private const val DEFAULT_SOCKS_PORT = 9050
    }
}
