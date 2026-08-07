-keepattributes Signature
-keepattributes *Annotation*
-keep class com.examshield.data.models.** { *; }
-keep class com.examshield.data.remote.ApiModels$* { *; }
-dontwarn okhttp3.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}
