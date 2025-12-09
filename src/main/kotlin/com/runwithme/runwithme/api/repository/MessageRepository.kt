package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.Message
import org.springframework.data.domain.Page
import org.springframework.data.domain.Pageable
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Modifying
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

    @Query(
        """
        SELECT m FROM Message m
        WHERE (m.senderId = :userId OR m.recipientId = :userId)
        ORDER BY m.createdAt DESC
        """,
    )
    fun findRelatedMessagesForUser(
        userId: UUID,
        pageable: Pageable,
    ): Page<Message>

    @Query(
        """
        SELECT m FROM Message m
        WHERE ((m.senderId = :userId AND m.recipientId IN :friendIds) OR (m.recipientId = :userId AND m.senderId IN :friendIds))
        ORDER BY m.createdAt DESC
        """,
    )
    fun findRelatedMessagesWithFriends(
        userId: UUID,
        friendIds: List<UUID>,
        pageable: Pageable,
    ): Page<Message>

    fun countByRecipientIdAndIsReadFalse(recipientId: UUID): Long

    // NOTE: recipientId must always be set to the authenticated user's ID.
    @Modifying(clearAutomatically = true)
    @Query(
        "UPDATE Message m SET m.isRead = true WHERE m.recipientId = :recipientId AND m.id IN :messageIds",
    )
    fun markMessagesAsReadByIds(
        recipientId: UUID, // Must be the authenticated user's ID
        messageIds: List<Long>,
    ): Int
}
