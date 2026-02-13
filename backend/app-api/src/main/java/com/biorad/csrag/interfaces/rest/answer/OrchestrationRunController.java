package com.biorad.csrag.interfaces.rest.answer;

import com.biorad.csrag.infrastructure.persistence.orchestration.OrchestrationRunJpaRepository;
import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inquiries/{inquiryId}/orchestration-runs")
public class OrchestrationRunController {

    private final InquiryRepository inquiryRepository;
    private final OrchestrationRunJpaRepository runRepository;

    public OrchestrationRunController(InquiryRepository inquiryRepository, OrchestrationRunJpaRepository runRepository) {
        this.inquiryRepository = inquiryRepository;
        this.runRepository = runRepository;
    }

    @GetMapping
    @ResponseStatus(HttpStatus.OK)
    public List<OrchestrationRunResponse> list(@PathVariable String inquiryId) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        inquiryRepository.findById(new InquiryId(inquiryUuid))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));

        return runRepository.findByInquiryIdOrderByCreatedAtDesc(inquiryUuid)
                .stream()
                .map(r -> new OrchestrationRunResponse(
                        r.getId().toString(),
                        r.getInquiryId().toString(),
                        r.getStep(),
                        r.getStatus(),
                        r.getLatencyMs(),
                        r.getErrorMessage(),
                        r.getCreatedAt()
                ))
                .toList();
    }

    private UUID parseInquiryId(String inquiryId) {
        try {
            return UUID.fromString(inquiryId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid inquiryId format");
        }
    }
}
