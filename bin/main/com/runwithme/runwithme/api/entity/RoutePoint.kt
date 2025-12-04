package com.runwithme.runwithme.api.entity

import jakarta.persistence.Column
import jakarta.persistence.Entity
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import jakarta.persistence.Table
import org.locationtech.jts.geom.Point

@Entity
@Table(name = "route_points")
open class RoutePoint(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "point_id")
    open var pointId: Long? = null,
    @Column(name = "route_id") open var routeId: Long? = null,
    @Column(name = "seq_no") open var seqNo: Int? = null,
    @Column(name = "point_geom", columnDefinition = "geography(Point,4326)")
    open var pointGeom: Point? = null,
    @Column(name = "elevation_m") open var elevationM: Double? = null,
)
