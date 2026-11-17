package com.axelor.ragassistant.document;

import java.io.IOException;
import java.io.Writer;
import java.nio.ByteBuffer;
import java.nio.charset.CharacterCodingException;
import java.nio.charset.CodingErrorAction;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Arrays;
import java.util.HexFormat;
import java.util.Locale;
import java.util.UUID;
import java.util.concurrent.Semaphore;
import java.util.concurrent.locks.ReentrantLock;

import org.apache.pdfbox.Loader;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

import com.axelor.ragassistant.exception.RequestValidationException;

@Service
public class DocumentUploadService {

    public static final long MAX_FILE_SIZE_BYTES = 10L * 1024 * 1024;
    public static final int MAX_PDF_PAGES = 100;
    private static final int MAX_EXTRACTED_TEXT_CHARACTERS = 5 * 1024 * 1024;
    private static final int PDF_HEADER_SEARCH_BYTES = 1024;
    private static final int MAX_CONCURRENT_UPLOADS = 2;
    private static final int DOCUMENT_LOCK_STRIPES = 64;

    private final DocumentIngestionService ingestionService;
    private final Semaphore uploadSlots = new Semaphore(MAX_CONCURRENT_UPLOADS);
    private final ReentrantLock[] documentLocks = new ReentrantLock[DOCUMENT_LOCK_STRIPES];

    public DocumentUploadService(DocumentIngestionService ingestionService) {
        this.ingestionService = ingestionService;
        Arrays.setAll(this.documentLocks, ignored -> new ReentrantLock());
    }

    public UploadResult upload(MultipartFile file) {
        if (file == null || file.isEmpty()) {
            throw new RequestValidationException("Select a non-empty PDF or TXT file.");
        }
        if (file.getSize() > MAX_FILE_SIZE_BYTES) {
            throw new RequestValidationException("The file must not exceed 10 MiB.");
        }

        String documentName = safeFileName(file.getOriginalFilename());
        if (!this.uploadSlots.tryAcquire()) {
            throw new DocumentProcessingCapacityException();
        }
        try {
            return processUpload(file, documentName);
        }
        finally {
            this.uploadSlots.release();
        }
    }

    private UploadResult processUpload(MultipartFile file, String documentName) {
        byte[] bytes;
        try {
            bytes = file.getBytes();
        }
        catch (IOException exception) {
            throw new RequestValidationException("The uploaded file could not be read.", exception);
        }
        if (bytes.length == 0) {
            throw new RequestValidationException("Select a non-empty PDF or TXT file.");
        }
        if (bytes.length > MAX_FILE_SIZE_BYTES) {
            throw new RequestValidationException("The file must not exceed 10 MiB.");
        }

        String content = extractContent(documentName, bytes).strip();
        if (!hasUsableText(content)) {
            throw new RequestValidationException("The file does not contain usable text.");
        }

        String contentSha256 = sha256(bytes);
        UUID documentId = UUID.nameUUIDFromBytes(
                (documentName + ":" + contentSha256).getBytes(StandardCharsets.UTF_8));
        ReentrantLock documentLock = this.documentLocks[
                (documentId.hashCode() & Integer.MAX_VALUE) % this.documentLocks.length];
        documentLock.lock();
        try {
            DocumentIngestionService.ExistingDocumentStatus existingDocument =
                    this.ingestionService.existingDocumentStatus(documentId, contentSha256);
            if (existingDocument.isComplete()) {
                return new UploadResult(
                        documentId.toString(), documentName, existingDocument.expectedChunkCount());
            }
            int chunkCount = this.ingestionService.ingest(documentId, documentName, content, contentSha256);
            return new UploadResult(documentId.toString(), documentName, chunkCount);
        }
        finally {
            documentLock.unlock();
        }
    }

    private static String extractContent(String documentName, byte[] bytes) {
        String extension = extensionOf(documentName);
        if ("txt".equals(extension)) {
            return decodeText(bytes);
        }
        if ("pdf".equals(extension)) {
            if (!hasPdfHeader(bytes)) {
                throw new RequestValidationException("The selected file is not a valid PDF.");
            }
            return extractPdf(bytes);
        }
        throw new RequestValidationException("Only PDF and TXT files are supported.");
    }

    private static String decodeText(byte[] bytes) {
        try {
            String text = StandardCharsets.UTF_8.newDecoder()
                    .onMalformedInput(CodingErrorAction.REPORT)
                    .onUnmappableCharacter(CodingErrorAction.REPORT)
                    .decode(ByteBuffer.wrap(bytes))
                    .toString();
            if (text.startsWith("\uFEFF")) {
                text = text.substring(1);
            }
            if (text.length() > MAX_EXTRACTED_TEXT_CHARACTERS) {
                throw new RequestValidationException("Extracted document text exceeds the supported size.");
            }
            if (text.codePoints().anyMatch(codePoint ->
                    Character.isISOControl(codePoint)
                            && codePoint != '\n'
                            && codePoint != '\r'
                            && codePoint != '\t'
                            && codePoint != '\f')) {
                throw new RequestValidationException("TXT files must contain readable UTF-8 text.");
            }
            return text;
        }
        catch (CharacterCodingException exception) {
            throw new RequestValidationException("TXT files must use UTF-8 encoding.", exception);
        }
    }

    private static String extractPdf(byte[] bytes) {
        try (PDDocument pdf = Loader.loadPDF(bytes)) {
            if (pdf.getNumberOfPages() > MAX_PDF_PAGES) {
                throw new RequestValidationException(
                        "PDF files must not contain more than " + MAX_PDF_PAGES + " pages.");
            }
            BoundedStringWriter text = new BoundedStringWriter(MAX_EXTRACTED_TEXT_CHARACTERS);
            new PDFTextStripper().writeText(pdf, text);
            return text.toString();
        }
        catch (ExtractedTextTooLargeException exception) {
            throw new RequestValidationException("Extracted PDF text exceeds the supported size.", exception);
        }
        catch (IOException exception) {
            throw new RequestValidationException("The PDF is invalid or could not be read.", exception);
        }
    }

    private static boolean hasPdfHeader(byte[] bytes) {
        int searchLimit = Math.min(bytes.length - 4, PDF_HEADER_SEARCH_BYTES);
        for (int index = 0; index < searchLimit; index++) {
            if (bytes[index] == '%'
                    && bytes[index + 1] == 'P'
                    && bytes[index + 2] == 'D'
                    && bytes[index + 3] == 'F'
                    && bytes[index + 4] == '-') {
                return true;
            }
        }
        return false;
    }

    private static String safeFileName(String originalFileName) {
        if (originalFileName == null || originalFileName.isBlank()) {
            throw new RequestValidationException("The uploaded file must have a filename.");
        }
        String normalized = originalFileName.trim().replace('\\', '/');
        normalized = normalized.substring(normalized.lastIndexOf('/') + 1);
        if (normalized.isBlank()
                || normalized.length() > 255
                || normalized.codePoints().anyMatch(Character::isISOControl)) {
            throw new RequestValidationException("The uploaded filename is invalid.");
        }
        return normalized;
    }

    private static String extensionOf(String fileName) {
        int extensionSeparator = fileName.lastIndexOf('.');
        if (extensionSeparator < 0 || extensionSeparator == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(extensionSeparator + 1).toLowerCase(Locale.ROOT);
    }

    private static boolean hasUsableText(String content) {
        return !content.isBlank()
                && content.codePoints().anyMatch(Character::isLetterOrDigit);
    }

    private static String sha256(byte[] bytes) {
        try {
            return HexFormat.of().formatHex(MessageDigest.getInstance("SHA-256").digest(bytes));
        }
        catch (NoSuchAlgorithmException exception) {
            throw new IllegalStateException("SHA-256 is not available in this Java runtime.", exception);
        }
    }

    public record UploadResult(String documentId, String documentName, int chunkCount) {
    }

    private static final class BoundedStringWriter extends Writer {

        private final int maximumCharacters;
        private final StringBuilder content = new StringBuilder();

        private BoundedStringWriter(int maximumCharacters) {
            this.maximumCharacters = maximumCharacters;
        }

        @Override
        public void write(char[] characters, int offset, int length) throws IOException {
            if (length > this.maximumCharacters - this.content.length()) {
                throw new ExtractedTextTooLargeException();
            }
            this.content.append(characters, offset, length);
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }

        @Override
        public String toString() {
            return this.content.toString();
        }
    }

    private static final class ExtractedTextTooLargeException extends IOException {
    }
}
