package com.swmansion.reactnativebottomsheet.closerequest

import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveInputContextPolicyTest {
  private enum class Context {
    A,
    B,
  }

  @Test
  fun `accessibility focus in B selects B while keyboard focus stays in A`() {
    assertEquals(
      ActiveInputContext.Portal(Context.B),
      selectActiveInputContext(
        accessibilityFocus =
          CurrentInputFocus.Inside(listOf(InputContextCandidate(Context.B, true))),
        keyboardFocus = CurrentInputFocus.Inside(listOf(InputContextCandidate(Context.A, true))),
        hasForegroundWindowModalPresentation = false,
      ),
    )
  }

  @Test
  fun `keyboard focus selects A only when accessibility focus is absent`() {
    assertEquals(
      ActiveInputContext.Portal(Context.A),
      selectActiveInputContext(
        accessibilityFocus = CurrentInputFocus.Absent,
        keyboardFocus = CurrentInputFocus.Inside(listOf(InputContextCandidate(Context.A, true))),
        hasForegroundWindowModalPresentation = false,
      ),
    )
  }

  @Test
  fun `inactive inner context does not displace its Active enclosing context`() {
    assertEquals(
      ActiveInputContext.Portal(Context.A),
      selectActiveInputContext(
        accessibilityFocus =
          CurrentInputFocus.Inside(
            listOf(InputContextCandidate(Context.B, false), InputContextCandidate(Context.A, true))
          ),
        keyboardFocus = CurrentInputFocus.Absent,
        hasForegroundWindowModalPresentation = false,
      ),
    )
  }

  @Test
  fun `foreground window-modal presentation overrides known outside focus`() {
    assertEquals(
      ActiveInputContext.WindowModal,
      selectActiveInputContext(
        accessibilityFocus = CurrentInputFocus.KnownOutside,
        keyboardFocus = CurrentInputFocus.Inside(listOf(InputContextCandidate(Context.A, true))),
        hasForegroundWindowModalPresentation = true,
      ),
    )
  }

  @Test
  fun `known outside accessibility focus leaves input to host despite keyboard focus in A`() {
    assertEquals(
      ActiveInputContext.Host,
      selectActiveInputContext(
        accessibilityFocus = CurrentInputFocus.KnownOutside,
        keyboardFocus = CurrentInputFocus.Inside(listOf(InputContextCandidate(Context.A, true))),
        hasForegroundWindowModalPresentation = false,
      ),
    )
  }

  @Test
  fun `unmappable accessibility focus keeps window fallback instead of using keyboard A`() {
    assertEquals(
      ActiveInputContext.WindowFallback,
      selectActiveInputContext(
        accessibilityFocus = CurrentInputFocus.Unmappable,
        keyboardFocus = CurrentInputFocus.Inside(listOf(InputContextCandidate(Context.A, true))),
        hasForegroundWindowModalPresentation = false,
      ),
    )
  }

  @Test
  fun `confirmed absence of both focus types preserves the window input owner policy`() {
    assertEquals(
      ActiveInputContext.WindowFallback,
      selectActiveInputContext(
        accessibilityFocus = CurrentInputFocus.Absent,
        keyboardFocus = CurrentInputFocus.Absent,
        hasForegroundWindowModalPresentation = false,
      ),
    )
  }

  @Test
  fun `known outside keyboard focus leaves input to host when accessibility focus is absent`() {
    assertEquals(
      ActiveInputContext.Host,
      selectActiveInputContext(
        accessibilityFocus = CurrentInputFocus.Absent,
        keyboardFocus = CurrentInputFocus.KnownOutside,
        hasForegroundWindowModalPresentation = false,
      ),
    )
  }

  @Test
  fun `unmappable keyboard focus preserves window fallback when accessibility focus is absent`() {
    assertEquals(
      ActiveInputContext.WindowFallback,
      selectActiveInputContext(
        accessibilityFocus = CurrentInputFocus.Absent,
        keyboardFocus = CurrentInputFocus.Unmappable,
        hasForegroundWindowModalPresentation = false,
      ),
    )
  }

  @Test
  fun `Active inner context wins over its Active enclosing context`() {
    assertEquals(
      ActiveInputContext.Portal(Context.B),
      selectActiveInputContext(
        accessibilityFocus =
          CurrentInputFocus.Inside(
            listOf(InputContextCandidate(Context.B, true), InputContextCandidate(Context.A, true))
          ),
        keyboardFocus = CurrentInputFocus.Absent,
        hasForegroundWindowModalPresentation = false,
      ),
    )
  }

  @Test
  fun `focus inside inactive contexts leaves input to host without substituting keyboard B`() {
    assertEquals(
      ActiveInputContext.Host,
      selectActiveInputContext(
        accessibilityFocus =
          CurrentInputFocus.Inside(listOf(InputContextCandidate(Context.A, false))),
        keyboardFocus = CurrentInputFocus.Inside(listOf(InputContextCandidate(Context.B, true))),
        hasForegroundWindowModalPresentation = false,
      ),
    )
  }
}
