# Health Connect / Glance keep rules are provided by their AARs.
-keepattributes *Annotation*

# Instantiated by name inside the Shizuku user-service process (see ShizukuWake).
-keep class dev.dazaike.cadence.samsung.ShellWakeService { <init>(); }
-keep class dev.dazaike.cadence.samsung.IWakeService { *; }
-keep class dev.dazaike.cadence.samsung.IWakeService$Stub { *; }
