package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.Message
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository
import java.util.UUID

@Repository
interface MessageRepository : JpaRepository<Message, Long> {
    @Query(
        "SELECT m FROM Message m WHERE (m.senderId = :userId1 AND m.recipientId = :userId2) OR (m.senderId = :userId2 AND m.recipientId = :userId1) ORDER BY m.createdAt DESC",
    )
    fun findChatHistory(
        userId1: UUID,
        userId2: UUID,
        pageable: Pageable,
    ): Page<Message>

    fun countByRecipientIdAndIsReadFalse(recipientId: Long): Long
}
