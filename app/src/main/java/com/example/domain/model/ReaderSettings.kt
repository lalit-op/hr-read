package com.example.domain.model

enum class AppThemeSetting(val title: String) {
    SYSTEM("System default"),
    LIGHT("Light mode"),
    DARK("Dark mode")
}

enum class DefaultReaderMode(val title: String) {
    CONTINUOUS_VERTICAL("Continuous vertical scroll"),
    SINGLE_PAGE("Single page flip")
}

enum class DefaultPageFitting(val title: String) {
    FIT_TO_WIDTH("Fit to width"),
    FIT_TO_PAGE("Fit whole page")
}

data class ReaderSettings(
    val theme: AppThemeSetting = AppThemeSetting.SYSTEM,
    val defaultReaderMode: DefaultReaderMode = DefaultReaderMode.CONTINUOUS_VERTICAL,
    val defaultPageFitting: DefaultPageFitting = DefaultPageFitting.FIT_TO_WIDTH,
    val isOcrEnabled: Boolean = false,
    val isAutoNightModeEnabled: Boolean = false,
    val keepScreenOn: Boolean = true
)
