# Keep Glance/WorkManager/Hilt generated classes (consumer rules cover most).
-keepattributes *Annotation*
-keep class com.kamalahmed.prayerapp.widget.** extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class com.kamalahmed.prayerapp.widget.** implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
