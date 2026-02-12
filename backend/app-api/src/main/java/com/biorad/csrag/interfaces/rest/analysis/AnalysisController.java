package com.biorad.csrag.interfaces.rest.analysis;

import com.biorad.csrag.inquiry.domain.model.InquiryId;
import com.biorad.csrag.inquiry.domain.repository.InquiryRepository;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.UUID;

@RestController
@RequestMapping("/api/v1/inquiries/{inquiryId}/analysis")
public class AnalysisController {

    private final InquiryRepository inquiryRepository;
    private final AnalysisService analysisService;

    public AnalysisController(InquiryRepository inquiryRepository, AnalysisService analysisService) {
        this.inquiryRepository = inquiryRepository;
        this.analysisService = analysisService;
    }

    @PostMapping
    @ResponseStatus(HttpStatus.OK)
    public AnalyzeResponse analyze(
            @PathVariable String inquiryId,
            @Valid @RequestBody AnalyzeRequest request
    ) {
        UUID inquiryUuid = parseInquiryId(inquiryId);
        inquiryRepository.findById(new InquiryId(inquiryUuid))
                .orElseThrow(() -> new ResponseStatusException(HttpStatus.NOT_FOUND, "Inquiry not found"));

        int topK = request.topK() == null ? 5 : request.topK();
        return analysisService.analyze(inquiryUuid, request.question(), topK);
    }

    private UUID parseInquiryId(String inquiryId) {
        try {
            return UUID.fromString(inquiryId);
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid inquiryId format");
        }
    }
}
