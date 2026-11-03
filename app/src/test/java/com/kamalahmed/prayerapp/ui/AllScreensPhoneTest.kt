package com.kamalahmed.prayerapp.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w411dp-h891dp")
class AllScreensPhoneTest : AllScreensSmoke(listDetail = false)
