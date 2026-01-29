package com.flowsyu.composedemo.ui.preview

import androidx.compose.ui.tooling.preview.Preview

@Preview(
    name = "Phone Portrait",
    device = "spec:width=1080px,height=2340px,dpi=480", showBackground = true
)
@Preview(
    name = "Phone Landscape",
    device = "spec:width=2340px,height=1080px,dpi=480", showBackground = true
)
@Preview(
    name = "TV 1080p",
    device = "spec:width=1920px,height=1080px,dpi=320,orientation=landscape",
    showBackground = true
)
annotation class DevicePreviews
