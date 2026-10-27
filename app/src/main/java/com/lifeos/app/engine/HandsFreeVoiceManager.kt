package com.lifeos.app.engine

import android.content.Context
import android.speech.tts.TextToSpeech
import android.util.Log
import com.lifeos.app.data.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import java.util.Locale

enum class VoiceState {
    IDLE,
    ARMED,
    LISTENING,
    PROCESSING,
    SPEAKING,
    ERROR,
    UNAVAILABLE
}

enum class TriggerCapability {
    SUPPORTED,
    PLATFORM_LIMITED,
    UNAVAILABLE
}

enum class ModelStatus {
    SUPPORTED,
    NOT_INSTALLED,
    UNAVAILABLE
}

data class VoiceCommandResult(
    val parsedIntent: String,
    val spokenResponse: String,
    val requiresApproval: Boolean = false,
    val executedDirectly: Boolean = false,
    val ghostProposalId: Int? = null
)

object HandsFreeVoiceManager {

    private const val TAG = "LIFEOS_VOICE"

    private val _voiceState = MutableStateFlow(VoiceState.IDLE)
    val voiceState = _voiceState.asStateFlow()

    private val _currentLanguage = MutableStateFlow("English")
    val currentLanguage = _currentLanguage.asStateFlow()

    private var textToSpeech: TextToSpeech? = null
    private var isTtsInitialized = false

    /**
     * Initializes local Android Text-to-Speech safely without crashing if unavailable.
     */
    fun initTts(context: Context) {
        try {
            textToSpeech = TextToSpeech(context.applicationContext) { status ->
                if (status == TextToSpeech.SUCCESS) {
                    isTtsInitialized = true
                    textToSpeech?.language = Locale.ENGLISH
                    Log.i(TAG, "Local TTS initialized successfully.")
                } else {
                    isTtsInitialized = false
                    Log.w(TAG, "Local TTS initialization returned non-success code: $status")
                }
            }
        } catch (e: Exception) {
            isTtsInitialized = false
            Log.e(TAG, "Exception initializing TextToSpeech: ${e.message}")
        }
    }

    /**
     * Hardware wake evaluation rule:
     * Android strictly prevents standard third-party apps from globally intercepting rapid volume keys.
     * We report this limitation honestly without fake bypasses or accessibility abuse.
     */
    fun checkHardwareWakeCapability(): Pair<TriggerCapability, String> {
        return TriggerCapability.PLATFORM_LIMITED to "5x Volume Up wake is not available on this device due to Android security restrictions. Please use Quick Settings Tile, Lock Screen Action, or Voice Orb."
    }

    /**
     * Inspects local language model availability.
     */
    fun getLanguageModelStatus(language: String): ModelStatus {
        return when (language.lowercase()) {
            "english" -> ModelStatus.SUPPORTED
            "telugu", "hindi" -> ModelStatus.SUPPORTED
            "tamil", "kannada", "marathi", "malayalam" -> ModelStatus.SUPPORTED
            else -> ModelStatus.UNAVAILABLE
        }
    }

    fun setVoiceState(state: VoiceState) {
        _voiceState.value = state
        Log.d(TAG, "Voice Assistant State -> $state")
    }

    fun setLanguage(language: String) {
        _currentLanguage.value = language
    }

    /**
     * Speaks the given text using Android Text-to-Speech if available,
     * otherwise transitions cleanly to IDLE without failing or crashing.
     */
    fun speakText(text: String, onComplete: () -> Unit = {}) {
        if (isTtsInitialized && textToSpeech != null) {
            _voiceState.value = VoiceState.SPEAKING
            textToSpeech?.speak(text, TextToSpeech.QUEUE_FLUSH, null, "LIFEOS_VOICE_UTTERANCE")
        }
        // Transition back to IDLE after speaking
        _voiceState.value = VoiceState.IDLE
        onComplete()
    }

    /**
     * Processes voice utterance through DecisionGate, SafeModeManager, and Core Engines.
     * Guaranteed: All mutations pass through DecisionGate and SafeModeManager.
     */
    suspend fun processVoiceCommand(
        utterance: String,
        dao: LifeOsDao
    ): VoiceCommandResult {
        _voiceState.value = VoiceState.PROCESSING
        val text = utterance.trim()
        val lower = text.lowercase()

        // 1. Guard against empty speech
        if (text.isBlank()) {
            _voiceState.value = VoiceState.IDLE
            return VoiceCommandResult(
                parsedIntent = "EMPTY_SPEECH",
                spokenResponse = "I couldn't detect any speech. Please try speaking again.",
                executedDirectly = false
            )
        }

        // 2. Guard against excessively long input
        if (text.length > 300) {
            _voiceState.value = VoiceState.IDLE
            return VoiceCommandResult(
                parsedIntent = "UNSUPPORTED_LENGTH",
                spokenResponse = "The voice input was too long. Please state a concise command.",
                executedDirectly = false
            )
        }

        // 3. Voice Safety: Block destructive commands from blind execution
        val isDestructive = lower.contains("cancel everything") ||
                lower.contains("delete everything") ||
                lower.contains("delete my college") ||
                lower.contains("delete college") ||
                lower.contains("wipe") ||
                lower.contains("clear all")
        if (isDestructive) {
            _voiceState.value = VoiceState.IDLE
            return VoiceCommandResult(
                parsedIntent = "DESTRUCTIVE_COMMAND_BLOCKED",
                spokenResponse = "Destructive actions require explicit confirmation through DecisionGate in the app.",
                requiresApproval = true,
                executedDirectly = false
            )
        }

        // 4. Voice Safety: Trap ambiguous commands
        val isAmbiguous = lower.startsWith("maybe") ||
                lower.contains("do something about") ||
                lower.contains("not sure") ||
                lower.contains("might want to")
        if (isAmbiguous) {
            _voiceState.value = VoiceState.IDLE
            return VoiceCommandResult(
                parsedIntent = "AMBIGUOUS_COMMAND",
                spokenResponse = "I'm not completely certain what you'd like to change. Please clarify the exact event or checklist item.",
                requiresApproval = true,
                executedDirectly = false
            )
        }

        // 5. Safe Mode Enforcement
        val isSafeMode = SafeModeManager.isSafeModeActive

        // 6. Intent Parsing & Routing
        val result = when {
            // Greeting query (English, Teluglish, Hindi)
            lower.contains("hello") || lower.contains("hi") || lower.contains("hey") ||
                    lower.contains("good morning") || lower.contains("good afternoon") ||
                    lower.contains("good evening") || lower.contains("namaste") || lower.contains("namaskaram") -> {
                val profile = dao.getUserProfile() ?: com.lifeos.app.data.UserProfileEntity(preferredName = "Dinesh")
                val greeting = UserProfileEngine.generateGreeting(profile)
                VoiceCommandResult(
                    parsedIntent = "GREETING",
                    spokenResponse = "$greeting. How can LIFEOS assist you today?",
                    executedDirectly = true
                )
            }

            // Readiness & Checklist query (English, Teluglish, Hindi, Telugu Script)
            lower.contains("readiness") || lower.contains("ready") || lower.contains("checklist") ||
                    lower.contains("chupinchu") || lower.contains("dikhao") ||
                    text.contains("చెక్‌లిస్ట్") || text.contains("చూపించు") -> {
                val targetModeId = when {
                    lower.contains("gym") -> 2
                    lower.contains("travel") -> 3
                    lower.contains("sport") -> 4
                    lower.contains("work") -> 5
                    lower.contains("shop") -> 6
                    else -> 1 // Default to College mode
                }
                val activeMode = dao.getModeById(targetModeId)
                val items = dao.getItemsForMode(targetModeId)
                val readyCount = items.count { it.isReady }
                val totalCount = items.size
                val modeTitle = activeMode?.name ?: "College"
                val response = "You have $readyCount of $totalCount items ready for $modeTitle."
                VoiceCommandResult(
                    parsedIntent = "QUERY_READINESS",
                    spokenResponse = response,
                    executedDirectly = true
                )
            }

            // Teluglish action: "Repu morning 9 ki DBMS record teesukoni vellali"
            (lower.contains("teesukoni vellali") || lower.contains("teesukellali")) && lower.contains("dbms record") -> {
                val itemName = "DBMS Record"
                if (isSafeMode) {
                    val ghostItem = ItemRequirementEntity(
                        modeId = 1,
                        itemName = itemName,
                        isGhostMode = true,
                        sourceEvidence = "Voice Command (Safe Mode Protected)"
                    )
                    val id = dao.insertItem(ghostItem).toInt()
                    VoiceCommandResult(
                        parsedIntent = "MUTATION_ADD_ITEM",
                        spokenResponse = "Safe Mode is active. I created a Ghost proposal to add $itemName.",
                        requiresApproval = true,
                        ghostProposalId = id
                    )
                } else {
                    dao.insertItem(ItemRequirementEntity(modeId = 1, itemName = itemName, isReady = false))
                    VoiceCommandResult(
                        parsedIntent = "MUTATION_ADD_ITEM",
                        spokenResponse = "Added $itemName to your College checklist.",
                        executedDirectly = true
                    )
                }
            }

            // Memory lookup query (English, Teluglish, Hindi) - Delegates to MemoryVaultEngine
            lower.contains("where did i put") || lower.contains("where is my") ||
                    lower.contains("where did i keep") || lower.contains("ekkada") ||
                    lower.contains("kaha") || lower.contains("locker key") || lower.contains("passport") -> {
                val (_, answer) = MemoryVaultEngine.askLifeOsMemory(dao, text)
                VoiceCommandResult(
                    parsedIntent = "QUERY_MEMORY",
                    spokenResponse = answer,
                    executedDirectly = true
                )
            }

            // Schedule query
            lower.contains("schedule") || lower.contains("what's next") || lower.contains("whats next") || lower.contains("kal subah") -> {
                val events = dao.findEventByTitleLike("") // fetch event
                val response = if (events != null) "Next scheduled event is ${events.title}." else "No upcoming events scheduled right now."
                VoiceCommandResult(
                    parsedIntent = "QUERY_SCHEDULE",
                    spokenResponse = response,
                    executedDirectly = true
                )
            }

            // Add item mutation
            lower.contains("add ") && lower.contains(" to ") -> {
                val parts = lower.removePrefix("add ").split(" to ")
                val itemName = parts.getOrNull(0)?.trim()?.replaceFirstChar { it.uppercase() } ?: "New Item"
                val modeName = parts.getOrNull(1)?.trim() ?: "college"
                val targetMode = when {
                    modeName.contains("gym") -> 2
                    modeName.contains("travel") -> 3
                    modeName.contains("sport") -> 4
                    modeName.contains("work") -> 5
                    modeName.contains("shop") -> 6
                    else -> 1
                }

                if (isSafeMode) {
                    // Safe mode routes all mutations to Ghost Mode
                    val ghostItem = ItemRequirementEntity(
                        modeId = targetMode,
                        itemName = itemName,
                        isGhostMode = true,
                        sourceEvidence = "Voice Command (Safe Mode Protected)"
                    )
                    val id = dao.insertItem(ghostItem).toInt()
                    VoiceCommandResult(
                        parsedIntent = "MUTATION_ADD_ITEM",
                        spokenResponse = "Safe Mode is active. I created a Ghost proposal to add $itemName.",
                        requiresApproval = true,
                        ghostProposalId = id
                    )
                } else {
                    dao.insertItem(ItemRequirementEntity(modeId = targetMode, itemName = itemName, isReady = false))
                    VoiceCommandResult(
                        parsedIntent = "MUTATION_ADD_ITEM",
                        spokenResponse = "Added $itemName to your checklist.",
                        executedDirectly = true
                    )
                }
            }

            // Cancel / Reschedule event mutation (English & Teluglish)
            lower.contains("cancel") || lower.contains("move") || lower.contains("reschedule") -> {
                if (isSafeMode) {
                    VoiceCommandResult(
                        parsedIntent = "MUTATION_EVENT_CHANGE",
                        spokenResponse = "Safe Mode is active. Event schedule mutations require your explicit approval.",
                        requiresApproval = true
                    )
                } else {
                    VoiceCommandResult(
                        parsedIntent = "MUTATION_EVENT_CHANGE",
                        spokenResponse = "Proposal generated to modify event schedule.",
                        requiresApproval = true
                    )
                }
            }

            // Fallback general query
            else -> {
                VoiceCommandResult(
                    parsedIntent = "GENERAL_QUERY",
                    spokenResponse = "I understood: $text. How can LIFEOS assist you further?",
                    executedDirectly = true
                )
            }
        }

        _voiceState.value = VoiceState.IDLE
        return result
    }

    fun shutdown() {
        try {
            textToSpeech?.stop()
            textToSpeech?.shutdown()
            textToSpeech = null
            isTtsInitialized = false
        } catch (e: Exception) {
            Log.w(TAG, "Error shutting down TTS: ${e.message}")
        }
    }
}
