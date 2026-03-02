package com.biorad.csrag.evaluation;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.io.InputStream;
import java.util.List;

/**
 * classpath:golden-dataset.json 파일을 로드하는 골든 데이터셋 로더.
 */
public class GoldenDataset {

    private final List<GoldenCase> cases;

    private GoldenDataset(List<GoldenCase> cases) {
        this.cases = cases;
    }

    /**
     * classpath 루트의 golden-dataset.json 에서 데이터셋을 로드한다.
     */
    public static GoldenDataset load() {
        return load("golden-dataset.json");
    }

    /**
     * 지정된 classpath 경로에서 데이터셋을 로드한다.
     */
    public static GoldenDataset load(String classpathResource) {
        ObjectMapper mapper = new ObjectMapper();
        try (InputStream is = GoldenDataset.class.getClassLoader().getResourceAsStream(classpathResource)) {
            if (is == null) {
                throw new IllegalStateException("Golden dataset not found on classpath: " + classpathResource);
            }
            RawDataset raw = mapper.readValue(is, RawDataset.class);
            return new GoldenDataset(raw.cases());
        } catch (IOException e) {
            throw new IllegalStateException("Failed to load golden dataset: " + classpathResource, e);
        }
    }

    public List<GoldenCase> getCases() {
        return cases;
    }

    public int size() {
        return cases.size();
    }

    // ─── Internal deserialization target ───────────────────────────────────

    @JsonIgnoreProperties(ignoreUnknown = true)
    record RawDataset(List<GoldenCase> cases) {
        // Jackson 역직렬화용
    }

    // ─── Public domain model ────────────────────────────────────────────────

    /**
     * 골든 데이터셋의 단일 평가 케이스.
     *
     * @param id                고유 식별자 (예: "case-001")
     * @param question          사용자 질문
     * @param expectedAnswer    모범 답변 (정성적 참고용)
     * @param relevantDocuments 관련 문서 파일명 목록
     * @param groundTruthFacts  답변에 반드시 포함돼야 하는 사실 목록 (contextRecall 평가 기준)
     */
    @JsonIgnoreProperties(ignoreUnknown = true)
    public record GoldenCase(
            String id,
            String question,
            String expectedAnswer,
            List<String> relevantDocuments,
            List<String> groundTruthFacts
    ) {
    }
}
