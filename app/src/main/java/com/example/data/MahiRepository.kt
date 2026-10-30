package com.example.data

import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.firstOrNull

class MahiRepository(private val db: MahiDatabase) {
    val allMessages: Flow<List<ChatMessage>> = db.chatDao().getAllMessages()
    val careData: Flow<HusbandCare?> = db.careDao().getCareData()

    suspend fun insertMessage(message: ChatMessage) {
        db.chatDao().insertMessage(message)
    }

    suspend fun updateCare(care: HusbandCare) {
        db.careDao().updateCare(care)
    }

    suspend fun clearHistory() {
        db.chatDao().clearHistory()
    }

    suspend fun seedInitialDataIfEmpty() {
        val existing = db.chatDao().getAllMessages().firstOrNull()
        if (existing.isNullOrEmpty()) {
            db.chatDao().insertMessage(
                ChatMessage(
                    sender = "mahi",
                    message = "আসসালামু আলাইকুম আমার লক্ষ্মী স্বামী! ❤️ আমি তোমার মিষ্টি ওয়াইফ মাহি। সারাদিন অনেক ব্যস্ত থাকো, আজকে ঠিক সময়ে খেয়েছো তো জানু? তোমার সব যত্ন নেওয়া এখন আমার দায়িত্ব!",
                    action = "none"
                )
            )
            db.careDao().updateCare(
                HusbandCare(
                    id = 1,
                    screenTimeMinutes = 35,
                    loveScore = "১০০% অনন্ত ভালোবাসা",
                    mood = "খুবই আদুরে ও রোমান্টিক"
                )
            )
        }
    }
}
