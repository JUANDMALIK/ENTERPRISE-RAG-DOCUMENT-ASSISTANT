package com.axelor.ragassistant.document;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.axelor.ragassistant.exception.RequestValidationException;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(DocumentUploadController.class)
class DocumentUploadControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentUploadService uploadService;

    @Test
    void uploadsDocumentAndReturnsTheIndexingResult() throws Exception {
        when(this.uploadService.upload(any())).thenReturn(
                new DocumentUploadService.UploadResult("doc-1", "guide.txt", 2));
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.txt", MediaType.TEXT_PLAIN_VALUE, "Some text".getBytes());

        this.mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.documentId").value("doc-1"))
                .andExpect(jsonPath("$.documentName").value("guide.txt"))
                .andExpect(jsonPath("$.chunkCount").value(2));

        verify(this.uploadService).upload(any());
    }

    @Test
    void returnsClientErrorForInvalidUploads() throws Exception {
        when(this.uploadService.upload(any()))
                .thenThrow(new RequestValidationException("Only PDF and TXT files are supported."));
        MockMultipartFile file = new MockMultipartFile(
                "file", "document.docx", MediaType.APPLICATION_OCTET_STREAM_VALUE, new byte[] {1});

        this.mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("Only PDF and TXT files are supported."));
    }

    @Test
    void doesNotExposeUnexpectedIllegalArgumentDetails() throws Exception {
        when(this.uploadService.upload(any()))
                .thenThrow(new IllegalArgumentException("internal parser detail"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.txt", MediaType.TEXT_PLAIN_VALUE, "Some text".getBytes());

        this.mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.message").value("The request could not be processed."));
    }

    @Test
    void reportsDocumentStorageFailuresAsServiceUnavailable() throws Exception {
        when(this.uploadService.upload(any()))
                .thenThrow(new DataAccessResourceFailureException("database unavailable"));
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.txt", MediaType.TEXT_PLAIN_VALUE, "Some text".getBytes());

        this.mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "Document storage is unavailable. Verify the PostgreSQL and pgvector configuration."));
    }

    @Test
    void reportsUploadCapacityAsRetryableServiceUnavailable() throws Exception {
        when(this.uploadService.upload(any())).thenThrow(new DocumentProcessingCapacityException());
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.txt", MediaType.TEXT_PLAIN_VALUE, "Some text".getBytes());

        this.mockMvc.perform(multipart("/api/documents").file(file))
                .andExpect(status().isServiceUnavailable())
                .andExpect(jsonPath("$.message").value(
                        "Document processing is busy. Please try again shortly."));
    }
}
