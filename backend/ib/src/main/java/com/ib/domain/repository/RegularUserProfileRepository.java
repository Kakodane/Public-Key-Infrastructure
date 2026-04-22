package com.ib.domain.repository;

import com.ib.domain.entity.RegularUserProfile;
import org.springframework.data.jpa.repository.JpaRepository;

public interface RegularUserProfileRepository extends JpaRepository<RegularUserProfile, Long> {
}
