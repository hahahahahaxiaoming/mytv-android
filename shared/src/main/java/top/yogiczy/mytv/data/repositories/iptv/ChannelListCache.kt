package top.yogiczy.mytv.data.repositories.iptv

import java.io.File
import java.io.IOException

/** Keeps the previous complete file readable across a failed or interrupted replacement. */
internal class ChannelListCache(private val file: File) {
    private val backup = File(file.parentFile, file.name + ".bak")

    fun read(): String = synchronized(lock) {
        (if (file.exists()) file else backup).readText()
    }

    fun save(data: String) = synchronized(lock) {
        try {
            val temporary = File.createTempFile("web-iptv-", ".tmp", file.parentFile)
            try {
                temporary.writeText(data)
                if (!temporary.renameTo(file)) {
                    // Some filesystems cannot rename over an existing destination.
                    if (file.exists()) {
                        if (backup.exists() && !backup.delete()) return@synchronized
                        if (!file.renameTo(backup)) return@synchronized
                    }
                    if (!temporary.renameTo(file)) return@synchronized
                }
                backup.delete()
            } finally {
                temporary.delete()
            }
        } catch (_: IOException) {
            // Playback can use the downloaded and validated list even when storage is full.
        }
        Unit
    }

    private companion object { val lock = Any() }
}
