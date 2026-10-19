package dev.schrammel.brilliantlist.feature_crisis

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import dev.schrammel.brilliantlist.core_data.repository.SettingsRepository
import dev.schrammel.brilliantlist.core_model.CrisisResource
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import javax.inject.Inject

@HiltViewModel
class CrisisViewModel @Inject constructor(
    settingsRepository: SettingsRepository
) : ViewModel() {

    val region: StateFlow<String> = settingsRepository.settings
        .map { it.crisisRegion }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5_000), "US")

    /**
     * Offline crisis resource catalog. Numbers are widely published helplines;
     * verify locally before shipping. Emergency numbers are always included.
     */
    fun resourcesFor(region: String): List<CrisisResource> = when (region.uppercase()) {
        "US" -> listOf(
            CrisisResource("988 Suicide & Crisis Lifeline", "24/7 free, confidential support for mental health, suicidal thoughts, or emotional distress.", "988"),
            CrisisResource("Crisis Text Line", "Text-based support, available 24/7.", "741741"),
            CrisisResource("Emergency Services", "Immediate life-threatening emergencies.", "911")
        )
        "UK", "GB" -> listOf(
            CrisisResource("Samaritans", "24/7 listening support, free from any phone.", "116123"),
            CrisisResource("SHOUT Crisis Text Line", "Text-based support, 24/7.", "85258"),
            CrisisResource("Emergency Services", "Immediate life-threatening emergencies.", "999")
        )
        "CA" -> listOf(
            CrisisResource("988 Suicide Crisis Helpline", "24/7 bilingual support across Canada.", "988"),
            CrisisResource("Emergency Services", "Immediate life-threatening emergencies.", "911")
        )
        "AU" -> listOf(
            CrisisResource("Lifeline Australia", "24/7 crisis support and suicide prevention.", "131114"),
            CrisisResource("Emergency Services", "Immediate life-threatening emergencies.", "000")
        )
        "DE" -> listOf(
            CrisisResource("Telefonseelsorge", "24/7 free and anonymous support (Deutsch/English).", "08001110111"),
            CrisisResource("Emergency Services", "Immediate life-threatening emergencies.", "112")
        )
        else -> listOf(
            // Generic fallback for unlisted regions.
            CrisisResource("Local Emergency Services", "Call your local emergency number for immediate help.", "112"),
            CrisisResource("findahelpline.com (offline note)", "When online, findahelpline.com lists verified helplines by country.", "112")
        )
    }
}
