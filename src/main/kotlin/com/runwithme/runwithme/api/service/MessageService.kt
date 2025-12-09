package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.dto.CreateMessageRequest
import com.runwithme.runwithme.api.dto.MessageDto
import com.runwithme.runwithme.api.dto.PageResponse
import com.runwithme.runwithme.api.entity.Message
import com.runwithme.runwithme.api.repository.FriendshipRepository
import com.runwithme.runwithme.api.repository.MessageRepository
import com.runwithme.runwithme.api.repository.UserRepository
import org.springframework.data.domain.Page
import org.springframework.data.domain.PageRequest
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import java.util.UUID

@Service
class MessageService(
    private val messageRepository: MessageRepository,
    private val friendshipRepository: FriendshipRepository,
    private val userRepository: UserRepository,
) {
    @Transactional
    fun sendMessage(
        senderUsername: String,
        request: CreateMessageRequest,
    ): MessageDto {
        val sender =
            userRepository.findByUsername(senderUsername).orElseThrow {
                RuntimeException("User not found: $senderUsername")
            }

        val recipient =
            userRepository.findById(request.recipientId).orElseThrow {
                RuntimeException("Recipient not found: ${request.recipientId}")
            }

        val message =
            Message(
                senderId = sender.userId,
                recipientId = recipient.userId,
                content = request.content,
            )

        val savedMessage = messageRepository.save(message)
        return MessageDto.fromEntity(savedMessage, sender.username, recipient.username)
    }

    @Transactional(readOnly = true)
    fun getChatHistory(
        username: String,
        otherUserId: UUID,
        page: Int,
        size: Int,
    ): PageResponse<MessageDto> {
        val currentUser =
            userRepository.findByUsername(username).orElseThrow {
                RuntimeException("User not found: $username")
            }

        val otherUser =
            userRepository.findById(otherUserId).orElseThrow {
                RuntimeException("User not found: $otherUserId")
            }

        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }

        val pageRequest = PageRequest.of(safePage, safeSize)
        val messagePage =
            messageRepository.findChatHistory(currentUser.userId!!, otherUserId, pageRequest)

        return PageResponse.fromPage(messagePage) {
            val senderName =
                if (it.senderId == currentUser.userId) {
                    currentUser.username
                } else {
                    otherUser.username
                }
            val recipientName =
                if (it.recipientId == currentUser.userId) {
                    currentUser.username
                } else {
                    otherUser.username
                }
            MessageDto.fromEntity(it, senderName, recipientName)
        }
    }

    @Transactional(readOnly = true)
    fun getAllRelatedMessages(
        username: String,
        page: Int,
        size: Int,
        friendsOnly: Boolean,
    ): PageResponse<MessageDto> {
        val currentUser =
            userRepository.findByUsername(username).orElseThrow {
                RuntimeException("User not found: $username")
            }

        val safePage = if (page < 0) 0 else page
        val safeSize =
            when {
                size < 1 -> 10
                size > 100 -> 100
                else -> size
            }

        val pageRequest = PageRequest.of(safePage, safeSize)
        val friendIds = friendshipRepository.findFriendIds(currentUser.userId!!)

        val messagePage: Page<Message> =
            if (friendsOnly) {
                if (friendIds.isEmpty()) {
                    Page.empty(pageRequest)
                } else {
                    messageRepository.findRelatedMessagesWithFriends(
                        currentUser.userId!!,
                        friendIds,
                        pageRequest,
                    )
                }
            } else {
                messageRepository.findRelatedMessagesForUser(currentUser.userId!!, pageRequest)
            }

        val participantUserIds =
            messagePage.content.flatMap { listOfNotNull(it.senderId, it.recipientId) }.toSet()

        val usersById = userRepository.findAllById(participantUserIds).associateBy { it.userId }

        return PageResponse.fromPage(messagePage) {
            val senderName =
                if (it.senderId == currentUser.userId) {
                    currentUser.username
                } else {
                    usersById[it.senderId]?.username
                }

            val recipientName =
                if (it.recipientId == currentUser.userId) {
                    currentUser.username
                } else {
                    usersById[it.recipientId]?.username
                }

            MessageDto.fromEntity(it, senderName, recipientName)
        }
    }

    @Transactional
    fun markMessagesAsRead(
        username: String,
        messageIds: List<Long>,
    ): Int {
        if (messageIds.isEmpty()) return 0

        val currentUser =
            userRepository.findByUsername(username).orElseThrow {
                RuntimeException("User not found: $username")
            }

        val distinctIds = messageIds.distinct()
        return try {
            messageRepository.markMessagesAsReadByIds(currentUser.userId!!, distinctIds)
        } catch (ex: org.springframework.dao.OptimisticLockingFailureException) {
            // Handle concurrent update conflict, e.g., by logging or returning 0
            0
        }
    }
}
