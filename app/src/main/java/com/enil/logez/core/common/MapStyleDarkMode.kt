package com.enil.logez.core.common

import kotlin.math.roundToInt
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive

/**
 * LogEZ is a dark-only app ([com.enil.logez.core.designsystem.LogEzTheme]), but the bundled
 * OpenMapTiles-schema style (`assets/map/style.json`) was authored as a light "OSM Bright"-style
 * map -- a jarring white/tan rectangle inside an otherwise all-dark UI. Rather than hand-author a
 * second ~130-layer style (a large, error-prone cartography effort of its own), this transforms the
 * existing style JSON programmatically at load time: every *area* color (`fill`/`background` layer
 * paint properties ending in `-color` -- land, water, buildings, landuse) has its HSL lightness
 * inverted; every `line` layer (roads, waterways) and `symbol` layer (labels) is left untouched.
 * That split is deliberate, not an oversight: this style's road colors (`#fc8` motorway orange,
 * `#ffdaa6` peach, near-white casings) were authored to read clearly against the *original* light
 * land -- they read exactly as well, arguably better, as light roads against newly-darkened land,
 * which is precisely the "light roads on dark ground" look real dark map styles (Google Maps night
 * mode, Mapbox Dark) hand-author on purpose. Inverting line colors too would instead turn those
 * already-good light road colors dark, making them vanish against dark land.
 *
 * Uses `kotlinx.serialization`'s [JsonElement] tree (already a project dependency), not
 * `org.json` -- `org.json.JSONObject` is an Android-framework stub on the plain JVM unit-test
 * classpath and throws unless run under Robolectric; this stays a fast, plain-JVM-testable pure
 * function like every other `core/common` utility.
 */
object MapStyleDarkMode {
    private val DARKENED_LAYER_TYPES = setOf("fill", "background")

    fun darken(rawStyleJson: String): String {
        val root = Json.parseToJsonElement(rawStyleJson).jsonObject
        val layers = root["layers"]?.jsonArray ?: return rawStyleJson
        val newLayers = buildJsonArray {
            layers.forEach { layerElement ->
                val layer = layerElement.jsonObject
                val type = layer["type"]?.jsonPrimitive?.content
                add(if (type in DARKENED_LAYER_TYPES) darkenLayer(layer) else layerElement)
            }
        }
        val newRoot = buildJsonObject {
            root.forEach { (key, value) -> put(key, if (key == "layers") newLayers else value) }
        }
        return newRoot.toString()
    }

    private fun darkenLayer(layer: JsonObject): JsonObject {
        val paint = layer["paint"]?.jsonObject ?: return layer
        val newPaint = buildJsonObject {
            paint.forEach { (key, value) -> put(key, if (key.endsWith("-color")) invertElement(value) else value) }
        }
        return buildJsonObject {
            layer.forEach { (key, value) -> put(key, if (key == "paint") newPaint else value) }
        }
    }

    /** Recurses into expression arrays (e.g. `["step", ["zoom"], "#fff", 16, "#eee"]`) -- only string elements that actually parse as a CSS color are touched; zoom stops and expression-operator keywords fail [parseCssColor] and pass through unchanged. */
    private fun invertElement(element: JsonElement): JsonElement = when {
        element is JsonArray -> buildJsonArray { element.forEach { add(invertElement(it)) } }
        element is JsonPrimitive && element.isString ->
            parseCssColor(element.content)?.let { JsonPrimitive(toRgbaString(invertLightness(it))) } ?: element
        else -> element
    }

    private data class Rgba(val r: Int, val g: Int, val b: Int, val a: Double)

    private val HEX6 = Regex("^#([0-9a-fA-F]{6})$")
    private val HEX3 = Regex("^#([0-9a-fA-F]{3})$")
    private val RGB = Regex("""^rgb\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*\)$""")
    private val RGBA = Regex("""^rgba\(\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*,\s*([\d.]+)\s*\)$""")
    private val HSL = Regex("""^hsl\(\s*([\d.]+)\s*,\s*([\d.]+)%\s*,\s*([\d.]+)%\s*\)$""")
    private val HSLA = Regex("""^hsla\(\s*([\d.]+)\s*,\s*([\d.]+)%\s*,\s*([\d.]+)%\s*,\s*([\d.]+)\s*\)$""")

    private fun parseCssColor(raw: String): Rgba? {
        val s = raw.trim()
        HEX6.find(s)?.let {
            val v = it.groupValues[1]
            return Rgba(v.substring(0, 2).toInt(16), v.substring(2, 4).toInt(16), v.substring(4, 6).toInt(16), 1.0)
        }
        HEX3.find(s)?.let {
            val v = it.groupValues[1]
            return Rgba(
                "${v[0]}${v[0]}".toInt(16),
                "${v[1]}${v[1]}".toInt(16),
                "${v[2]}${v[2]}".toInt(16),
                1.0,
            )
        }
        RGBA.find(s)?.let {
            val (r, g, b, a) = it.destructured
            return Rgba(r.toDouble().roundToInt(), g.toDouble().roundToInt(), b.toDouble().roundToInt(), a.toDouble())
        }
        RGB.find(s)?.let {
            val (r, g, b) = it.destructured
            return Rgba(r.toDouble().roundToInt(), g.toDouble().roundToInt(), b.toDouble().roundToInt(), 1.0)
        }
        HSLA.find(s)?.let {
            val (h, sat, l, a) = it.destructured
            return hslToRgba(h.toDouble(), sat.toDouble() / 100.0, l.toDouble() / 100.0, a.toDouble())
        }
        HSL.find(s)?.let {
            val (h, sat, l) = it.destructured
            return hslToRgba(h.toDouble(), sat.toDouble() / 100.0, l.toDouble() / 100.0, 1.0)
        }
        return null
    }

    private fun rgbaToHsl(c: Rgba): Triple<Double, Double, Double> {
        val r = c.r / 255.0
        val g = c.g / 255.0
        val b = c.b / 255.0
        val max = maxOf(r, g, b)
        val min = minOf(r, g, b)
        val l = (max + min) / 2.0
        if (max == min) return Triple(0.0, 0.0, l)
        val d = max - min
        val s = if (l > 0.5) d / (2 - max - min) else d / (max + min)
        val h = when (max) {
            r -> (g - b) / d + (if (g < b) 6.0 else 0.0)
            g -> (b - r) / d + 2.0
            else -> (r - g) / d + 4.0
        } * 60.0
        return Triple(h, s, l)
    }

    private fun hslToRgba(h: Double, s: Double, l: Double, a: Double): Rgba {
        if (s == 0.0) {
            val gray = (l * 255).roundToInt().coerceIn(0, 255)
            return Rgba(gray, gray, gray, a)
        }
        val q = if (l < 0.5) l * (1 + s) else l + s - l * s
        val p = 2 * l - q
        val hk = (((h % 360) + 360) % 360) / 360.0
        fun hueToRgb(t0: Double): Double {
            var t = t0
            if (t < 0) t += 1.0
            if (t > 1) t -= 1.0
            return when {
                t < 1.0 / 6 -> p + (q - p) * 6 * t
                t < 1.0 / 2 -> q
                t < 2.0 / 3 -> p + (q - p) * (2.0 / 3 - t) * 6
                else -> p
            }
        }
        val r = (hueToRgb(hk + 1.0 / 3) * 255).roundToInt().coerceIn(0, 255)
        val g = (hueToRgb(hk) * 255).roundToInt().coerceIn(0, 255)
        val b = (hueToRgb(hk - 1.0 / 3) * 255).roundToInt().coerceIn(0, 255)
        return Rgba(r, g, b, a)
    }

    private fun invertLightness(c: Rgba): Rgba {
        val (h, s, l) = rgbaToHsl(c)
        return hslToRgba(h, s, 1.0 - l, c.a)
    }

    private fun toRgbaString(c: Rgba): String = "rgba(${c.r}, ${c.g}, ${c.b}, ${c.a})"
}
