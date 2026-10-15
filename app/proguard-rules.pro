# R8/ProGuard rules for release builds.
#
# `isMinifyEnabled` is currently false, so nothing here is applied yet. The file exists so that
# turning minification on does not fail the build (the release type references it) and does not
# break the AI layer, which relies on reflection.

# --- Moshi -------------------------------------------------------------------------------------
# The Gemini wire models are (de)serialised by reflection through KotlinJsonAdapterFactory, so R8
# must not rename or strip their fields.
-keepclassmembers class com.example.uepflip_flashcard.data.service.** {
    <fields>;
    <init>(...);
}
-keepclassmembers class kotlin.Metadata { public <methods>; }
-keep,allowobfuscation,allowshrinking class kotlin.reflect.KClass
-keep @com.squareup.moshi.JsonQualifier interface *
-keepclassmembers class * {
    @com.squareup.moshi.FromJson <methods>;
    @com.squareup.moshi.ToJson <methods>;
}

# --- PDFBox ------------------------------------------------------------------------------------
# PDFBox references desktop-only JDK classes (java.beans, java.awt) that do not exist on Android.
-dontwarn org.apache.pdfbox.**
-dontwarn java.awt.**
-dontwarn javax.**
-dontwarn org.bouncycastle.**
