package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate
import java.util.UUID

@Entity
@Table(name = "user_profiles")
open class UserProfile(
    @Id
    @Column(name = "user_id")
    open var userId: UUID? = null,
    @Column(name = "first_name")
    open var firstName: String? = null,
    @Column(name = "last_name")
    open var lastName: String? = null,
    @Column(name = "pronouns")
    open var pronouns: String? = null,
    @Column(name = "birthday")
    open var birthday: LocalDate? = null,
    @Column(name = "expert_level")
    open var expertLevel: String? = null,
    @Column(name = "profile_pic")
    open var profilePic: String? = null,
    /**
     * Profile visibility setting. Stored as String to support:
     * - PUBLIC: visible to everyone
     * - FRIENDS_ONLY: visible only to friends
     * - FRIENDS_OF_FRIENDS: visible to friends and friends of friends
     * - PRIVATE: visible only to the owner
     * For backwards compatibility, "true" is treated as PUBLIC and "false" as PRIVATE
     */
    @Column(name = "profile_visibility", nullable = false)
    open var profileVisibility: String = ProfileVisibility.PUBLIC.name,
    @Column(name = "region_id")
    open var regionId: Int? = null,
    @Column(name = "subregion_id")
    open var subregionId: Int? = null,
    @Column(name = "country_id")
    open var countryId: Int? = null,
    @Column(name = "state_id")
    open var stateId: Int? = null,
    @Column(name = "city_id")
    open var cityId: Int? = null,
)
