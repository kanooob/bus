package com.example.data.model

enum class ThemeMode(val label: String) {
    LIGHT("Clair"),
    DARK("Sombre"),
    SYSTEM("Système")
}

enum class PrimaryColorMode(val label: String) {
    DYNAMIC("Couleurs dynamiques (Material You)"),
    APP_DEFAULT("Couleur de l'application")
}

enum class InAppRefreshFrequency(val label: String, val intervalSeconds: Int) {
    EVERY_45_SEC("Toutes les 45 secondes", 45),
    EVERY_1_MIN("Toutes les minutes", 60),
    EVERY_2_MIN("Toutes les 2 min", 120),
    EVERY_3_MIN("Toutes les 3 min", 180),
    EVERY_5_MIN("Toutes les 5 min", 300),
    EVERY_10_MIN("Toutes les 10 min", 600),
    ON_OPEN_OR_STOP_CHANGE("À l'ouverture ou au changement d'arrêt", 0)
}

enum class WidgetRefreshFrequency(val label: String, val intervalMinutes: Int) {
    EVERY_1_MIN("Toutes les min", 1),
    EVERY_2_MIN("Toutes les 2 min", 2),
    EVERY_3_MIN("Toutes les 3 min", 3),
    EVERY_5_MIN("Toutes les 5 min", 5),
    EVERY_15_MIN("Toutes les 15 min", 15),
    MANUAL_ONLY("Manuelle uniquement (et à l'ouverture de l'application)", 0)
}

enum class WidgetLocationFrequency(val label: String, val intervalMinutes: Int) {
    EVERY_1_MIN("Toutes les min", 1),
    EVERY_2_MIN("Toutes les 2 min", 2),
    EVERY_3_MIN("Toutes les 3 min", 3),
    EVERY_5_MIN("Toutes les 5 min", 5),
    EVERY_10_MIN("Toutes les 10 min", 10),
    EVERY_15_MIN("Toutes les 15 min", 15),
    MANUAL_ONLY("Manuelle uniquement (et à l'ouverture de l'application)", 0)
}

enum class GtfsSyncFrequency(
    val label: String,
    val intervalMs: Long,
    val isPeriodic: Boolean
) {
    EVERY_1_HOUR("Toutes les heures", 1L * 3_600_000L, true),
    EVERY_2_HOURS("Toutes les 2 heures", 2L * 3_600_000L, true),
    EVERY_5_HOURS("Toutes les 5 heures", 5L * 3_600_000L, true),
    TWICE_A_DAY("2 fois par jour", 12L * 3_600_000L, true),
    DAILY("Quotidienne", 24L * 3_600_000L, true),
    DAILY_ON_OPEN("Quotidienne à l'ouverture", 24L * 3_600_000L, false),
    WEEKLY("Hebdomadaire", 7L * 24L * 3_600_000L, true),
    NEVER("Jamais", 0L, false)
}

enum class TrafficRefreshFrequency(val label: String, val intervalMs: Long) {
    EVERY_30_MIN("Toutes les 30 min", 30L * 60_000L),
    EVERY_1_HOUR("Toutes les heures", 1L * 3_600_000L),
    EVERY_2_HOURS("Toutes les 2 heures", 2L * 3_600_000L),
    EVERY_5_HOURS("Toutes les 5 heures", 5L * 3_600_000L),
    EVERY_12_HOURS("Toutes les 12 heures", 12L * 3_600_000L),
    DAILY("Tous les jours", 24L * 3_600_000L),
    NEVER("Jamais", 0L)
}

enum class AppUpdateCheckFrequency(val label: String, val intervalMs: Long) {
    DAILY_ON_OPEN("Quotidienne à l'ouverture", 24L * 3_600_000L),
    WEEKLY("Hebdomadaire", 7L * 24L * 3_600_000L),
    MONTHLY("Mensuelle", 30L * 24L * 3_600_000L),
    NEVER("Jamais", 0L)
}

enum class UpdateNetworkMode(val label: String) {
    WIFI_ONLY("Wi-Fi uniquement"),
    WIFI_AND_CELLULAR("Wi-Fi et données mobiles")
}
