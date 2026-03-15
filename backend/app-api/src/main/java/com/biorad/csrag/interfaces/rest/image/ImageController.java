package com.biorad.csrag.interfaces.rest.image;

import com.biorad.csrag.common.exception.ValidationException;
import com.biorad.csrag.infrastructure.persistence.image.ImageJpaEntity;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.core.io.Resource;
import org.springframework.http.CacheControl;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

import java.util.UUID;
import java.util.concurrent.TimeUnit;

@Tag(name = "Image", description = "이미지 업로드/서빙 API")
@RestController
@RequestMapping("/api/v1/images")
public class ImageController {

    private final ImageStorageService imageStorageService;

    public ImageController(ImageStorageService imageStorageService) {
        this.imageStorageService = imageStorageService;
    }

    @Operation(summary = "이미지 업로드", description = "PNG/JPEG 이미지를 업로드합니다 (최대 5MB)")
    @ApiResponse(responseCode = "201", description = "업로드 성공")
    @ApiResponse(responseCode = "400", description = "잘못된 파일 형식 또는 크기 초과")
    @PostMapping("/upload")
    @ResponseStatus(HttpStatus.CREATED)
    public ImageUploadResponse upload(
            @Parameter(description = "이미지 파일 (PNG/JPEG, 최대 5MB)")
            @RequestParam("file") MultipartFile file,
            @Parameter(description = "연결할 문의 ID (선택)")
            @RequestParam(value = "inquiryId", required = false) UUID inquiryId
    ) {
        return imageStorageService.upload(file, inquiryId);
    }

    @Operation(summary = "이미지 조회", description = "업로드된 이미지를 바이너리로 반환합니다")
    @ApiResponse(responseCode = "200", description = "이미지 반환 성공")
    @ApiResponse(responseCode = "404", description = "이미지를 찾을 수 없음")
    @GetMapping("/{imageId}.{ext}")
    public ResponseEntity<Resource> getImage(
            @Parameter(description = "이미지 ID (UUID)")
            @PathVariable String imageId,
            @Parameter(description = "파일 확장자 (png, jpg)")
            @PathVariable String ext
    ) {
        UUID id = parseImageId(imageId);
        ImageJpaEntity entity = imageStorageService.getImageEntity(id);
        Resource resource = imageStorageService.getImageResource(id);

        MediaType mediaType = resolveMediaType(entity.getContentType());

        return ResponseEntity.ok()
                .contentType(mediaType)
                .cacheControl(CacheControl.maxAge(365, TimeUnit.DAYS).cachePublic())
                .header(HttpHeaders.CONTENT_DISPOSITION, "inline")
                .body(resource);
    }

    private MediaType resolveMediaType(String contentType) {
        return switch (contentType) {
            case "image/png" -> MediaType.IMAGE_PNG;
            case "image/jpeg" -> MediaType.IMAGE_JPEG;
            default -> MediaType.APPLICATION_OCTET_STREAM;
        };
    }

    private UUID parseImageId(String imageId) {
        try {
            return UUID.fromString(imageId);
        } catch (IllegalArgumentException e) {
            throw new ValidationException("INVALID_IMAGE_ID", "Invalid imageId format");
        }
    }
}
