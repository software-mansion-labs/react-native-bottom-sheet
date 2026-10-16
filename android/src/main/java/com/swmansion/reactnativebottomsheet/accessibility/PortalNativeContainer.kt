package com.swmansion.reactnativebottomsheet.accessibility

import android.view.View

/** Current owner of an existing native container that redirects Fabric children. */
internal interface PortalNativeContainer {
  val portalNativeOwner: View?
}
