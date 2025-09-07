# Flutter's default rules are guarded by a check like this:
-if class io.flutter.embedding.engine.FlutterEngine
-keep class io.flutter.embedding.engine.FlutterEngine { *; }

# Add project specific ProGuard rules here.
# ... (mungkin ada aturan lain di sini)

# Aturan untuk mengabaikan kelas-kelas compile-time-only yang tidak ada di Android Runtime.
# Ini sering dibutuhkan oleh library seperti AutoValue, Dagger, dll. yang digunakan oleh MediaPipe.
-dontwarn javax.annotation.processing.**
-dontwarn javax.lang.model.**