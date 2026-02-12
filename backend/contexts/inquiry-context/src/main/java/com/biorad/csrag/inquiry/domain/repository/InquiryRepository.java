package com.biorad.csrag.inquiry.domain.repository;

import com.biorad.csrag.inquiry.domain.model.Inquiry;
import com.biorad.csrag.inquiry.domain.model.InquiryId;

import java.util.Optional;

public interface InquiryRepository {

    Inquiry save(Inquiry inquiry);

    Optional<Inquiry> findById(InquiryId inquiryId);
}
