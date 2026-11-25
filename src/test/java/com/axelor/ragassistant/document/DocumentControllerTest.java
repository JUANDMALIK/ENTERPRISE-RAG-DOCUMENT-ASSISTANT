package com.axelor.ragassistant.document;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

@WebMvcTest(DocumentController.class)
class DocumentControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @MockitoBean
    private DocumentProcessingService documentProcessingService;

    @Test
    void uploadsPdfAndReturnsProcessingResult() throws Exception {
        DocumentProcessingService.ProcessedDocument result =
                new DocumentProcessingService.ProcessedDocument(
                        "doc-1", "guide.pdf", 2, 1500, 2, false);
        when(documentProcessingService.process(any())).thenReturn(result);
        MockMultipartFile file = new MockMultipartFile(
                "file", "guide.pdf", "application/pdf", "pdf-content".getBytes());

        mockMvc.perform(multipart("/api/documents/upload").file(file))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.documentId").value("doc-1"))
                .andExpect(jsonPath("$.pageCount").value(2))
                .andExpect(jsonPath("$.chunkCount").value(2));

        verify(documentProcessingService).process(any());
    }

    @Test
    void rejectsUploadWithoutFile() throws Exception {
        mockMvc.perform(multipart("/api/documents/upload"))
                .andExpect(status().isBadRequest());

        verifyNoInteractions(documentProcessingService);
    }
}
