package com.axelor.ragassistant.document;

import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestPart;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@RestController
@RequestMapping("/api/documents")
public class DocumentUploadController {

    private final DocumentUploadService uploadService;

    public DocumentUploadController(DocumentUploadService uploadService) {
        this.uploadService = uploadService;
    }

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<UploadResponse> upload(@RequestPart("file") MultipartFile file) {
        DocumentUploadService.UploadResult result = this.uploadService.upload(file);
        return ResponseEntity.status(HttpStatus.CREATED)
                .body(new UploadResponse(
                        result.documentId(),
                        result.documentName(),
                        result.chunkCount(),
                        "Document uploaded and indexed successfully."));
    }

    public record UploadResponse(String documentId, String documentName, int chunkCount, String message) {
    }
}
