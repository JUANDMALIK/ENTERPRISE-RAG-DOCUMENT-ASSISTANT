package com.axelor.ragassistant.document;

import java.io.IOException;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Component;

@Component
public class PdfTextExtractor {

    public ExtractedPdf extract(byte[] pdfBytes) throws IOException {
        try (PDDocument document = PDDocument.load(pdfBytes)) {
            PDFTextStripper stripper = new PDFTextStripper();
            String text = stripper.getText(document);
            return new ExtractedPdf(document.getNumberOfPages(), normalize(text));
        }
    }

    public String extractText(byte[] pdfBytes) throws IOException {
        return extract(pdfBytes).text();
    }

    private String normalize(String text) {
        return text == null ? "" : text.replace("\r\n", "\n").trim();
    }

    public record ExtractedPdf(int pageCount, String text) {
    }
}
