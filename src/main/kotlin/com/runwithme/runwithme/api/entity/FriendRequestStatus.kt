package com.runwithme.runwithme.api.entity

/**
 * Enum representing the status of a friend request.
 */
enum class FriendRequestStatus {
    /**
     * Request is pending and waiting for response
     */
    PENDING,

    /**
     * Request has been accepted
     */
    ACCEPTED,

    /**
     * Request has been rejected
     */
    REJECTED,

    /**
     * Request has been cancelled by the sender
     */
    CANCELLED,
}
