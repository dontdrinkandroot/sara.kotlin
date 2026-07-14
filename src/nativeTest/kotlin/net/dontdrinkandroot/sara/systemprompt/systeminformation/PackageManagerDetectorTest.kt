package net.dontdrinkandroot.sara.systemprompt.systeminformation

import net.dontdrinkandroot.sara.systemprompt.systeminformation.general.detectPackageManager
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class PackageManagerDetectorTest {

    @Test
    fun picksHighestPriorityWhenMultipleAvailable() {
        // apt beats dnf/yum/pacman even if all are present.
        assertEquals(
            "Package manager: apt",
            detectPackageManager("apt\ndnf\nyum\npacman")
        )
    }

    @Test
    fun picksDnfOverYum() {
        assertEquals("Package manager: dnf", detectPackageManager("yum\ndnf"))
    }

    @Test
    fun picksSingleAvailable() {
        assertEquals("Package manager: pacman", detectPackageManager("pacman"))
    }

    @Test
    fun ignoresUnknownManagers() {
        assertEquals("Package manager: apk", detectPackageManager("brew\nsnap\napk"))
    }

    @Test
    fun returnsNullWhenNoneAvailable() {
        assertNull(detectPackageManager(""))
        assertNull(detectPackageManager("brew\nsnap"))
        assertNull(detectPackageManager(null))
    }

    @Test
    fun handlesBlankLines() {
        assertEquals("Package manager: zypper", detectPackageManager("\n  \nzypper\n"))
    }
}
