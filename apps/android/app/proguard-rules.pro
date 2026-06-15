# lyvox-vault ProGuard Rules
# Keep all model classes for GSON serialization
-keepclassmembers class com.lyvox.vault.data.model.** { *; }
-keepclassmembers class com.lyvox.vault.service.BackupEnvelope { *; }
-keepclassmembers class com.lyvox.vault.service.BackupPayload { *; }

# Keep Bouncy Castle
-keep class org.bouncycastle.** { *; }
-dontwarn org.bouncycastle.**

# Keep crypto classes
-keep class com.lyvox.vault.crypto.** { *; }

# Ktor/ML Kit transitive logging references are optional in release.
-dontwarn org.slf4j.**
