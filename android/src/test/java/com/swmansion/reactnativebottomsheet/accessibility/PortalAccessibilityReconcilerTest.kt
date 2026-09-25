package com.swmansion.reactnativebottomsheet.accessibility

import android.app.Activity
import android.content.Context
import android.view.View
import android.widget.FrameLayout
import androidx.test.ext.junit.runners.AndroidJUnit4
import java.lang.ref.WeakReference
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Robolectric
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PortalAccessibilityReconcilerTest {
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

      PortalAccessibilityReconciler(root).reconcile(listOf(owner))

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
  fun `final release restores every exact accessibility importance baseline`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = FrameLayout(activity.get())
      val baselines =
        listOf(
          View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
          View.IMPORTANT_FOR_ACCESSIBILITY_YES,
          View.IMPORTANT_FOR_ACCESSIBILITY_NO,
          View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        )
      val background = baselines.map { baseline ->
        View(activity.get()).apply {
          importantForAccessibility = baseline
          root.addView(this)
        }
      }
      val owner = View(activity.get()).also(root::addView)
      activity.get().setContentView(root)
      val reconciler = PortalAccessibilityReconciler(root)

      reconciler.reconcile(listOf(owner))
      reconciler.reconcile(emptyList())

      assertEquals(baselines, background.map(View::getImportantForAccessibility))
    } finally {
      activity.close()
    }
  }

  @Test
  fun `distinguishable application write is covered again and becomes the restore candidate`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = FrameLayout(activity.get())
      val background =
        View(activity.get()).apply {
          importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
          root.addView(this)
        }
      val owner = View(activity.get()).also(root::addView)
      activity.get().setContentView(root)
      val reconciler = PortalAccessibilityReconciler(root)
      reconciler.reconcile(listOf(owner))

      background.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
      reconciler.reconcile(listOf(owner))

      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        background.importantForAccessibility,
      )
      reconciler.reconcile(emptyList())
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, background.importantForAccessibility)
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

      PortalAccessibilityReconciler(root).reconcile(listOf(firstOwner, secondOwner))

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
  fun `invalid retained path releases this root without mutating the other root`() {
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
      reconciler.reconcile(listOf(owner))

      reconciler.reconcile(listOf(otherOwner))

      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, background.importantForAccessibility)
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, otherOwner.importantForAccessibility)
    } finally {
      activity.close()
    }
  }

  @Test
  fun `release preserves an application write made after the coordinator mask`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = FrameLayout(activity.get())
      val background = View(activity.get()).also(root::addView)
      val owner = View(activity.get()).also(root::addView)
      activity.get().setContentView(root)
      val reconciler = PortalAccessibilityReconciler(root)
      reconciler.reconcile(listOf(owner))

      background.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
      reconciler.reconcile(emptyList())

      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, background.importantForAccessibility)
    } finally {
      activity.close()
    }
  }

  @Test
  fun `same-value application write remains indistinguishable and restores the last candidate`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = FrameLayout(activity.get())
      val background = View(activity.get()).also(root::addView)
      val owner = View(activity.get()).also(root::addView)
      activity.get().setContentView(root)
      val reconciler = PortalAccessibilityReconciler(root)
      reconciler.reconcile(listOf(owner))

      background.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      reconciler.reconcile(emptyList())

      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, background.importantForAccessibility)
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
      reconciler.reconcile(listOf(owner))
      val added = CountingImportanceView(activity.get()).also(root::addView)

      reconciler.reconcile(listOf(owner))
      reconciler.reconcile(listOf(owner))

      assertEquals(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        added.importantForAccessibility,
      )
      assertEquals(1, added.writeCount)
      root.removeView(added)
      reconciler.reconcile(listOf(owner))
      assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, added.importantForAccessibility)
    } finally {
      activity.close()
    }
  }

  @Test
  fun `owner transfer masks the new side branch before restoring the old one`() {
    val activity = Robolectric.buildActivity(Activity::class.java).setup()
    try {
      val root = FrameLayout(activity.get())
      val background = View(activity.get()).also(root::addView)
      val firstBranch = FrameLayout(activity.get()).also(root::addView)
      val firstOwner = View(activity.get()).also(firstBranch::addView)
      var maskedAtRestore = false
      val secondBranch =
        RestoreObservingLayout(activity.get()) {
            maskedAtRestore =
              firstBranch.importantForAccessibility ==
                View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS &&
                background.importantForAccessibility ==
                  View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
          }
          .also(root::addView)
      val secondOwner = View(activity.get()).also(secondBranch::addView)
      activity.get().setContentView(root)
      val reconciler = PortalAccessibilityReconciler(root)
      reconciler.reconcile(listOf(firstOwner))

      reconciler.reconcile(listOf(secondOwner))

      assertEquals(true, maskedAtRestore)
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
    reconciler.reconcile(listOf(owner))
    root.removeView(branch)
    reconciler.reconcile(listOf(owner))
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

  private class RestoreObservingLayout(
    context: Context,
    private val onRestore: () -> Unit,
  ) : FrameLayout(context) {
    override fun setImportantForAccessibility(mode: Int) {
      if (
        importantForAccessibility == View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS &&
          mode != View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      ) {
        onRestore()
      }
      super.setImportantForAccessibility(mode)
    }
  }
}
