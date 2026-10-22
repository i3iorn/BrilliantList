package dev.schrammel.brilliantlist.feature_entry

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The drag-to-reorder math is the fiddliest part of the entry list, so it is
 * kept in a pure function and unit tested on the JVM.
 */
class ReorderTest {

    @Test
    fun noDragKeepsThePosition() {
        assertEquals(2, dragTargetIndex(from = 2, dragOffsetPx = 0f, itemSizePx = 100, itemCount = 5))
    }

    @Test
    fun dragMovesByWholeItems() {
        assertEquals(3, dragTargetIndex(from = 2, dragOffsetPx = 120f, itemSizePx = 100, itemCount = 5))
        assertEquals(1, dragTargetIndex(from = 2, dragOffsetPx = -140f, itemSizePx = 100, itemCount = 5))
    }

    @Test
    fun targetIsClampedToTheListBounds() {
        assertEquals(4, dragTargetIndex(from = 2, dragOffsetPx = 9_999f, itemSizePx = 100, itemCount = 5))
        assertEquals(0, dragTargetIndex(from = 2, dragOffsetPx = -9_999f, itemSizePx = 100, itemCount = 5))
    }

    @Test
    fun zeroSizedItemsDoNotDivideByZero() {
        // A zero sized item is treated as 1px, so the drag is clamped to the
        // list bounds instead of dividing by zero.
        assertEquals(2, dragTargetIndex(from = 0, dragOffsetPx = 50f, itemSizePx = 0, itemCount = 3))
    }

    @Test
    fun emptyListIsHandled() {
        assertEquals(0, dragTargetIndex(from = 0, dragOffsetPx = 100f, itemSizePx = 100, itemCount = 0))
    }

    @Test
    fun sampleSizeDownscalesLargeImages() {
        assertEquals(1, sampleSize(sourceWidth = 100, targetWidthPx = 100))
        assertEquals(2, sampleSize(sourceWidth = 200, targetWidthPx = 100))
        assertEquals(4, sampleSize(sourceWidth = 400, targetWidthPx = 100))
    }

    @Test
    fun sampleSizeNeverUpscales() {
        assertEquals(1, sampleSize(sourceWidth = 50, targetWidthPx = 400))
    }
}
