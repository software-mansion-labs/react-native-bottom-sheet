package com.swmansion.reactnativebottomsheet.accessibility

import android.content.Context
import android.view.View
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

@RunWith(AndroidJUnit4::class)
@Config(sdk = [35])
class PortalAccessibilityMaskLedgerTest {
  @Test
  fun `releasing one background restores it while the other remains masked`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val backgroundA =
      View(context).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
      }
    val backgroundB = View(context)
    val ledger = PortalAccessibilityMaskLedger()
    ledger.reconcile(listOf(backgroundA, backgroundB))

    ledger.reconcile(listOf(backgroundB))

    assertEquals(
      listOf(
        View.IMPORTANT_FOR_ACCESSIBILITY_YES,
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
      ),
      listOf(backgroundA, backgroundB).map(View::getImportantForAccessibility),
    )
  }

  @Test
  fun `complete desired union masks both backgrounds and leaves outside control unchanged`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val backgroundA = View(context)
    val backgroundB = View(context)
    val outside = View(context)

    PortalAccessibilityMaskLedger().reconcile(listOf(backgroundA, backgroundB))

    assertEquals(
      listOf(
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
      ),
      listOf(backgroundA, backgroundB, outside).map(View::getImportantForAccessibility),
    )
  }

  @Test
  fun `final release restores every exact accessibility importance baseline`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val baselines =
      listOf(
        View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
        View.IMPORTANT_FOR_ACCESSIBILITY_YES,
        View.IMPORTANT_FOR_ACCESSIBILITY_NO,
        View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
      )
    val background = baselines.map { baseline ->
      View(context).apply { importantForAccessibility = baseline }
    }
    val ledger = PortalAccessibilityMaskLedger()

    ledger.reconcile(background)
    ledger.reconcile(emptyList())

    assertEquals(baselines, background.map(View::getImportantForAccessibility))
  }

  @Test
  fun `distinguishable application write is covered again and becomes the restore candidate`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val background =
      View(context).apply {
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
      }
    val ledger = PortalAccessibilityMaskLedger()
    ledger.reconcile(listOf(background))

    background.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
    ledger.reconcile(listOf(background))

    assertEquals(
      View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS,
      background.importantForAccessibility,
    )
    ledger.reconcile(emptyList())
    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_NO, background.importantForAccessibility)
  }

  @Test
  fun `release preserves an application write made after the coordinator mask`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val background = View(context)
    val ledger = PortalAccessibilityMaskLedger()
    ledger.reconcile(listOf(background))

    background.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    ledger.reconcile(emptyList())

    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_YES, background.importantForAccessibility)
  }

  @Test
  fun `same-value application write remains indistinguishable and restores the last candidate`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val background = View(context)
    val ledger = PortalAccessibilityMaskLedger()
    ledger.reconcile(listOf(background))

    background.importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
    ledger.reconcile(emptyList())

    assertEquals(View.IMPORTANT_FOR_ACCESSIBILITY_AUTO, background.importantForAccessibility)
  }

  @Test
  fun `new masks cover background before previous masks are restored`() {
    val context = ApplicationProvider.getApplicationContext<Context>()
    val sharedBackground = View(context)
    val nextBackground = View(context)
    var maskedAtRestore = false
    val previousBackground =
      RestoreObservingView(context) {
        maskedAtRestore =
          nextBackground.importantForAccessibility ==
            View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS &&
            sharedBackground.importantForAccessibility ==
              View.IMPORTANT_FOR_ACCESSIBILITY_NO_HIDE_DESCENDANTS
      }
    val ledger = PortalAccessibilityMaskLedger()
    ledger.reconcile(listOf(sharedBackground, previousBackground))

    ledger.reconcile(listOf(sharedBackground, nextBackground))

    assertEquals(true, maskedAtRestore)
    assertEquals(
      View.IMPORTANT_FOR_ACCESSIBILITY_AUTO,
      previousBackground.importantForAccessibility,
    )
  }

  private class RestoreObservingView(
    context: Context,
    private val onRestore: () -> Unit,
  ) : View(context) {
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
