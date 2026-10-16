package com.swmansion.reactnativebottomsheet.accessibility

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.facebook.jni.HybridData
import com.facebook.react.soloader.OpenSourceMergedSoMapping
import com.facebook.react.uimanager.StateWrapper
import com.facebook.soloader.SoLoader
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.BeforeClass
import org.junit.Test
import org.junit.runner.RunWith

/** No Activity or React app is launched: this tests the actual Kotlin/JNI boundary. */
@RunWith(AndroidJUnit4::class)
class FabricPortalPublicationJniTest {
  @Test
  fun bootstrappedDeliveredStateCrossesJniAndReleasedResourceStopsServingReceipts() = onMain {
    FabricPublicationJniFixture.create().use { fixture ->
      val lease = fixture.lease()
      try {
        assertNull(fixture.takeDeliveredPublicationState())
        lease.bootstrap()
        val state = requireNotNull(fixture.takeDeliveredPublicationState())
        try {
          val receipt = requireNotNull(lease.receipt(1, 14, mapOf(14 to state)))
          assertTrue(receipt.publicationId.isNotEmpty())
          assertEquals(13, receipt.hostTag)
          assertEquals(listOf(11), receipt.contexts)
          assertEquals(listOf(14, 13, 31, 11, 1), receipt.ancestors)
          assertEquals(listOf(1, 11, 13, 14, 31), receipt.logicalTags)
          assertEquals(mapOf(1 to 0, 13 to 1, 14 to 13), receipt.nativeParents)
          // Synthetic source membership tests transport, not native attachment acceptance.
          val projection =
            requireNotNull(
              lease.isolation(1, mapOf(14 to state), receipt.nativeParents, mapOf(13 to listOf(14)))
            )
          assertEquals(emptySet<Int>(), projection.hiddenBranches)
          assertEquals(
            mapOf(11 to setOf(31), 31 to setOf(13), 13 to setOf(14)),
            projection.retainedChildren,
          )
          val released =
            requireNotNull(
              lease.isolation(1, mapOf(14 to state), receipt.nativeParents, emptyMap())
            )
          assertEquals(emptyMap<Int, Set<Int>>(), released.retainedChildren)
          assertNull(
            lease.isolation(1, mapOf(14 to state), receipt.nativeParents, mapOf(13 to emptyList()))
          )
          assertNull(lease.receipt(2, 14, mapOf(14 to state)))
          assertNull(lease.receipt(1, 99, mapOf(14 to state)))
          state.destroyState()
          assertNull(lease.receipt(1, 14, mapOf(14 to state)))
          assertNull(
            lease.isolation(1, mapOf(14 to state), receipt.nativeParents, mapOf(13 to listOf(14)))
          )
          lease.release()
          lease.release()
          lease.bootstrap()
          fixture.makeWrapperContainingBlock()
          assertNull(fixture.takeDeliveredPublicationState())
          assertNull(lease.receipt(1, 14, mapOf(14 to state)))
          assertNull(lease.isolation(1, emptyMap(), emptyMap(), emptyMap()))
        } finally {
          state.destroyState()
        }
      } finally {
        lease.release()
      }
    }
  }

  @Test
  fun delayedWrapperKeepsItsDeliveredContextAfterNewerFabricState() = onMain {
    FabricPublicationJniFixture.create().use { fixture ->
      val lease = fixture.lease()
      var delayed: StateWrapper? = null
      var current: StateWrapper? = null
      try {
        lease.bootstrap()
        delayed = requireNotNull(fixture.takeDeliveredPublicationState())
        fixture.makeWrapperContainingBlock()
        current = requireNotNull(fixture.takeDeliveredPublicationState())
        assertTrue(
          "The delayed wrapper must have a newer State in the same family",
          fixture.hasNewerStateInSameFamily(delayed, current),
        )
        val oldReceipt = requireNotNull(lease.receipt(1, 14, mapOf(14 to delayed)))
        val newReceipt = requireNotNull(lease.receipt(1, 14, mapOf(14 to current)))
        assertEquals(listOf(11), oldReceipt.contexts)
        assertEquals(listOf(31), newReceipt.contexts)
        assertNotEquals(oldReceipt.publicationId, newReceipt.publicationId)
        val oldProjection =
          requireNotNull(
            lease.isolation(
              1,
              mapOf(14 to delayed),
              oldReceipt.nativeParents,
              mapOf(13 to listOf(14)),
            )
          )
        val newProjection =
          requireNotNull(
            lease.isolation(
              1,
              mapOf(14 to current),
              newReceipt.nativeParents,
              mapOf(13 to listOf(14)),
            )
          )
        assertEquals(
          mapOf(11 to setOf(31), 31 to setOf(13), 13 to setOf(14)),
          oldProjection.retainedChildren,
        )
        assertEquals(mapOf(31 to setOf(13), 13 to setOf(14)), newProjection.retainedChildren)
        lease.release()
        assertNull(lease.receipt(1, 14, mapOf(14 to current)))
      } finally {
        delayed?.destroyState()
        current?.destroyState()
        lease.release()
      }
    }
  }

  private fun onMain(block: () -> Unit) {
    var failure: Throwable? = null
    InstrumentationRegistry.getInstrumentation().runOnMainSync {
      try {
        block()
      } catch (caught: Throwable) {
        failure = caught
      }
    }
    failure?.let { throw it }
  }

  @Test
  fun sourceAgreementRejectsAnIncompleteDeliveredSheetInventory() = onMain {
    FabricPublicationJniFixture.create().use { fixture ->
      val lease = fixture.lease()
      var states = emptyArray<StateWrapper>()
      try {
        lease.bootstrap()
        states = fixture.addSecondSheetAndTakeDeliveredStates()
        assertEquals(2, states.size)
        val complete = mapOf(14 to states[0], 15 to states[1])
        assertNull(lease.receipt(1, 14, mapOf(14 to states[0])))
        assertNull(lease.receipt(1, 14, mapOf(14 to states[0], 15 to null)))
        val first = requireNotNull(lease.receipt(1, 14, complete))
        val second = requireNotNull(lease.receipt(1, 15, complete))
        assertEquals(first.publicationId, second.publicationId)
        assertEquals(13, first.hostTag)
        assertEquals(listOf(11), first.contexts)
        assertEquals(13, second.hostTag)
        assertEquals(listOf(11), second.contexts)
        assertNull(
          lease.isolation(1, mapOf(14 to states[0]), first.nativeParents, mapOf(13 to listOf(14)))
        )
        val both =
          requireNotNull(
            lease.isolation(1, complete, first.nativeParents, mapOf(13 to listOf(14, 15)))
          )
        assertEquals(setOf(14, 15), both.retainedChildren[13])
        val one =
          requireNotNull(lease.isolation(1, complete, first.nativeParents, mapOf(13 to listOf(14))))
        assertEquals(setOf(15), one.hiddenBranches)
      } finally {
        states.forEach { it.destroyState() }
        lease.release()
      }
    }
  }

  companion object {
    @BeforeClass
    @JvmStatic
    fun loadNativeBoundary() {
      assumeTrue(
        InstrumentationRegistry.getArguments().getString("bottomSheetPublicationJni") == "true"
      )
      SoLoader.init(InstrumentationRegistry.getInstrumentation().context, OpenSourceMergedSoMapping)
      SoLoader.loadLibrary("fabricjni")
      System.loadLibrary("bottomsheet_publication_jni_test")
    }
  }
}

internal class FabricPublicationJniFixture
private constructor(private val mHybridData: HybridData) : AutoCloseable {
  external fun lease(): FabricPortalPublication

  external fun takeDeliveredPublicationState(): StateWrapper?

  external fun makeWrapperContainingBlock()

  external fun hasNewerStateInSameFamily(delivered: StateWrapper, current: StateWrapper): Boolean

  external fun addSecondSheetAndTakeDeliveredStates(): Array<StateWrapper>

  override fun close() = mHybridData.resetNative()

  companion object {
    @JvmStatic external fun create(): FabricPublicationJniFixture
  }
}
