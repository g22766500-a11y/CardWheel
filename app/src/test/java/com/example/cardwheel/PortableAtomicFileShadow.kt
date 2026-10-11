package com.example.cardwheel

import android.util.AtomicFile
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements
import java.io.File
import java.nio.file.Files
import java.nio.file.StandardCopyOption

/** Adapt only the platform rename operation to the host JVM filesystem. */
@Implements(AtomicFile::class)
class PortableAtomicFileShadow {
    companion object {
        @JvmStatic
        @Implementation
        protected fun rename(source: File, target: File) {
            Files.move(source.toPath(), target.toPath(), StandardCopyOption.REPLACE_EXISTING)
        }
    }
}
