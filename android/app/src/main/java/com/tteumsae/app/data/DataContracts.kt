package com.tteumsae.app.data

import com.tteumsae.app.domain.PlaceCandidate
import com.tteumsae.app.domain.SearchCriteria

/**
 * TourAPI 동기화 DB 또는 서버 API가 구현할 장소 조회 계약.
 * UI는 TourAPI 응답 형식을 직접 알지 않는다.
 */
interface PlaceRepository {
    suspend fun findCandidates(criteria: SearchCriteria): List<PlaceCandidate>
}

/**
 * 카카오모빌리티 또는 다른 경로 제공자가 구현할 이동시간 계약.
 * 장소별 출발·도착 구간을 한 번에 계산하도록 배치 호출을 우선한다.
 */
interface RouteTimeProvider {
    suspend fun calculateRoutes(
        criteria: SearchCriteria,
        candidates: List<PlaceCandidate>,
    ): List<PlaceCandidate>
}

class SamplePlaceRepository : PlaceRepository {
    override suspend fun findCandidates(criteria: SearchCriteria): List<PlaceCandidate> {
        return SamplePlaces.forMode(criteria.mode)
    }
}

class SampleRouteTimeProvider : RouteTimeProvider {
    override suspend fun calculateRoutes(
        criteria: SearchCriteria,
        candidates: List<PlaceCandidate>,
    ): List<PlaceCandidate> = candidates
}

