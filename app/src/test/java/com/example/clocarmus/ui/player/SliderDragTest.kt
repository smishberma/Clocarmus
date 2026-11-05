package com.example.clocarmus.ui.player

import org.junit.Assert.*
import org.junit.Test

class SliderDragTest {
    private fun drag(value: Float = .5f, x: Float = 110f, y: Float = 24f) =
        SliderDrag(value, x, y, 200f, 48f, 48f, 8f)

    @Test fun tapHoldAndJitterNeverProduceACommit() {
        val gesture = drag()
        repeat(100) { assertNull(gesture.move(110f, 24f)) }
        assertNull(gesture.move(118f, 26f))
        assertNull(gesture.move(105f, 20f))
        assertNull(gesture.finish())
    }

    @Test fun offCenterGrabPreservesOffsetAndOriginalValue() {
        val gesture = drag()
        assertEquals(.6f, gesture.move(130f, 24f)!!, .0001f)
        assertEquals(.7f, gesture.move(150f, 24f)!!, .0001f)
        assertEquals(.7f, gesture.finish()!!, .0001f)
        assertNull(gesture.finish())
    }

    @Test fun outsideStartCannotActivateByCrossingEndpoint() {
        val gesture = drag(x = 20f)
        assertNull(gesture.move(100f, 24f))
        assertNull(gesture.move(150f, 24f))
        assertNull(gesture.finish())
    }

    @Test fun verticalStartCannotTurnIntoScrubbing() {
        val gesture = drag()
        assertNull(gesture.move(112f, 70f))
        assertNull(gesture.move(180f, 70f))
        assertNull(gesture.finish())
    }

    @Test fun horizontalSlopIsRequiredEvenOnDiagonalMovement() {
        val gesture = drag()
        assertNull(gesture.move(117f, 31f))
        assertEquals(.55f, gesture.move(120f, 31f)!!, .0001f)
    }

    @Test fun endpointsRemainGrabbableAndClampOutsideBounds() {
        val minimum = drag(value = 0f, x = 0f)
        assertEquals(0f, minimum.move(-80f, 24f)!!, 0f)
        assertEquals(1f, minimum.move(280f, 24f)!!, 0f)
        val maximum = drag(value = 1f, x = 200f)
        assertEquals(.9f, maximum.move(180f, 24f)!!, .0001f)
        assertEquals(1f, maximum.move(300f, 24f)!!, 0f)
    }

    @Test fun cancellationDiscardsPreviewAndPreventsFurtherMovement() {
        val gesture = drag()
        assertNotNull(gesture.move(140f, 24f))
        gesture.cancel()
        assertNull(gesture.finish())
        assertNull(gesture.move(180f, 24f))
    }

    @Test fun targetHonorsHeightAndVisibleThumbSize() {
        assertNull(drag(y = -1f).move(150f, 24f))
        val largeThumb = SliderDrag(.5f, 130f, 50f, 200f, 80f, 66f, 8f)
        assertNotNull(largeThumb.move(150f, 50f))
        val zeroWidth = SliderDrag(.5f, 0f, 24f, 0f, 48f, 48f, 8f)
        assertNull(zeroWidth.move(40f, 24f))
    }
}
