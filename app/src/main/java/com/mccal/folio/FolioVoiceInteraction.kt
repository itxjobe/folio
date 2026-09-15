package com.mccal.folio

import android.content.Intent
import android.os.Bundle
import android.service.voice.VoiceInteractionService
import android.service.voice.VoiceInteractionSession
import android.service.voice.VoiceInteractionSessionService
import android.speech.RecognitionService
import android.speech.SpeechRecognizer

/**
 * Folio as a full "Digital assistant app". Some phones only offer a real voice interaction service on the side key
 * (One UI's press-and-hold starts the assistant through it, not through ACTION_ASSIST), so Folio provides a minimal
 * one: holding the key shows Folio's assistant picker. It never listens or records.
 */
class FolioVoiceInteractionService : VoiceInteractionService()

class FolioVoiceSessionService : VoiceInteractionSessionService() {
    override fun onNewSession(args: Bundle?): VoiceInteractionSession = FolioVoiceSession(this)
}

private class FolioVoiceSession(service: VoiceInteractionSessionService) : VoiceInteractionSession(service) {
    override fun onShow(args: Bundle?, showFlags: Int) {
        super.onShow(args, showFlags)
        runCatching { startAssistantActivity(Intent(context, AssistPickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) }
            .onFailure { runCatching { context.startActivity(Intent(context, AssistPickerActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
        hide()
    }
}

/** Android requires an assistant to name a speech recognizer; Folio doesn't do speech, so this one always declines. */
class FolioRecognitionService : RecognitionService() {
    override fun onStartListening(recognizerIntent: Intent?, listener: Callback?) {
        runCatching { listener?.error(SpeechRecognizer.ERROR_CLIENT) }
    }
    override fun onCancel(listener: Callback?) = Unit
    override fun onStopListening(listener: Callback?) = Unit
}
