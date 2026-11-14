package com.swiftdrop.merchant.dto.request;

import com.swiftdrop.merchant.entity.enums.DocumentType;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;

public class UploadDocumentRequest {

    @NotNull(message = "Document type is required")
    private DocumentType documentType;

    @NotBlank(message = "File name is required")
    @Size(max = 255)
    private String fileName;

    @NotBlank(message = "File URL is required")
    @Size(max = 1024)
    private String fileUrl;

    private Long fileSizeBytes;

    @NotBlank(message = "MIME type is required (e.g. application/pdf, image/jpeg)")
    @Size(max = 100)
    private String mimeType;

    public UploadDocumentRequest() {
    }

    public DocumentType getDocumentType() {
        return documentType;
    }

    public void setDocumentType(DocumentType documentType) {
        this.documentType = documentType;
    }

    public String getFileName() {
        return fileName;
    }

    public void setFileName(String fileName) {
        this.fileName = fileName;
    }

    public String getFileUrl() {
        return fileUrl;
    }

    public void setFileUrl(String fileUrl) {
        this.fileUrl = fileUrl;
    }

    public Long getFileSizeBytes() {
        return fileSizeBytes;
    }

    public void setFileSizeBytes(Long fileSizeBytes) {
        this.fileSizeBytes = fileSizeBytes;
    }

    public String getMimeType() {
        return mimeType;
    }

    public void setMimeType(String mimeType) {
        this.mimeType = mimeType;
    }
}
