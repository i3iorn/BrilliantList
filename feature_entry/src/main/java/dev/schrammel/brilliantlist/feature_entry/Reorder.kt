package dev.schrammel.brilliantlist.feature_entry

import kotlin.math.roundToInt

/**
 * Target index of a dragged item.
 *
 * The drag offset is converted into whole item heights and the result is
 * clamped to the list bounds. Pure function so it can be unit tested on the
 * JVM (see ReorderTest).
 */
fun dragTargetIndex(
    from: Int,
    dragOffsetPx: Float,
    itemSizePx: Int,
    itemCount: Int
): Int {
    if (itemCount <= 0) return 0
    val size = itemSizePx.coerceAtLeast(1)
    val steps = (dragOffsetPx / size).roundToInt()
    return (from + steps).coerceIn(0, itemCount - 1)
}
