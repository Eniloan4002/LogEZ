package com.enil.logez.core.common

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MapStyleDarkModeTest {
    private fun styleWith(vararg layers: String): String =
        """{"version":8,"layers":[${layers.joinToString(",")}]}"""

    private fun colorOf(darkenedJson: String, layerIndex: Int, paintKey: String): String {
        val layers = Json.parseToJsonElement(darkenedJson).jsonObject["layers"]!!.jsonArray
        return layers[layerIndex].jsonObject["paint"]!!.jsonObject[paintKey]!!.jsonPrimitive.content
    }

    @Test
    fun `a fill layer's hex color has its lightness inverted -- white becomes black`() {
        val result = MapStyleDarkMode.darken(styleWith("""{"id":"a","type":"fill","paint":{"fill-color":"#ffffff"}}"""))
        assertEquals("rgba(0, 0, 0, 1.0)", colorOf(result, 0, "fill-color"))
    }

    @Test
    fun `a background layer's hex color has its lightness inverted -- black becomes white`() {
        val result = MapStyleDarkMode.darken(styleWith("""{"id":"bg","type":"background","paint":{"background-color":"#000000"}}"""))
        assertEquals("rgba(255, 255, 255, 1.0)", colorOf(result, 0, "background-color"))
    }

    @Test
    fun `a real style color inverts to the expected hue-preserving dark counterpart`() {
        // #f8f4f0 (this app's actual bundled background fill) -- ground truth computed via Python's colorsys HLS round-trip.
        val result = MapStyleDarkMode.darken(styleWith("""{"id":"bg","type":"background","paint":{"background-color":"#f8f4f0"}}"""))
        assertEquals("rgba(15, 11, 7, 1.0)", colorOf(result, 0, "background-color"))
    }

    @Test
    fun `mid-gray is its own inverse`() {
        val result = MapStyleDarkMode.darken(styleWith("""{"id":"a","type":"fill","paint":{"fill-color":"#808080"}}"""))
        assertEquals("rgba(127, 127, 127, 1.0)", colorOf(result, 0, "fill-color"))
    }

    @Test
    fun `rgb, rgba, hsl and hsla source formats all parse and invert`() {
        val result = MapStyleDarkMode.darken(
            styleWith(
                """{"id":"a","type":"fill","paint":{"fill-color":"rgb(255, 255, 255)"}}""",
                """{"id":"b","type":"fill","paint":{"fill-color":"rgba(0, 0, 0, 0.5)"}}""",
                """{"id":"c","type":"fill","paint":{"fill-color":"hsl(0, 0%, 100%)"}}""",
                """{"id":"d","type":"fill","paint":{"fill-color":"hsla(0, 0%, 0%, 0.3)"}}""",
            ),
        )
        assertEquals("rgba(0, 0, 0, 1.0)", colorOf(result, 0, "fill-color"))
        assertEquals("rgba(255, 255, 255, 0.5)", colorOf(result, 1, "fill-color"))
        assertEquals("rgba(0, 0, 0, 1.0)", colorOf(result, 2, "fill-color"))
        assertEquals("rgba(255, 255, 255, 0.3)", colorOf(result, 3, "fill-color"))
    }

    @Test
    fun `a line layer's color is left completely untouched -- roads must stay light against newly-dark land`() {
        val result = MapStyleDarkMode.darken(styleWith("""{"id":"road","type":"line","paint":{"line-color":"#fc8"}}"""))
        assertEquals("#fc8", colorOf(result, 0, "line-color"))
    }

    @Test
    fun `a symbol (label) layer's color is left untouched`() {
        val result = MapStyleDarkMode.darken(styleWith("""{"id":"label","type":"symbol","paint":{"text-color":"#333"}}"""))
        assertEquals("#333", colorOf(result, 0, "text-color"))
    }

    @Test
    fun `colors nested inside step and interpolate expressions are inverted, zoom stops and operators are untouched`() {
        val result = MapStyleDarkMode.darken(
            styleWith("""{"id":"a","type":"fill","paint":{"fill-color":["step",["zoom"],"#ffffff",16,"#000000"]}}"""),
        )
        val expr = Json.parseToJsonElement(result).jsonObject["layers"]!!.jsonArray[0]
            .jsonObject["paint"]!!.jsonObject["fill-color"]!!.jsonArray
        assertEquals("step", expr[0].jsonPrimitive.content)
        assertEquals("zoom", expr[1].jsonArray[0].jsonPrimitive.content)
        assertEquals("rgba(0, 0, 0, 1.0)", expr[2].jsonPrimitive.content) // white -> black
        assertEquals(16, expr[3].jsonPrimitive.content.toInt()) // zoom stop number untouched
        assertEquals("rgba(255, 255, 255, 1.0)", expr[4].jsonPrimitive.content) // black -> white
    }

    @Test
    fun `a non-color paint property is left untouched`() {
        val result = MapStyleDarkMode.darken(styleWith("""{"id":"a","type":"fill","paint":{"fill-opacity":0.5}}"""))
        val opacity = Json.parseToJsonElement(result).jsonObject["layers"]!!.jsonArray[0]
            .jsonObject["paint"]!!.jsonObject["fill-opacity"]!!.jsonPrimitive.content
        assertEquals(0.5, opacity.toDouble(), 0.0001)
    }

    @Test
    fun `layer order is preserved`() {
        val result = MapStyleDarkMode.darken(
            styleWith(
                """{"id":"first","type":"background","paint":{"background-color":"#fff"}}""",
                """{"id":"second","type":"line","paint":{"line-color":"#000"}}""",
                """{"id":"third","type":"fill","paint":{"fill-color":"#fff"}}""",
            ),
        )
        val layers = Json.parseToJsonElement(result).jsonObject["layers"]!!.jsonArray
        assertEquals(3, layers.size)
        assertEquals("first", layers[0].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("second", layers[1].jsonObject["id"]!!.jsonPrimitive.content)
        assertEquals("third", layers[2].jsonObject["id"]!!.jsonPrimitive.content)
    }

    @Test
    fun `a layer with no paint object is left untouched, no crash`() {
        val result = MapStyleDarkMode.darken(styleWith("""{"id":"a","type":"fill"}"""))
        assertTrue(result.contains("\"id\":\"a\""))
    }
}
