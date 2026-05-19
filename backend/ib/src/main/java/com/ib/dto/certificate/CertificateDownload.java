package com.ib.dto.certificate;

public record CertificateDownload(byte[] data, String contentType, String filename) {
}
