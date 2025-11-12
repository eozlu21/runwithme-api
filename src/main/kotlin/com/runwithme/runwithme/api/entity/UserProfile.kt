package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.Id
import jakarta.persistence.Table
import java.time.LocalDate

@Entity
@Table(name = "user_profile")
open class UserProfile(
    @Id
    @Column(name = "user_id")
    open var userId: Long? = null,
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
    @Column(name = "profile_visibility", nullable = false)
    open var profileVisibility: Boolean = true,
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
