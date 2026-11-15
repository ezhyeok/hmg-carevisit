package dev.carevisit.app

import dev.carevisit.core.Archive
import dev.carevisit.core.Repository

/** Each demo gets a fresh archive, including settings, consent and family token. No disk access. */
internal object VisitRepositories {
    fun open(demo: Boolean, real: () -> Repository): Repository = if (demo) object : Repository {
        private var data = Archive()
        override suspend fun load() = data
        override suspend fun save(archive: Archive) { data = archive }
    } else real()
}
