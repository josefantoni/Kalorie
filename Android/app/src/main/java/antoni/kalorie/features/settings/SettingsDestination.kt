package antoni.kalorie.features.settings

sealed interface SettingsDestination {
    data object Settings : SettingsDestination
    data object Export : SettingsDestination
    data object ModerationQueue : SettingsDestination
    data object ModerationReports : SettingsDestination
}
