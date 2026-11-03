# Keep Glance/WorkManager/Hilt generated classes (consumer rules cover most).
-keepattributes *Annotation*
-keep class com.kamalahmed.prayerapp.widget.** extends androidx.glance.appwidget.GlanceAppWidgetReceiver { *; }
-keep class com.kamalahmed.prayerapp.widget.** implements androidx.glance.appwidget.action.ActionCallback { <init>(); }
# Each GlanceAppWidget subclass must stay a distinct class: Glance maps receivers to providers by class name,
# and R8 class merging (all six widgets were merged into one class in 0.1.0-beta.1/2) made updates render the
# wrong layout into other widgets. Keep them (and their names) unmerged.
-keep class com.kamalahmed.prayerapp.widget.** extends androidx.glance.appwidget.GlanceAppWidget { *; }
