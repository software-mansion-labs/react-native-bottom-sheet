package com.swmansion.reactnativebottomsheet

import android.view.View
import com.facebook.react.uimanager.common.UIManagerType
import com.facebook.react.uimanager.common.ViewUtil
import org.robolectric.annotation.Implementation
import org.robolectric.annotation.Implements

/**
 * Keeps the legacy/native fixture path available in controller tests. RN 0.88 classifies every view
 * as Fabric; these tests use untagged native views without a live Fabric registry. Even-tagged
 * fixtures still exercise the Fabric path, which is also covered by the JNI integration tests.
 */
@Implements(ViewUtil::class)
class TestViewUtilShadow {
  companion object {
    @JvmStatic
    @Implementation
    fun getUIManagerType(view: View): Int =
      if (view.id > 0 && view.id % 2 == 0) UIManagerType.FABRIC else UIManagerType.DEFAULT
  }
}
