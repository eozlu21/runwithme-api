package com.runwithme.runwithme.api.service

import com.runwithme.runwithme.api.entity.RoutePoint
import org.springframework.stereotype.Service
import kotlin.math.min

@Service
class RouteMatchingService {
    /**
     * Calculates the Dynamic Time Warping (DTW) distance between two sequences of route points.
     * Lower distance means more similar routes.
     */
    fun calculateDTWDistance(
        route1Points: List<RoutePoint>,
        route2Points: List<RoutePoint>,
    ): Double {
        if (route1Points.isEmpty() || route2Points.isEmpty()) {
            return Double.MAX_VALUE
        }

        val n = route1Points.size
        val m = route2Points.size
        val dtw = Array(n + 1) { DoubleArray(m + 1) { Double.MAX_VALUE } }

        dtw[0][0] = 0.0

        for (i in 1..n) {
            for (j in 1..m) {
                val cost = distance(route1Points[i - 1], route2Points[j - 1])
                dtw[i][j] = cost + min(dtw[i - 1][j], min(dtw[i][j - 1], dtw[i - 1][j - 1]))
            }
        }

        return dtw[n][m]
    }

    private fun distance(
        p1: RoutePoint,
        p2: RoutePoint,
    ): Double {
        val g1 = p1.pointGeom
        val g2 = p2.pointGeom
        if (g1 == null || g2 == null) return Double.MAX_VALUE

        // Use Euclidean distance for simplicity, or Haversine if needed.
        // JTS distance() on 4326 (geography) might be in degrees if not cast to geography,
        // but here we are likely dealing with projected or just raw coords.
        // Ideally we should project to meters, but for similarity ranking,
        // degree distance is often "good enough" if routes are in the same area.
        // However, to be more precise, we can use a simple Euclidean on lat/lon
        // or a Haversine formula. JTS distance on Point returns Euclidean distance.
        return g1.distance(g2)
    }
}
