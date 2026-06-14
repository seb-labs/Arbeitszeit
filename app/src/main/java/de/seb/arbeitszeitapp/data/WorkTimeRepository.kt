package de.seb.arbeitszeitapp.data

import android.content.Context

/**
 * Legacy compatibility shim kept so the old file path no longer breaks the build.
 * The new implementation lives in [WorkSessionRepository].
 */
@Deprecated("Use WorkSessionRepository instead")
class WorkTimeRepository(context: Context) {
    @Suppress("unused")
    private val delegate = WorkSessionRepository(context)
}
