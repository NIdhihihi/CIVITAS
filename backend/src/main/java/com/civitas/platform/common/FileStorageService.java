package com.civitas.platform.common;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.Locale;
import java.util.UUID;

@Service
public class FileStorageService {
    private final Path uploads;

    public FileStorageService(@Value("${civitas.uploads.dir:uploads}") String uploadsDir) {
        this.uploads = Path.of(uploadsDir).toAbsolutePath().normalize();
        try {
            Files.createDirectories(uploads);
        } catch (IOException e) {
            throw new IllegalStateException("Cannot create uploads directory: " + uploads, e);
        }
    }

    public Path root() {
        return uploads;
    }

    /** Accepts a data URL or raw base64 payload and returns a public /uploads/... path. */
    public String storeDataImage(String payload, String fallbackName) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        String mime = "image/jpeg";
        String base64 = payload.trim();
        if (base64.startsWith("data:")) {
            int comma = base64.indexOf(',');
            if (comma < 0) {
                throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Invalid image data URL.");
            }
            String header = base64.substring(5, comma);
            int semi = header.indexOf(';');
            mime = semi >= 0 ? header.substring(0, semi) : header;
            base64 = base64.substring(comma + 1);
        }
        String normalizedMime = mime.toLowerCase(Locale.ROOT).trim();
        if (!normalizedMime.startsWith("image/")) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Only image uploads are accepted.");
        }
        String extension = switch (normalizedMime) {
            case "image/png" -> ".png";
            case "image/webp" -> ".webp";
            case "image/gif" -> ".gif";
            case "image/jpeg", "image/jpg" -> ".jpg";
            default -> throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Unsupported image format: " + mime);
        };
        byte[] bytes;
        try {
            bytes = Base64.getDecoder().decode(base64.replaceAll("\\s", ""));
        } catch (IllegalArgumentException e) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "The image is not valid base64 data.");
        }
        if (bytes.length > 5 * 1024 * 1024) {
            throw new ResponseStatusException(HttpStatus.PAYLOAD_TOO_LARGE, "Images must be 5 MB or smaller.");
        }
        String name = (fallbackName == null || fallbackName.isBlank() ? "candidate" : fallbackName.replaceAll("[^a-zA-Z0-9_-]", "_"))
                + "-" + UUID.randomUUID() + extension;
        try {
            Files.write(uploads.resolve(name), bytes);
        } catch (IOException e) {
            throw new IllegalStateException("Could not save uploaded image.", e);
        }
        return "/uploads/" + name;
    }

    public static String safePhotoUrl(String url) {
        if (url == null || url.isBlank()) {
            return null;
        }
        String trimmed = url.trim();
        if (trimmed.startsWith("/uploads/") || trimmed.startsWith("http://") || trimmed.startsWith("https://")) {
            return trimmed;
        }
        throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Photo URL must start with /uploads/, http://, or https://.");
    }
}
