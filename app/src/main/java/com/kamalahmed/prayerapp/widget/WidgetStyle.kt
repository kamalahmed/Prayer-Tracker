package com.kamalahmed.prayerapp.widget

import java.util.Locale

enum class WidgetKind(val label: String, val description: String) {
    NEXT_SMALL("Next prayer", "2×2 · next prayer, time and live countdown"),
    ALL_WIDE("All prayers", "4×1 · all five prayers in a row with the hijri day"),
    NEXT_STREAK("Next + streak", "3×2 · next prayer, today's progress and streak"),
    TRACKER("Streak tracker", "4×1 · tap a prayer to mark it prayed"),
    LARGE("Dashboard", "4×3 · ring, all prayers, hijri date and streak"),
    MINIMAL("Minimal text", "Text only · next prayer on one line"),
    TASBIH("Tasbih counter", "2×2 · tap to count a selected daily worship"),
    IQAMAH_TABLE("Adhan + Iqamah", "4×3 · all prayers with both time columns"),
}

enum class WidgetTimeMode(val label: String) { START("Start time"), IQAMAH("Iqamah time"), BOTH("Both") }

enum class WidgetTheme(val label: String) { GLASS("Glass"), SOLID("Solid"), MINIMAL("Minimal"), GRADIENT("Gradient"), LIGHT("Light") }

/** Where the background panel sits inside a taller/wider widget cell. */
enum class WidgetAlign(val label: String) { TOP("Top"), CENTER("Center"), BOTTOM("Bottom") }

enum class TickTimes(val label: String) { OFF("Off"), ABOVE("Above tick"), BELOW("Below tick") }

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
    /** "Always show all prayers": never switch to the compact layout, whatever size the launcher reports. */
    val alwaysFull: Boolean = false,
    val timeMode: WidgetTimeMode = WidgetTimeMode.START,
    val counterId: String = "istighfar",
    val contentScale: Float = 1f,
    val fillPanel: Boolean = true,
    val outerMargin: Int = 0,
    val tickTimes: TickTimes = TickTimes.OFF,
) {
    fun use24h(app: Boolean) = when (clock24) { Tri.APP -> app; Tri.ON -> true; Tri.OFF -> false }
    fun abbr(app: Boolean) = when (abbreviations) { Tri.APP -> app; Tri.ON -> true; Tri.OFF -> false }

    fun encode(): String = listOf(
        "v" to "3", "theme" to theme.name, "accent" to accent.toString(), "opacity" to opacity.toString(),
        "corner" to corner.toString(), "padv" to padV.toString(), "padh" to padH.toString(), "spacing" to spacing.toString(), "align" to align.name,
        "text" to fmt(textScale), "icon" to fmt(iconScale), "box" to fmt(boxScale), "weight" to weight.name,
        "hijri" to b(showHijri), "countdown" to b(showCountdown), "icons" to b(showIcons), "ampm" to b(showAmPm),
        "sunrise" to b(showSunrise), "midnight" to b(showMidnight), "clock" to clock24.name, "abbr" to abbreviations.name,
        "scale" to fmt(contentScale), "fill" to b(fillPanel), "margin" to outerMargin.toString(), "ticks" to tickTimes.name,
        "full" to b(alwaysFull), "times" to timeMode.name, "counter" to counterId,
    ).joinToString(";") { "${it.first}=${it.second}" }

    /** Values clamped into their valid ranges. */
    fun sanitized() = copy(
        contentScale = finite(contentScale, 0.6f, 1.5f), outerMargin = outerMargin.coerceIn(0, 24),
        counterId = counterId.take(80).filter { it.isLetterOrDigit() || it == '_' || it == '-' },
        opacity = opacity.coerceIn(0, 100), corner = corner.coerceIn(0, 40), padV = padV.coerceIn(0, 32), padH = padH.coerceIn(0, 32),
        spacing = spacing.coerceIn(0, 24), textScale = finite(textScale, 0.6f, 1.8f),
        iconScale = finite(iconScale, 0.5f, 1.8f), boxScale = finite(boxScale, 0.6f, 1.5f),
    )

    companion object {
        private fun finite(f: Float, min: Float, max: Float) = if (f.isFinite()) f.coerceIn(min, max) else 1f
        private fun b(v: Boolean) = if (v) "1" else "0"
        private fun fmt(f: Float) = String.format(Locale.US, "%.2f", f)

        fun defaults(kind: WidgetKind): WidgetStyle = when (kind) {
            WidgetKind.NEXT_SMALL -> WidgetStyle()
            WidgetKind.ALL_WIDE -> WidgetStyle(opacity = 70, abbreviations = Tri.ON, showAmPm = false)
            WidgetKind.NEXT_STREAK -> WidgetStyle()
            WidgetKind.TRACKER -> WidgetStyle(abbreviations = Tri.ON, showAmPm = false)
            WidgetKind.LARGE -> WidgetStyle(theme = WidgetTheme.SOLID, opacity = 92, showSunrise = true)
            WidgetKind.TASBIH -> WidgetStyle(theme = WidgetTheme.SOLID)
            WidgetKind.IQAMAH_TABLE -> WidgetStyle(theme = WidgetTheme.SOLID, timeMode = WidgetTimeMode.BOTH)
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
                alwaysFull = bool("full", d.alwaysFull),
                timeMode = WidgetTimeMode.entries.firstOrNull { it.name == m["times"] } ?: d.timeMode,
                counterId = m["counter"] ?: d.counterId,
                contentScale = float("scale", d.contentScale),
                fillPanel = bool("fill", d.fillPanel), outerMargin = int("margin", d.outerMargin),
                tickTimes = TickTimes.entries.firstOrNull { it.name == m["ticks"] } ?: d.tickTimes,
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

/**
 * Layout decisions from the real per-instance widget size (dp). Pure so it can be unit tested.
 * Only the *detail* level changes with size (icons / times under the dots); the set of prayers shown never
 * shrinks, so a widget can't "collapse" to one prayer because a launcher reported a small size.
 */
object WidgetLayout {
    fun wideCounter(widthDp: Float, heightDp: Float) = widthDp >= heightDp * 1.7f || heightDp < 112f

    /** Fit typography, icons and hit areas to the actual launcher allocation, not a fixed panel. */
    fun fitScale(kind: WidgetKind, widthDp: Float, heightDp: Float, style: WidgetStyle): Float {
        if (!widthDp.isFinite() || !heightDp.isFinite() || widthDp <= 0 || heightDp <= 0) return 1f
        val both = style.timeMode == WidgetTimeMode.BOTH
        val ticks = style.tickTimes != TickTimes.OFF
        val (w, h) = when (kind) {
            WidgetKind.NEXT_SMALL -> 125f to (if (both) 130f else 110f)
            WidgetKind.ALL_WIDE -> 280f to (if (heightDp < WIDE_COMPACT_H && !style.alwaysFull) { if (both) 60f else 36f } else if (both) 124f else 90f)
            WidgetKind.NEXT_STREAK -> 245f to (if (ticks) 150f else 125f)
            WidgetKind.TRACKER -> 275f to (if (ticks) { if (both) 110f else 95f } else 72f)
            WidgetKind.LARGE -> 280f to (if (both) 400f else 310f)
            WidgetKind.IQAMAH_TABLE -> 280f to 235f
            WidgetKind.MINIMAL -> 260f to (if (both) 65f else 42f)
            WidgetKind.TASBIH -> if (wideCounter(widthDp, heightDp)) 245f to 50f else 135f to 150f
        }
        val s = style.sanitized()
        val availW = (widthDp - 2 * (s.outerMargin + s.padH)).coerceAtLeast(1f)
        val availH = (heightDp - 2 * (s.outerMargin + s.padV)).coerceAtLeast(1f)
        return minOf(availW / w, availH / h).coerceIn(0.35f, 1.6f)
    }

    /** Heights below these (dp) drop secondary details. */
    const val WIDE_COMPACT_H = 100
    const val STREAK_COMPACT_H = 130
    const val TRACKER_COMPACT_H = 100

    fun compact(kind: WidgetKind, widthDp: Float, heightDp: Float, style: WidgetStyle): Boolean {
        if (style.alwaysFull) return false
        // Size 0x0 means the launcher has not reported options yet: use the full layout.
        if (widthDp <= 0f || heightDp <= 0f) return false
        return when (kind) {
            WidgetKind.ALL_WIDE -> heightDp < WIDE_COMPACT_H
            WidgetKind.NEXT_STREAK -> heightDp < STREAK_COMPACT_H
            WidgetKind.TRACKER -> heightDp < TRACKER_COMPACT_H
            else -> false
        }
    }

    /** Number of prayer cells a list layout shows: always every enabled row (stable across sizes). */
    fun cellCount(kind: WidgetKind, enabledRows: Int): Int = when (kind) {
        WidgetKind.ALL_WIDE -> enabledRows.coerceAtMost(8)
        WidgetKind.NEXT_STREAK, WidgetKind.TRACKER -> 5
        WidgetKind.LARGE, WidgetKind.IQAMAH_TABLE -> enabledRows
        WidgetKind.NEXT_SMALL, WidgetKind.MINIMAL, WidgetKind.TASBIH -> 0
    }
}
