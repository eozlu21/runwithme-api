package com.runwithme.runwithme.api.entity

/**
 * Enum representing the visibility settings for a user's profile.
 */
enum class ProfileVisibility {
    /**
     * Profile is visible to everyone
     */
    PUBLIC,

    /**
     * Profile is visible only to friends
     */
    FRIENDS_ONLY,

    /**
     * Profile is visible to friends and friends of friends
     */
    FRIENDS_OF_FRIENDS,

    /**
     * Profile is completely private (only visible to the owner)
     */
    PRIVATE,
}
