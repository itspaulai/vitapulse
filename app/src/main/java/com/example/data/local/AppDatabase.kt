package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "chat_sessions")
data class ChatSessionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val createdAt: Long = System.currentTimeMillis(),
    val updatedAt: Long = System.currentTimeMillis(),
    val lastMessagePreview: String = "",
    val healthWindowBadge: String = "7D"
)

@Entity(tableName = "chat_messages")
data class ChatMessageEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val sessionId: Long,
    val role: String, // "USER" or "ASSISTANT"
    val content: String,
    val attachedHealthBadge: String = "",
    val attachedHealthContext: String = "",
    val tokensPerSec: Double = 0.0,
    val engineBackend: String = "LiteRT GPU",
    val isPinned: Boolean = false,
    val timestamp: Long = System.currentTimeMillis()
)

@Entity(tableName = "pinned_insights")
data class PinnedInsightEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val content: String,
    val category: String, // InsightCategory name
    val metricsBadge: String,
    val sourcePrompt: String = "",
    val createdAt: Long = System.currentTimeMillis()
)

@Dao
interface VitalPulseDao {
    @Query("SELECT * FROM chat_sessions ORDER BY updatedAt DESC")
    fun getAllSessions(): Flow<List<ChatSessionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSession(session: ChatSessionEntity): Long

    @Query("UPDATE chat_sessions SET title = :title, updatedAt = :updatedAt, lastMessagePreview = :preview WHERE id = :sessionId")
    suspend fun updateSessionSummary(sessionId: Long, title: String, updatedAt: Long, preview: String)

    @Query("DELETE FROM chat_sessions WHERE id = :sessionId")
    suspend fun deleteSession(sessionId: Long)

    @Query("SELECT * FROM chat_messages WHERE sessionId = :sessionId ORDER BY timestamp ASC")
    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertMessage(message: ChatMessageEntity): Long

    @Query("UPDATE chat_messages SET content = :content, tokensPerSec = :tokPerSec, engineBackend = :backend, attachedHealthBadge = :badge, attachedHealthContext = :contextText WHERE id = :messageId")
    suspend fun updateMessageContent(
        messageId: Long,
        content: String,
        tokPerSec: Double,
        backend: String,
        badge: String,
        contextText: String
    )

    @Query("UPDATE chat_messages SET isPinned = :isPinned WHERE id = :messageId")
    suspend fun updateMessagePinned(messageId: Long, isPinned: Boolean)

    @Query("DELETE FROM chat_messages WHERE sessionId = :sessionId")
    suspend fun deleteMessagesForSession(sessionId: Long)

    @Query("SELECT * FROM pinned_insights ORDER BY createdAt DESC")
    fun getAllPinnedInsights(): Flow<List<PinnedInsightEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPinnedInsight(insight: PinnedInsightEntity): Long

    @Query("DELETE FROM pinned_insights WHERE id = :insightId")
    suspend fun deletePinnedInsight(insightId: Long)
}

@Database(
    entities = [
        ChatSessionEntity::class,
        ChatMessageEntity::class,
        PinnedInsightEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun vitalPulseDao(): VitalPulseDao

    companion object {
        @Volatile
        private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "vitalpulse_local_vault.db"
                )
                    .fallbackToDestructiveMigration(true)
                    .build()
                INSTANCE = instance
                instance
            }
        }
    }
}

class LocalVaultRepository(private val dao: VitalPulseDao) {
    val allSessions: Flow<List<ChatSessionEntity>> = dao.getAllSessions()
    val allPinnedInsights: Flow<List<PinnedInsightEntity>> = dao.getAllPinnedInsights()

    fun getMessagesForSession(sessionId: Long): Flow<List<ChatMessageEntity>> =
        dao.getMessagesForSession(sessionId)

    suspend fun createSession(title: String, windowBadge: String): Long {
        return dao.insertSession(
            ChatSessionEntity(
                title = title,
                healthWindowBadge = windowBadge,
                lastMessagePreview = "Local Gemma 4 E2B conversation ready"
            )
        )
    }

    suspend fun updateSession(sessionId: Long, title: String, preview: String) {
        dao.updateSessionSummary(
            sessionId = sessionId,
            title = title,
            updatedAt = System.currentTimeMillis(),
            preview = preview
        )
    }

    suspend fun deleteSession(sessionId: Long) {
        dao.deleteMessagesForSession(sessionId)
        dao.deleteSession(sessionId)
    }

    suspend fun insertMessage(message: ChatMessageEntity): Long = dao.insertMessage(message)

    suspend fun updateMessage(
        messageId: Long,
        content: String,
        tokPerSec: Double,
        backend: String,
        badge: String = "",
        contextText: String = ""
    ) {
        dao.updateMessageContent(messageId, content, tokPerSec, backend, badge, contextText)
    }

    suspend fun markMessagePinned(messageId: Long, isPinned: Boolean) {
        dao.updateMessagePinned(messageId, isPinned)
    }

    suspend fun pinInsight(insight: PinnedInsightEntity): Long = dao.insertPinnedInsight(insight)

    suspend fun removePinnedInsight(insightId: Long) = dao.deletePinnedInsight(insightId)
}
