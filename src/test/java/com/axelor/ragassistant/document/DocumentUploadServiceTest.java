package com.axelor.ragassistant.document;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockMultipartFile;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class DocumentUploadServiceTest {

    private final DocumentIngestionService ingestionService = mock(DocumentIngestionService.class);
    private final DocumentUploadService service = new DocumentUploadService(this.ingestionService);

    @BeforeEach
    void setUp() {
        when(this.ingestionService.existingDocumentStatus(any(UUID.class), anyString()))
                .thenReturn(new DocumentIngestionService.ExistingDocumentStatus(0, 0));
    }

    @Test
    void extractsAndIndexesUtf8Text() {
        MockMultipartFile file = new MockMultipartFile(
                "file",
                "policies\\leave-policy.txt",
                "text/plain",
                "Employees receive 20 days of annual leave.".getBytes(StandardCharsets.UTF_8));
        when(this.ingestionService.ingest(any(UUID.class), eq("leave-policy.txt"), anyString(), anyString()))
                .thenReturn(3);

        DocumentUploadService.UploadResult result = this.service.upload(file);

        assertEquals("leave-policy.txt", result.documentName());
        assertEquals(3, result.chunkCount());
        verify(this.ingestionService).ingest(
                any(UUID.class),
                eq("leave-policy.txt"),
                eq("Employees receive 20 days of annual leave."),
                anyString());
    }

    @Test
    void extractsAndIndexesPdfText() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.pdf", "application/pdf", createPdf("PDF policy details."));
        when(this.ingestionService.ingest(any(UUID.class), eq("guide.pdf"), anyString(), anyString()))
                .thenReturn(1);

        DocumentUploadService.UploadResult result = this.service.upload(file);

        assertEquals(1, result.chunkCount());
        verify(this.ingestionService).ingest(
                any(UUID.class), eq("guide.pdf"), eq("PDF policy details."), anyString());
    }

    @Test
    void skipsEmbeddingWhenTheExactDocumentIsAlreadyIndexed() {
        MockMultipartFile first = textFile("guide.txt", "same document");
        MockMultipartFile second = textFile("guide.txt", "same document");
        when(this.ingestionService.existingDocumentStatus(any(UUID.class), anyString()))
                .thenReturn(
                        new DocumentIngestionService.ExistingDocumentStatus(0, 0),
                        new DocumentIngestionService.ExistingDocumentStatus(2, 2));
        when(this.ingestionService.ingest(any(UUID.class), anyString(), anyString(), anyString()))
                .thenReturn(1);

        String firstId = this.service.upload(first).documentId();
        String secondId = this.service.upload(second).documentId();

        assertEquals(firstId, secondId);
        verify(this.ingestionService)
                .ingest(any(UUID.class), eq("guide.txt"), eq("same document"), anyString());
        verify(this.ingestionService, org.mockito.Mockito.times(2))
                .existingDocumentStatus(any(UUID.class), anyString());
    }

    @Test
    void rejectsEmptyUnsupportedOversizedAndMalformedFiles() {
        assertThrows(IllegalArgumentException.class, () -> this.service.upload(
                new MockMultipartFile("file", "empty.txt", "text/plain", new byte[0])));
        assertThrows(IllegalArgumentException.class, () -> this.service.upload(
                new MockMultipartFile("file", "document.docx", "application/octet-stream", new byte[] {1})));
        assertThrows(IllegalArgumentException.class, () -> this.service.upload(
                new MockMultipartFile(
                        "file",
                        "large.txt",
                        "text/plain",
                        new byte[(int) DocumentUploadService.MAX_FILE_SIZE_BYTES + 1])));
        assertThrows(IllegalArgumentException.class, () -> this.service.upload(
                new MockMultipartFile("file", "invalid.pdf", "application/pdf", "%PDF-invalid".getBytes(
                        StandardCharsets.US_ASCII))));
        assertThrows(IllegalArgumentException.class, () -> this.service.upload(
                new MockMultipartFile("file", "invalid.txt", "text/plain", new byte[] {(byte) 0xC3, 0x28})));
        assertThrows(IllegalArgumentException.class, () -> this.service.upload(
                new MockMultipartFile(
                        "file", "binary.txt", "text/plain", new byte[] {(byte) 'a', 0, (byte) 'b'})));

        org.mockito.Mockito.verifyNoInteractions(this.ingestionService);
    }

    @Test
    void rejectsPdfFilesAboveThePageLimit() throws IOException {
        MockMultipartFile file = new MockMultipartFile(
                "file", "too-many-pages.pdf", "application/pdf", createPdf("page", DocumentUploadService.MAX_PDF_PAGES + 1));

        assertThrows(IllegalArgumentException.class, () -> this.service.upload(file));
        org.mockito.Mockito.verifyNoInteractions(this.ingestionService);
    }

    @Test
    void rejectsTextWithoutUsableContent() {
        assertThrows(IllegalArgumentException.class,
                () -> this.service.upload(textFile("punctuation.txt", " \t--...")));
        org.mockito.Mockito.verifyNoInteractions(this.ingestionService);
    }

    @Test
    void limitsConcurrentUploadProcessing() throws Exception {
        CountDownLatch processingStarted = new CountDownLatch(2);
        CountDownLatch allowProcessing = new CountDownLatch(1);
        when(this.ingestionService.existingDocumentStatus(any(UUID.class), anyString())).thenAnswer(invocation -> {
            processingStarted.countDown();
            if (!allowProcessing.await(5, TimeUnit.SECONDS)) {
                throw new IllegalStateException("Test upload timed out.");
            }
            return new DocumentIngestionService.ExistingDocumentStatus(0, 0);
        });
        ExecutorService executor = Executors.newFixedThreadPool(2);
        try {
            Future<DocumentUploadService.UploadResult> first =
                    executor.submit(() -> this.service.upload(textFile("first.txt", "first")));
            Future<DocumentUploadService.UploadResult> second =
                    executor.submit(() -> this.service.upload(textFile("second.txt", "second")));

            assertTrue(processingStarted.await(5, TimeUnit.SECONDS));
            assertThrows(DocumentProcessingCapacityException.class,
                    () -> this.service.upload(textFile("third.txt", "third")));

            allowProcessing.countDown();
            first.get(5, TimeUnit.SECONDS);
            second.get(5, TimeUnit.SECONDS);
        }
        finally {
            allowProcessing.countDown();
            executor.shutdownNow();
        }
    }

    private static MockMultipartFile textFile(String name, String content) {
        return new MockMultipartFile(
                "file", name, "text/plain", content.getBytes(StandardCharsets.UTF_8));
    }

    private static byte[] createPdf(String content) throws IOException {
        return createPdf(content, 1);
    }

    private static byte[] createPdf(String content, int pageCount) throws IOException {
        try (PDDocument pdf = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            for (int pageIndex = 0; pageIndex < pageCount; pageIndex++) {
                pdf.addPage(new PDPage());
            }
            try (PDPageContentStream stream = new PDPageContentStream(pdf, pdf.getPage(0))) {
                stream.beginText();
                stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 12);
                stream.newLineAtOffset(25, 700);
                stream.showText(content);
                stream.endText();
            }
            pdf.save(output);
            return output.toByteArray();
        }
    }
}
