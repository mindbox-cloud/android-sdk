# Room
-keep class * extends androidx.room.RoomDatabase { void <init>(); }
-keep @androidx.room.Entity class *
-dontwarn androidx.room.paging.**