package nz.eloque.openmaps.map

import nz.eloque.openmaps.core.model.GeoBounds
import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class TileCountTest {
    @Test
    fun aSpotNeedsOneTilePerZoomOfEachLayer() {
        val karlsruhePalace = GeoBounds(49.0130, 8.4040, 49.0135, 8.4045)

        assertEquals(15 + 7, tileCount(karlsruhePalace))
    }

    @Test
    fun aCityFitsWhileAStateDoesNot() {
        val karlsruhe = GeoBounds(48.95, 8.30, 49.09, 8.50)
        val badenWuerttemberg = GeoBounds(47.5, 7.5, 49.8, 10.5)

        assertTrue(tileCount(karlsruhe) <= MAX_DOWNLOAD_TILES)
        assertTrue(tileCount(badenWuerttemberg) > MAX_DOWNLOAD_TILES)
    }
}
