package com.runwithme.runwithme.api.repository

import com.runwithme.runwithme.api.entity.RunSessionPoint
import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.stereotype.Repository

@Repository
interface RunSessionPointRepository : JpaRepository<RunSessionPoint, Long> {
    fun findByRunSessionIdOrderBySeqNoAsc(runSessionId: Long): List<RunSessionPoint>

    fun deleteByRunSessionId(runSessionId: Long)

    @Query("SELECT COALESCE(MAX(p.seqNo), 0) FROM RunSessionPoint p WHERE p.runSessionId = :runSessionId")
    fun findMaxSeqNoByRunSessionId(runSessionId: Long): Int

    fun countByRunSessionId(runSessionId: Long): Long
}
