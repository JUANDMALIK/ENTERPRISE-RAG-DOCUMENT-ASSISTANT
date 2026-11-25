package com.axelor.ragassistant.document;

import java.io.IOException;
import java.util.UUID;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class DocumentProcessingService {

    private final PdfTextExtractor pdfTextExtractor;
    private final DocumentIngestionService documentIngestionService;

    public DocumentProcessingService(PdfTextExtractor pdfTextExtractor,
                                     DocumentIngestionService documentIngestionService) {
        this.pdfTextExtractor = pdfTextExtractor;
        this.documentIngestionService = documentIngestionService;
    }

    public ProcessedDocument process(MultipartFile file) {
        validate(file);

        PdfTextExtractor.ExtractedPdf extracted;
        try {
            extracted = pdfTextExtractor.extract(file.getBytes());
        } catch (IOException exception) {
            throw new IllegalArgumentException("Unable to read the uploaded PDF.", exception);
        }
        if (extracted.text().isBlank()) {
            throw new IllegalArgumentException(
                    "The PDF contains no extractable text. Scanned PDFs may require OCR.");
        }

        String documentId = UUID.randomUUID().toString();
        String fileName = file.getOriginalFilename();
        if (fileName == null || fileName.isBlank()) {
            fileName = "uploaded.pdf";
        }
        int chunkCount = documentIngestionService.ingest(documentId, fileName, extracted.text());

        return new ProcessedDocument(
                documentId,
                fileName,
                extracted.pageCount(),
                extracted.text().length(),
                chunkCount,
                true);
    }

    private void validate(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new IllegalArgumentException("A PDF file is required.");
        }

        String fileName = file.getOriginalFilename();
        boolean extensionPdf = fileName != null
                && fileName.toLowerCase(java.util.Locale.ROOT).endsWith(".pdf");
        boolean contentTypePdf = "application/pdf".equalsIgnoreCase(file.getContentType());
        if (!extensionPdf && !contentTypePdf) {
            throw new IllegalArgumentException("Only PDF files are supported.");
        }
    }

    public record ProcessedDocument(
            String documentId,
            String fileName,
            int pageCount,
            int characterCount,
            int chunkCount,
            boolean indexed) {
    }
}
