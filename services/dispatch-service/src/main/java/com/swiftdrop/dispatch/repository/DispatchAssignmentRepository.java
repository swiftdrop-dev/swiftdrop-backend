package com.swiftdrop.dispatch.repository;

import com.swiftdrop.dispatch.entity.DispatchAssignment;
import com.swiftdrop.dispatch.entity.enums.AssignmentDecision;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface DispatchAssignmentRepository extends JpaRepository<DispatchAssignment, UUID> {

    List<DispatchAssignment> findByDispatchJobIdOrderByAttemptNumberDesc(UUID dispatchJobId);

    List<DispatchAssignment> findByCourierIdOrderByOfferedAtDesc(UUID courierId);

    Optional<DispatchAssignment> findFirstByDispatchJobIdAndDecisionOrderByAttemptNumberDesc(
            UUID dispatchJobId, AssignmentDecision decision);
}
