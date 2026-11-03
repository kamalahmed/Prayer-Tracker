package com.kamalahmed.prayerapp.ui

import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34], qualifiers = "w840dp-h700dp")
class AllScreensFoldTest : AllScreensSmoke(listDetail = true)
