# Правила обфускации для release-сборки.
# Держим пустым для debug — R8 в debug не запускается.
-keep class com.gvineon550coder.tvapp.data.** { *; }
-keepattributes Signature
-keepattributes *Annotation*
