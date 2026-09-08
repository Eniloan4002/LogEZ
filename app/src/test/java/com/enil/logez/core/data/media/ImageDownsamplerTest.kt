package com.enil.logez.core.data.media

import org.junit.Assert.assertEquals
import org.junit.Test

class ImageDownsamplerTest {
    @Test
    fun `a 4000x3000 photo downsampled to 1024 needs inSampleSize 4`() {
        assertEquals(4, inSampleSizeFor(4000, 3000, 1024))
    }

    @Test
    fun `an image already under the target needs no downsampling`() {
        assertEquals(1, inSampleSizeFor(800, 600, 1024))
    }

    @Test
    fun `an image exactly at the target needs no downsampling`() {
        assertEquals(1, inSampleSizeFor(1024, 768, 1024))
    }

    @Test
    fun `a portrait photo samples against its long edge (height), not width`() {
        assertEquals(4, inSampleSizeFor(3000, 4000, 1024))
    }

    @Test
    fun `a huge photo needs a correspondingly larger power-of-two sample size`() {
        assertEquals(8, inSampleSizeFor(8000, 6000, 1024))
    }

    @Test
    fun `invalid dimensions or target return 1 rather than looping forever`() {
        assertEquals(1, inSampleSizeFor(0, 0, 1024))
        assertEquals(1, inSampleSizeFor(4000, 3000, 0))
        assertEquals(1, inSampleSizeFor(-100, 200, 1024))
    }
}
