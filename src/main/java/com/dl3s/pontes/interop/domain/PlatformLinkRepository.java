package com.dl3s.pontes.interop.domain;

import java.util.List;

import org.springframework.data.jpa.repository.JpaRepository;

public interface PlatformLinkRepository extends JpaRepository<PlatformLink, Long> {

    boolean existsByPlatformAndParticipant(String platform, String participant);

    List<PlatformLink> findByPlatformOrderByParticipant(String platform);
}
