package com.kamalahmed.prayerapp.widget

import java.util.Locale

enum class WidgetKind(val label: String, val description: String) {
    NEXT_SMALL("Next prayer", "2×2 · next prayer, time and live countdown"),
    ALL_WIDE("All prayers", "4×1 · all five prayers in a row with the hijri day"),
    NEXT_STREAK("Next + streak", "3×2 · next prayer, today's progress and streak"),
    TRACKER("Streak tracker", "4×1 · tap a prayer to mark it prayed"),
    LARGE("Dashboard", "4×4 · ring, all prayers, hijri date and streak"),
    MINIMAL("Minimal text", "Text only · next prayer on one line"),
}

enum class WidgetTheme(val label: String) { GLASS("Glass"), SOLID("Solid"), MINIMAL("Minimal"), GRADIENT("Gradient"), LIGHT("Light") }

/** Where the background panel sits inside a taller/wider widget cell. */
enum class WidgetAlign(val label: String) { TOP("Top"), CENTER("Center"), BOTTOM("Bottom") }

enum class WidgetWeight(val label: String) { NORMAL("Regular"), MEDIUM("Medium"), BOLD("Bold") }

/** Follow the app setting, or force on/off. */
enum class Tri(val label: String) { APP("Follow app"), ON("On"), OFF("Off") }

data class NamedColor(val name: String, val argb: Int)

val AccentPalette = listOf(
    NamedColor("Green", 0xFF5BC16B.toInt()),
    NamedColor("Gold", 0xFFE6A23C.toInt()),
    NamedColor("Teal", 0xFF2EC4B6.toInt()),
    NamedColor("Blue", 0xFF4D9DE0.toInt()),
    NamedColor("Purple", 0xFF9B7BEA.toInt()),
    NamedColor("Pink", 0xFFE5739C.toInt()),
    NamedColor("Orange", 0xFFF0883E.toInt()),
    NamedColor("White", 0xFFF2F2F2.toInt()),
)

/** Per-widget-instance appearance. Stored per appWidgetId. */
data class WidgetStyle(
    val theme: WidgetTheme = WidgetTheme.GLASS,
    val accent: Int = AccentPalette[0].argb,
    /** Background alpha 0..100 (real ARGB alpha; no blur). */
    val opacity: Int = 80,
    val corner: Int = 20,
    /** Top/bottom padding inside the panel (dp, 0..32). */
    val padV: Int = 10,
    /** Start/end padding inside the panel (dp, 0..32). */
    val padH: Int = 12,
    /** Spacing between items (dp, 0..24). */
    val spacing: Int = 6,
    val align: WidgetAlign = WidgetAlign.CENTER,
    val textScale: Float = 1f,
    val iconScale: Float = 1f,
    val boxScale: Float = 1f,
    val weight: WidgetWeight = WidgetWeight.MEDIUM,
    val showHijri: Boolean = true,
    val showCountdown: Boolean = true,
    val showIcons: Boolean = true,
    val showAmPm: Boolean = true,
    val showSunrise: Boolean = false,
    val showMidnight: Boolean = false,
    val clock24: Tri = Tri.APP,
    val abbreviations: Tri = Tri.APP,
) {
    fun use24h(app: Boolean) = when (clock24) { Tri.APP -> app; Tri.ON -> true; Tri.OFF -> false }
    fun abbr(app: Boolean) = when (abbreviations) { Tri.APP -> app; Tri.ON -> true; Tri.OFF -> false }

    fun encode(): String = listOf(
        "v" to "2", "theme" to theme.name, "accent" to accent.toString(), "opacity" to opacity.toString(),
        "corner" to corner.toString(), "padv" to padV.toString(), "padh" to padH.toString(), "spacing" to spacing.toString(), "align" to align.name,
        "text" to fmt(textScale), "icon" to fmt(iconScale), "box" to fmt(boxScale), "weight" to weight.name,
        "hijri" to b(showHijri), "countdown" to b(showCountdown), "icons" to b(showIcons), "ampm" to b(showAmPm),
        "sunrise" to b(showSunrise), "midnight" to b(showMidnight), "clock" to clock24.name, "abbr" to abbreviations.name,
    ).joinToString(";") { "${it.first}=${it.second}" }

    /** Values clamped into their valid ranges. */
    fun sanitized() = copy(
        opacity = opacity.coerceIn(0, 100), corner = corner.coerceIn(0, 40), padV = padV.coerceIn(0, 32), padH = padH.coerceIn(0, 32),
        spacing = spacing.coerceIn(0, 24), textScale = textScale.coerceIn(0.6f, 1.8f),
        iconScale = iconScale.coerceIn(0.5f, 1.8f), boxScale = boxScale.coerceIn(0.6f, 1.5f),
    )

    companion object {
        private fun b(v: Boolean) = if (v) "1" else "0"
        private fun fmt(f: Float) = String.format(Locale.US, "%.2f", f)

        fun defaults(kind: WidgetKind): WidgetStyle = when (kind) {
            WidgetKind.NEXT_SMALL -> WidgetStyle()
            WidgetKind.ALL_WIDE -> WidgetStyle(opacity = 70, abbreviations = Tri.ON, showAmPm = false)
            WidgetKind.NEXT_STREAK -> WidgetStyle()
            WidgetKind.TRACKER -> WidgetStyle(abbreviations = Tri.ON, showAmPm = false)
            WidgetKind.LARGE -> WidgetStyle(theme = WidgetTheme.SOLID, opacity = 92, showSunrise = true)
            WidgetKind.MINIMAL -> WidgetStyle(theme = WidgetTheme.MINIMAL, opacity = 0, padV = 4, padH = 4, showIcons = false)
        }

        /** Tolerant decoder: unknown/missing keys fall back to the kind's defaults. */
        fun decode(s: String?, kind: WidgetKind): WidgetStyle {
            val d = defaults(kind)
            if (s.isNullOrBlank()) return d
            val m = s.split(';').mapNotNull { kv -> kv.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }.toMap()
            fun bool(k: String, def: Boolean) = m[k]?.let { it == "1" } ?: def
            fun int(k: String, def: Int) = m[k]?.toIntOrNull() ?: def
            fun float(k: String, def: Float) = m[k]?.toFloatOrNull() ?: def
            return WidgetStyle(
                theme = WidgetTheme.entries.firstOrNull { it.name == m["theme"] } ?: d.theme,
                accent = int("accent", d.accent),
                opacity = int("opacity", d.opacity),
                corner = int("corner", d.corner),
                // v1 stored a single "padding" for all sides.
                padV = int("padv", int("padding", d.padV)),
                padH = int("padh", int("padding", d.padH)),
                align = WidgetAlign.entries.firstOrNull { it.name == m["align"] } ?: d.align,
                spacing = int("spacing", d.spacing),
                textScale = float("text", d.textScale),
                iconScale = float("icon", d.iconScale),
                boxScale = float("box", d.boxScale),
                weight = WidgetWeight.entries.firstOrNull { it.name == m["weight"] } ?: d.weight,
                showHijri = bool("hijri", d.showHijri),
                showCountdown = bool("countdown", d.showCountdown),
                showIcons = bool("icons", d.showIcons),
                showAmPm = bool("ampm", d.showAmPm),
                showSunrise = bool("sunrise", d.showSunrise),
                showMidnight = bool("midnight", d.showMidnight),
                clock24 = Tri.entries.firstOrNull { it.name == m["clock"] } ?: d.clock24,
                abbreviations = Tri.entries.firstOrNull { it.name == m["abbr"] } ?: d.abbreviations,
            ).sanitized()
        }
    }
}

/** Resolved colours for a style. All ARGB ints. */
data class WidgetPalette(
    val bg: Int,
    val bgEnd: Int,
    val text: Int,
    val sub: Int,
    val accent: Int,
    val highlight: Int,
    val gold: Int,
    val badge: Int,
    val danger: Int,
) {
    companion object {
        fun withAlpha(c: Int, alpha: Float): Int = (Math.round(alpha.coerceIn(0f, 1f) * 255) shl 24) or (c and 0xFFFFFF)

        /** Background alpha byte for an opacity percentage: 0 -> 0 (fully transparent), 100 -> 255, linear between. */
        fun alphaFor(opacityPct: Int): Int = Math.round(opacityPct.coerceIn(0, 100) * 255 / 100f)

        fun mix(a: Int, b: Int, t: Float): Int {
            fun ch(c: Int, s: Int) = (c shr s) and 0xFF
            fun lerp(s: Int) = (ch(a, s) + (ch(b, s) - ch(a, s)) * t).toInt().coerceIn(0, 255)
            return (lerp(24) shl 24) or (lerp(16) shl 16) or (lerp(8) shl 8) or lerp(0)
        }

        fun of(style: WidgetStyle): WidgetPalette {
            val a = alphaFor(style.opacity) / 255f
            val noBg = style.opacity <= 0
            val light = style.theme == WidgetTheme.LIGHT
            val text = if (light) 0xFF15171A.toInt() else 0xFFFFFFFF.toInt()
            val accent = if (light && style.accent == AccentPalette.last().argb) 0xFF2E7D32.toInt() else style.accent
            val accentReadable = if (light) mix(accent, 0xFF000000.toInt(), 0.25f) else accent
            val (bg, bgEnd) = when (style.theme) {
                WidgetTheme.GLASS -> 0xFF2C2C2E.toInt() to 0xFF2C2C2E.toInt()
                WidgetTheme.SOLID -> 0xFF141816.toInt() to 0xFF141816.toInt()
                WidgetTheme.MINIMAL -> 0xFF000000.toInt() to 0xFF000000.toInt()
                WidgetTheme.GRADIENT -> mix(accent, 0xFF000000.toInt(), 0.45f) to 0xFF0C1110.toInt()
                WidgetTheme.LIGHT -> 0xFFF6F6F3.toInt() to 0xFFF6F6F3.toInt()
            }
            return WidgetPalette(
                bg = withAlpha(bg, a),
                bgEnd = withAlpha(bgEnd, a),
                text = text,
                sub = withAlpha(text, 0.62f),
                accent = accentReadable,
                // At opacity 0 every background layer (panel, next-prayer pill, hijri badge) is removed.
                highlight = if (noBg) 0 else withAlpha(accent, if (light) 0.18f else 0.24f),
                gold = if (light) 0xFFB26B00.toInt() else 0xFFE6A23C.toInt(),
                badge = if (noBg) 0 else withAlpha(text, if (light) 0.08f else 0.14f),
                danger = 0xFFE5534B.toInt(),
            )
        }
    }
}

/** Resolved dimensions (dp) used by the widget layouts; pure so it can be unit tested. */
data class WidgetDims(val padTop: Int, val padBottom: Int, val padStart: Int, val padEnd: Int, val gap: Int, val cellPad: Int, val hasBackground: Boolean) {
    companion object {
        fun of(style: WidgetStyle): WidgetDims {
            val s = style.sanitized()
            return WidgetDims(s.padV, s.padV, s.padH, s.padH, s.spacing, s.spacing / 2, s.opacity > 0)
        }
    }
}
