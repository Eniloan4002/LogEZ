package com.enil.logez.fakes

import com.enil.logez.core.data.media.MediaFileCleaner

/** Records every path it was asked to clean up, in order. */
class FakeMediaFileCleaner : MediaFileCleaner {
    val requested = mutableListOf<String>()

    override suspend fun deleteIfUnreferenced(relativePath: String) {
        requested += relativePath
    }
}
