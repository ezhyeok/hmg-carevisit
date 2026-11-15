package dev.carevisit.core

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.io.File
import java.io.FileOutputStream
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** One private file is one consent + records transaction. A failed read is surfaced, never silently granted. */
class AtomicRepository(private val file: File) : Repository {
    private val json=Json { ignoreUnknownKeys=true; encodeDefaults=true }
    override suspend fun load(): Archive = withContext(Dispatchers.IO) {
        val temp=File(file.path+".tmp")
        // Temp can contain health data from an interrupted write. It is never a recovery source.
        if(temp.exists()) check(temp.delete())
        if(!file.exists()) Archive() else {
            require(file.length()<=4*1024*1024) { "archive exceeds limit" }
            json.decodeFromString<Archive>(file.readText(Charsets.UTF_8)).also(::validate)
        }
    }
    override suspend fun save(archive: Archive) = withContext(Dispatchers.IO) {
        validate(archive)
        val bytes=json.encodeToString(archive).toByteArray(Charsets.UTF_8)
        require(bytes.size<=4*1024*1024) { "archive exceeds limit" }
        file.parentFile?.mkdirs()
        val temp=File(file.path+".tmp")
        try {
            FileOutputStream(temp).use { it.write(bytes); it.fd.sync() }
            // ATOMIC_MOVE is required; failure keeps the old valid transaction and stops the effect.
            Files.move(temp.toPath(),file.toPath(),StandardCopyOption.ATOMIC_MOVE,StandardCopyOption.REPLACE_EXISTING)
        } finally { if(temp.exists()) temp.delete() }
        Unit
    }
    private fun validate(a: Archive) {
        require(a.schema==1)
        require(a.settings.retentionDays in 1..365)
        require(a.visits.all(Visit::traceable) && a.session.visit?.traceable()!=false) { "untraceable slot" }
        require(a.consent.healthAt!=null || (a.visits.isEmpty() && a.session.visit==null && a.settings.hospitals.isEmpty())) { "health record without consent" }
    }
}
