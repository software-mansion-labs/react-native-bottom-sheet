package com.swmansion.reactnativebottomsheet

import android.content.Context
import com.facebook.react.views.view.ReactViewGroup

// Visual surface that sits behind the sheet content. It carries no behavior of
// its own; the BottomSheetView identifies it by this type, and it covers the full
// sheet so a content shrink never exposes blank space. Its React children provide
// the appearance only.
class BottomSheetSurfaceView(context: Context) : ReactViewGroup(context)
