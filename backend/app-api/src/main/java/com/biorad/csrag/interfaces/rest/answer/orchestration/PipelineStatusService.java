package com.biorad.csrag.interfaces.rest.answer.orchestration;

import com.biorad.csrag.infrastructure.persistence.pipeline.PipelineExecutionJpaEntity;
import com.biorad.csrag.infrastructure.persistence.pipeline.PipelineExecutionJpaRepository;
import com.biorad.csrag.infrastructure.persistence.pipeline.PipelineStepJpaEntity;
import com.biorad.csrag.infrastructure.persistence.pipeline.PipelineStepJpaRepository;
import com.biorad.csrag.interfaces.rest.answer.PipelineStatusResponse;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class PipelineStatusService {

    private static final Logger log = LoggerFactory.getLogger(PipelineStatusService.class);
    private static final long TIMEOUT_MINUTES = 30;
    private static final List<String> ALL_STEPS = List.of(
        "DECOMPOSE", "RETRIEVE", "ADAPTIVE_RETRIEVE", "MULTI_HOP",
        "VERIFY", "COMPOSE", "CRITIC", "SELF_REVIEW"
    );

    private final PipelineExecutionJpaRepository executionRepository;
    private final PipelineStepJpaRepository stepRepository;

    public PipelineStatusService(PipelineExecutionJpaRepository executionRepository,
                                  PipelineStepJpaRepository stepRepository) {
        this.executionRepository = executionRepository;
        this.stepRepository = stepRepository;
    }

    @Transactional
    public PipelineExecutionJpaEntity startExecution(UUID inquiryId) {
        // 기존 GENERATING execution을 FAILED로 강제 변경
        List<PipelineExecutionJpaEntity> existing =
                executionRepository.findByInquiryIdAndStatus(inquiryId, "GENERATING");
        for (PipelineExecutionJpaEntity old : existing) {
            old.markFailed("Superseded by new execution");
            executionRepository.save(old);
        }

        // 새 execution 생성
        PipelineExecutionJpaEntity execution =
                new PipelineExecutionJpaEntity(UUID.randomUUID(), inquiryId);
        executionRepository.save(execution);

        // 각 단계에 대해 PENDING step 생성
        for (String stepName : ALL_STEPS) {
            PipelineStepJpaEntity step =
                    new PipelineStepJpaEntity(UUID.randomUUID(), execution.getId(), stepName);
            stepRepository.save(step);
        }

        log.info("pipeline.execution.started inquiryId={} executionId={}", inquiryId, execution.getId());
        return execution;
    }

    @Transactional
    public void updateStep(UUID inquiryId, String stepName, String status, String message) {
        executionRepository.findTopByInquiryIdOrderByCreatedAtDesc(inquiryId)
                .ifPresent(execution -> {
                    if (!"GENERATING".equals(execution.getStatus())) {
                        return;
                    }
                    stepRepository.findByExecutionIdAndStepName(execution.getId(), stepName)
                            .ifPresent(step -> {
                                step.updateStatus(status, message);
                                stepRepository.save(step);
                            });
                });
    }

    @Transactional
    public void completeExecution(UUID inquiryId) {
        executionRepository.findTopByInquiryIdOrderByCreatedAtDesc(inquiryId)
                .ifPresent(execution -> {
                    execution.markCompleted();
                    executionRepository.save(execution);
                    log.info("pipeline.execution.completed inquiryId={} executionId={}",
                            inquiryId, execution.getId());
                });
    }

    @Transactional
    public void failExecution(UUID inquiryId, String error) {
        executionRepository.findTopByInquiryIdOrderByCreatedAtDesc(inquiryId)
                .ifPresent(execution -> {
                    execution.markFailed(error);
                    executionRepository.save(execution);
                    log.warn("pipeline.execution.failed inquiryId={} executionId={} error={}",
                            inquiryId, execution.getId(), error);
                });
    }

    @Transactional
    public PipelineStatusResponse getStatus(UUID inquiryId) {
        return executionRepository.findTopByInquiryIdOrderByCreatedAtDesc(inquiryId)
                .map(execution -> {
                    // 30분 타임아웃 체크
                    if ("GENERATING".equals(execution.getStatus())
                            && Duration.between(execution.getStartedAt(), Instant.now()).toMinutes() >= TIMEOUT_MINUTES) {
                        execution.markFailed("Pipeline execution timed out after " + TIMEOUT_MINUTES + " minutes");
                        executionRepository.save(execution);
                    }

                    List<PipelineStatusResponse.PipelineStepResponse> steps =
                            stepRepository.findByExecutionIdOrderByUpdatedAtAsc(execution.getId())
                                    .stream()
                                    .map(step -> new PipelineStatusResponse.PipelineStepResponse(
                                            step.getStepName(),
                                            step.getStatus(),
                                            step.getMessage(),
                                            step.getUpdatedAt() != null ? step.getUpdatedAt().toString() : null
                                    ))
                                    .toList();

                    return new PipelineStatusResponse(
                            execution.getStatus(),
                            execution.getStartedAt() != null ? execution.getStartedAt().toString() : null,
                            execution.getErrorMessage(),
                            steps
                    );
                })
                .orElse(new PipelineStatusResponse("IDLE", null, null, List.of()));
    }
}
