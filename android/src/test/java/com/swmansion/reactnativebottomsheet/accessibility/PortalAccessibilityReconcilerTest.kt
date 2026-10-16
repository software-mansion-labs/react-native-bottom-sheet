package com.swmansion.reactnativebottomsheet.accessibility

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.ref.WeakReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PortalAccessibilityReconcilerTest {
  @Test
  fun `invalid projected cuts preserve existing masks without applying a valid partial proposal`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val area = FrameLayout(activity.get())
      val background = View(activity.get()).also(area::addView)
      val owner = View(activity.get()).also(area::addView)
      val added = View(activity.get()).also(area::addView)
      activity.get().setContentView(area)
      val reconciler = PortalAccessibilityReconciler(area)
      assertTrue(
        reconciler.reconcileCuts(listOf(PortalAccessibilityCut(listOf(background), listOf(owner))))
      )
      val dialog = Dialog(activity.get())
      try {
        val otherArea = FrameLayout(activity.get())
        val otherBackground = View(activity.get()).also(otherArea::addView)
        val otherOwner = View(activity.get()).also(otherArea::addView)
        dialog.setContentView(otherArea)
        dialog.show()
        shadowOf(Looper.getMainLooper()).idle()
        val valid = PortalAccessibilityCut(listOf(added), listOf(owner))
        listOf(
            PortalAccessibilityCut(listOf(otherBackground), listOf(owner)),
            PortalAccessibilityCut(listOf(background), listOf(otherOwner)),
            PortalAccessibilityCut(listOf(background), emptyList()),
          )
          .forEach { invalid ->
            assertFalse(reconciler.reconcileCuts(listOf(valid, invalid)))
            assertEquals(
              View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
              background.importantForAccessibility,
            )
            listOf(added, owner, otherBackground, otherOwner).forEach {
              assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.importantForAccessibility)
            }
          }
      } finally {
        dialog.dismiss()
      }
      assertTrue(
        reconciler.reconcileCuts(listOf(PortalAccessibilityCut(emptyList(), listOf(owner))))
      )
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, background.importantForAccessibility)
    } finally {
      activity.close()
    }
  }

  @Test
  fun `current check can reject release or invalidate native facts before any mask write`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val area = FrameLayout(activity.get())
      val background = View(activity.get()).also(area::addView)
      val owner = View(activity.get()).also(area::addView)
      activity.get().setContentView(area)
      val reconciler = PortalAccessibilityReconciler(area)
      val cut = PortalAccessibilityCut(listOf(background), listOf(owner))
      assertTrue(reconciler.reconcileCuts(listOf(cut)))
      val added = View(activity.get()).also(area::addView)

      assertFalse(reconciler.reconcileCuts(emptyList()) { false })
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        background.importantForAccessibility,
      )
      assertFalse(
        reconciler.reconcileCuts(listOf(PortalAccessibilityCut(listOf(added), listOf(owner)))) {
          area.removeView(owner)
          true
        }
      )
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        background.importantForAccessibility,
      )
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, added.importantForAccessibility)
      assertTrue(reconciler.reconcileCuts(emptyList()))
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, background.importantForAccessibility)
    } finally {
      activity.close()
    }
  }

  @Test
  fun `projected cuts preserve another source owner inside a covered branch while keeping inner isolation`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val window = FrameLayout(activity.get())
      val toolbar = View(activity.get()).also(window::addView)
      val area = FrameLayout(activity.get()).also(window::addView)
      val background = View(activity.get()).also(area::addView)
      val covered = FrameLayout(activity.get()).also(area::addView)
      val chrome = View(activity.get()).also(covered::addView)
      val inner = FrameLayout(activity.get()).also(covered::addView)
      val innerBackground = View(activity.get()).also(inner::addView)
      val innerOwner = FrameLayout(activity.get()).also(inner::addView)
      val content = View(activity.get()).also(innerOwner::addView)
      val top = View(activity.get()).also(area::addView)
      activity.get().setContentView(window)
      val reconciler = PortalAccessibilityReconciler(window)

      val applied =
        reconciler.reconcileCuts(
          listOf(
            PortalAccessibilityCut(listOf(background, covered), listOf(top)),
            PortalAccessibilityCut(listOf(innerBackground), listOf(innerOwner)),
          )
        )

      listOf(background, chrome, innerBackground).forEach {
        assertEquals(
          View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
          it.importantForAccessibility,
        )
      }
      listOf(toolbar, covered, inner, innerOwner, content, top).forEach {
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.importantForAccessibility)
      }
      assertTrue(applied)
      assertTrue(reconciler.reconcileCuts(emptyList()))
      listOf(background, chrome, innerBackground).forEach {
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.importantForAccessibility)
      }
    } finally {
      activity.close()
    }
  }

  @Test
  @Config(sdk = [24, 35])
  fun `shared scope retains a whole Active ancestor without hiding its content for another retained path`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val area = FrameLayout(activity.get())
      val background = View(activity.get()).also(area::addView)
      val whole = FrameLayout(activity.get()).also(area::addView)
      val chrome = View(activity.get()).also(whole::addView)
      val nested = FrameLayout(activity.get()).also(whole::addView)
      val nestedBackground = View(activity.get()).also(nested::addView)
      val descendant = View(activity.get()).also(nested::addView)
      activity.get().setContentView(area)
      val reconciler = PortalAccessibilityReconciler(area)

      reconciler.reconcile(
        listOf(
          PortalAccessibilityScope(area, listOf(whole)),
          PortalAccessibilityScope(area, listOf(descendant)),
        )
      )

      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        background.importantForAccessibility,
      )
      listOf(whole, chrome, nested, nestedBackground, descendant).forEach {
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.importantForAccessibility)
      }

      reconciler.reconcile(
        listOf(
          PortalAccessibilityScope(area, listOf(whole)),
          PortalAccessibilityScope(nested, listOf(descendant)),
        )
      )
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        nestedBackground.importantForAccessibility,
      )
      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        background.importantForAccessibility,
      )
      listOf(whole, chrome, nested, descendant).forEach {
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.importantForAccessibility)
      }
    } finally {
      activity.close()
    }
  }

  @Test
  @Config(sdk = [24, 35])
  fun `inner isolation lifts only its path through covered outer content and releases without opening the outer background`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val window = FrameLayout(activity.get())
      val toolbar = View(activity.get()).also(window::addView)
      val area = FrameLayout(activity.get()).also(window::addView)
      val background = View(activity.get()).also(area::addView)
      val host = FrameLayout(activity.get()).also(area::addView)
      val covered = FrameLayout(activity.get()).also(host::addView)
      val chrome = View(activity.get()).also(covered::addView)
      val innerArea = FrameLayout(activity.get()).also(covered::addView)
      val innerBackground = View(activity.get()).also(innerArea::addView)
      val innerOwner = View(activity.get()).also(innerArea::addView)
      val top = View(activity.get()).also(host::addView)
      activity.get().setContentView(window)
      val reconciler = PortalAccessibilityReconciler(window)
      val outerScope = PortalAccessibilityScope(area, listOf(top))

      reconciler.reconcile(
        listOf(outerScope, PortalAccessibilityScope(innerArea, listOf(innerOwner)))
      )

      listOf(background, chrome, innerBackground).forEach {
        assertEquals(
          View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
          it.importantForAccessibility,
        )
      }
      listOf(toolbar, covered, innerArea, innerOwner, top).forEach {
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.importantForAccessibility)
      }

      reconciler.reconcile(listOf(outerScope))
      listOf(background, covered).forEach {
        assertEquals(
          View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
          it.importantForAccessibility,
        )
      }
      listOf(chrome, innerBackground, top, toolbar).forEach {
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.importantForAccessibility)
      }
      reconciler.reconcile(emptyList())
      listOf(background, covered, chrome, innerBackground, top, toolbar).forEach {
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.importantForAccessibility)
      }
    } finally {
      activity.close()
    }
  }

  @Test
  @Config(sdk = [24, 35])
  fun `disjoint supplied scopes isolate both backgrounds without hiding the outside toolbar`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val window = FrameLayout(activity.get())
      val toolbar = View(activity.get()).also(window::addView)
      val areaA = FrameLayout(activity.get()).also(window::addView)
      val backgroundA = View(activity.get()).also(areaA::addView)
      val ownerA = View(activity.get()).also(areaA::addView)
      val areaB = FrameLayout(activity.get()).also(window::addView)
      val backgroundB = View(activity.get()).also(areaB::addView)
      val ownerB = View(activity.get()).also(areaB::addView)
      activity.get().setContentView(window)

      PortalAccessibilityReconciler(window)
        .reconcile(
          listOf(
            PortalAccessibilityScope(areaA, listOf(ownerA)),
            PortalAccessibilityScope(areaB, listOf(ownerB)),
          )
        )

      listOf(backgroundA, backgroundB).forEach {
        assertEquals(
          View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
          it.importantForAccessibility,
        )
      }
      listOf(toolbar, areaA, ownerA, areaB, ownerB).forEach {
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.importantForAccessibility)
      }
    } finally {
      activity.close()
    }
  }

  @Test
  fun `one retained owner masks every side branch without masking the owner subtree`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = FrameLayout(activity.get())
      val backgroundBefore = View(activity.get())
      val retainedBranch = FrameLayout(activity.get())
      val nestedBackground = View(activity.get())
      val owner = FrameLayout(activity.get())
      val ownerContent = View(activity.get())
      val backgroundAfter = View(activity.get())
      owner.addView(ownerContent)
      retainedBranch.addView(nestedBackground)
      retainedBranch.addView(owner)
      root.addView(backgroundBefore)
      root.addView(retainedBranch)
      root.addView(backgroundAfter)
      activity.get().setContentView(root)

      PortalAccessibilityReconciler(root)
        .reconcile(listOf(PortalAccessibilityScope(root, listOf(owner))))

      listOf(backgroundBefore, nestedBackground, backgroundAfter).forEach {
        assertEquals(
          View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
          it.importantForAccessibility,
        )
      }
      listOf(root, retainedBranch, owner, ownerContent).forEach {
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.importantForAccessibility)
      }
    } finally {
      activity.close()
    }
  }

  @Test
  fun `multiple retained paths preserve both owners and mask their remaining side branches`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = FrameLayout(activity.get())
      val background = View(activity.get()).also(root::addView)
      val firstBranch = FrameLayout(activity.get()).also(root::addView)
      val firstSide = View(activity.get()).also(firstBranch::addView)
      val firstOwner = View(activity.get()).also(firstBranch::addView)
      val secondBranch = FrameLayout(activity.get()).also(root::addView)
      val secondOwner = View(activity.get()).also(secondBranch::addView)
      val secondSide = View(activity.get()).also(secondBranch::addView)
      activity.get().setContentView(root)

      PortalAccessibilityReconciler(root)
        .reconcile(listOf(PortalAccessibilityScope(root, listOf(firstOwner, secondOwner))))

      listOf(background, firstSide, secondSide).forEach {
        assertEquals(
          View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
          it.importantForAccessibility,
        )
      }
      listOf(firstBranch, firstOwner, secondBranch, secondOwner).forEach {
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.importantForAccessibility)
      }
    } finally {
      activity.close()
    }
  }

  @Test
  @Config(sdk = [24, 35])
  fun `invalid scoped proposals preserve existing masks without partially applying valid scopes or touching another window`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val window = FrameLayout(activity.get())
      val root = FrameLayout(activity.get())
      val background = View(activity.get()).also(root::addView)
      val owner = View(activity.get()).also(root::addView)
      val otherRoot = FrameLayout(activity.get())
      val otherOwner = View(activity.get()).also(otherRoot::addView)
      window.addView(root)
      window.addView(otherRoot)
      activity.get().setContentView(window)
      val reconciler = PortalAccessibilityReconciler(root)
      val scope = PortalAccessibilityScope(root, listOf(owner))
      reconciler.reconcile(listOf(scope))
      val added = View(activity.get()).also(root::addView)
      val dialog = Dialog(activity.get())
      try {
        val dialogArea = FrameLayout(activity.get())
        val dialogBackground = View(activity.get()).also(dialogArea::addView)
        val dialogOwner = View(activity.get()).also(dialogArea::addView)
        dialog.setContentView(dialogArea)
        dialog.show()
        shadowOf(Looper.getMainLooper()).idle()
        listOf(
            PortalAccessibilityScope(root, listOf(otherOwner)),
            PortalAccessibilityScope(root, emptyList()),
            PortalAccessibilityScope(dialogArea, listOf(dialogOwner)),
          )
          .forEach { invalid ->
            assertFalse(reconciler.reconcile(listOf(scope, invalid)))
            assertEquals(
              View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
              background.importantForAccessibility,
            )
            listOf(added, otherOwner, dialogBackground, dialogOwner).forEach {
              assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, it.importantForAccessibility)
            }
          }
        reconciler.reconcile(emptyList())
        assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, background.importantForAccessibility)
      } finally {
        dialog.dismiss()
      }
    } finally {
      activity.close()
    }
  }

  @Test
  fun `dynamic insertion removal and repeated reconciliation use the current hierarchy`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = FrameLayout(activity.get())
      val owner = View(activity.get()).also(root::addView)
      activity.get().setContentView(root)
      val reconciler = PortalAccessibilityReconciler(root)
      reconciler.reconcile(listOf(PortalAccessibilityScope(root, listOf(owner))))
      val added = CountingImportanceView(activity.get()).also(root::addView)

      reconciler.reconcile(listOf(PortalAccessibilityScope(root, listOf(owner))))
      reconciler.reconcile(listOf(PortalAccessibilityScope(root, listOf(owner))))

      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        added.importantForAccessibility,
      )
      assertEquals(1, added.writeCount)
      root.removeView(added)
      reconciler.reconcile(listOf(PortalAccessibilityScope(root, listOf(owner))))
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, added.importantForAccessibility)
    } finally {
      activity.close()
    }
  }

  @Test
  fun `removed masked branches are restored and not retained by the ledger`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = FrameLayout(activity.get())
      val owner = View(activity.get()).also(root::addView)
      activity.get().setContentView(root)
      val reconciler = PortalAccessibilityReconciler(root)
      val removed = maskAndRemoveBranch(activity.get(), root, owner, reconciler)

      repeat(20) {
        if (removed.get() != null) {
          System.gc()
          System.runFinalization()
        }
      }

      assertNull(removed.get())
    } finally {
      activity.close()
    }
  }

  private fun maskAndRemoveBranch(
    context: Context,
    root: FrameLayout,
    owner: View,
    reconciler: PortalAccessibilityReconciler,
  ): WeakReference<View> {
    val branch = View(context)
    root.addView(branch)
    reconciler.reconcile(listOf(PortalAccessibilityScope(root, listOf(owner))))
    root.removeView(branch)
    reconciler.reconcile(listOf(PortalAccessibilityScope(root, listOf(owner))))
    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, branch.importantForAccessibility)
    return WeakReference(branch)
  }

  private class CountingImportanceView(context: Context) : View(context) {
    var writeCount = 0

    override fun setImportantForAccessibility(mode: Int) {
      super.setImportantForAccessibility(mode)
      writeCount++
    }
  }
}
