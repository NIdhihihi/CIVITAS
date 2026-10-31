package com.civitas.platform.modules.results;

import com.civitas.platform.common.CryptoUtils;
import com.civitas.platform.modules.audit.AuditService;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.encryption.AccessPermission;
import org.apache.pdfbox.pdmodel.encryption.StandardProtectionPolicy;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.apache.poi.poifs.crypt.EncryptionInfo;
import org.apache.poi.poifs.crypt.EncryptionMode;
import org.apache.poi.poifs.crypt.Encryptor;
import org.apache.poi.poifs.filesystem.POIFSFileSystem;
import org.apache.poi.ss.usermodel.*;
import org.apache.poi.xssf.usermodel.XSSFWorkbook;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.web.server.ResponseStatusException;

import java.io.ByteArrayOutputStream;
import java.io.OutputStream;
import java.time.Instant;
import java.util.List;
import java.util.Map;

@Service
public class ExportService {
    private final TallyService tally;
    private final AuditService audit;
    private final String masterSecret;

    public ExportService(TallyService tally, AuditService audit,
                         @Value("${civitas.security.master-secret}") String masterSecret) {
        this.tally = tally;
        this.audit = audit;
        this.masterSecret = masterSecret;
    }

    public ResultDtos.ExportFile export(java.util.UUID electionId, String format, String password) {
        if (password == null || password.length() < 8) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Use an export password of at least 8 characters.");
        }
        if (format == null || !(format.equalsIgnoreCase("pdf") || format.equalsIgnoreCase("excel")
                || format.equalsIgnoreCase("xlsx"))) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Choose pdf or excel format.");
        }
        ResultDtos.ResultsResponse results = tally.tally(electionId);
        byte[] bytes;
        String contentType;
        String extension;
        if ("excel".equalsIgnoreCase(format) || "xlsx".equalsIgnoreCase(format)) {
            bytes = excel(results, password);
            contentType = "application/vnd.openxmlformats-officedocument.spreadsheetml.sheet";
            extension = "xlsx";
        } else {
            bytes = pdf(results, password);
            contentType = "application/pdf";
            extension = "pdf";
        }
        String sha = CryptoUtils.sha256(bytes);
        String signature = CryptoUtils.hmacSha256(masterSecret, sha);
        String safeTitle = results.title().replaceAll("[^a-zA-Z0-9_-]+", "-").replaceAll("^-+|-+$", "");
        if (safeTitle.isBlank()) safeTitle = "election";
        String filename = "civitas-" + safeTitle + "-results." + extension;
        audit.record("RESULTS_EXPORTED", electionId.toString(), results.tenantId(),
                "ELECTION", electionId.toString(), Map.of("format", extension, "sha256", sha,
                        "resultHash", results.resultHash()));
        return new ResultDtos.ExportFile(filename, contentType, bytes, sha, signature);
    }

    private byte[] pdf(ResultDtos.ResultsResponse results, String password) {
        try (PDDocument document = new PDDocument()) {
            PDType1Font regular = new PDType1Font(Standard14Fonts.FontName.HELVETICA);
            PDType1Font bold = new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD);
            PDPage page = new PDPage(PDRectangle.A4);
            document.addPage(page);
            PDPageContentStream content = new PDPageContentStream(document, page);
            float y = 800;
            y = text(content, bold, 18, 50, y, "CIVITAS Election Results");
            y = text(content, bold, 11, 50, y - 6, "PASSWORD-PROTECTED | TAMPER-EVIDENT EXPORT");
            y = text(content, regular, 10, 50, y - 12, "Election: " + ascii(results.title()));
            y = text(content, regular, 10, 50, y, "Society: " + ascii(results.society()));
            y = text(content, regular, 10, 50, y, "Status: " + results.status() + " | Counted: "
                    + (results.countedAt() == null ? "not yet" : results.countedAt()));
            y = text(content, regular, 10, 50, y, "Ballots recorded: " + results.totalBallots()
                    + " | Eligible voters: " + results.eligibleVoters());
            y = text(content, regular, 9, 50, y, "Result SHA-256: " + results.resultHash());
            y = text(content, regular, 9, 50, y, "Result signature: " + results.resultSignature());
            y = text(content, regular, 9, 50, y, "Audit chain valid: " + results.auditChainValid());
            y -= 10;
            for (ResultDtos.PostResult post : results.posts()) {
                if (y < 90) {
                    content.close();
                    page = new PDPage(PDRectangle.A4);
                    document.addPage(page);
                    content = new PDPageContentStream(document, page);
                    y = 800;
                }
                y = text(content, bold, 12, 50, y, ascii(post.title()) + "  (" + post.totalVotes() + " votes)");
                if (post.candidates().isEmpty()) {
                    y = text(content, regular, 9, 62, y, "No individual candidate results.");
                }
                for (ResultDtos.ContestantResult candidate : post.candidates()) {
                    if (y < 70) {
                        content.close();
                        page = new PDPage(PDRectangle.A4);
                        document.addPage(page);
                        content = new PDPageContentStream(document, page);
                        y = 800;
                    }
                    y = text(content, regular, 10, 70, y, ascii(candidate.name()) + "  " + candidate.votes()
                            + " votes  (" + candidate.percentage() + "%)");
                }
                if (!post.panels().isEmpty()) {
                    y = text(content, bold, 10, 62, y, "Panel totals");
                    for (ResultDtos.ContestantResult panel : post.panels()) {
                        y = text(content, regular, 9, 70, y, ascii(panel.name()) + "  " + panel.votes() + " panel votes");
                    }
                }
                y -= 10;
            }
            if (y < 100) {
                content.close();
                page = new PDPage(PDRectangle.A4);
                document.addPage(page);
                content = new PDPageContentStream(document, page);
                y = 800;
            }
            y = text(content, regular, 8, 50, y - 10, "Export timestamp: " + Instant.now());
            y = text(content, regular, 8, 50, y, "The embedded digest and HMAC signature must verify against the audit ledger.");
            content.close();
            AccessPermission permission = new AccessPermission();
            permission.setCanModify(false);
            permission.setCanExtractContent(true);
            StandardProtectionPolicy policy = new StandardProtectionPolicy(password + "-owner", password, permission);
            policy.setEncryptionKeyLength(128);
            document.protect(policy);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            document.save(out);
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate the PDF report.", e);
        }
    }

    private float text(PDPageContentStream content, PDType1Font font, float size, float x, float y,
                       String value) throws Exception {
        content.beginText();
        content.setFont(font, size);
        content.newLineAtOffset(x, y);
        content.showText(value == null ? "" : value);
        content.endText();
        return y - (size + 5);
    }

    private byte[] excel(ResultDtos.ResultsResponse results, String password) {
        try (XSSFWorkbook workbook = new XSSFWorkbook()) {
            CellStyle header = workbook.createCellStyle();
            Font bold = workbook.createFont();
            bold.setBold(true);
            header.setFont(bold);
            Sheet summary = workbook.createSheet("Summary");
            int row = 0;
            row = row(summary, row, header, "CIVITAS Election Results", "Password-protected export");
            row = row(summary, row, header, "Election", results.title());
            row = row(summary, row, header, "Society", results.society());
            row = row(summary, row, header, "Status", results.status());
            row = row(summary, row, header, "Counted at", results.countedAt() == null ? "not yet" : results.countedAt());
            row = row(summary, row, header, "Ballots recorded", String.valueOf(results.totalBallots()));
            row = row(summary, row, header, "Eligible voters", String.valueOf(results.eligibleVoters()));
            row = row(summary, row, header, "Result SHA-256", results.resultHash());
            row = row(summary, row, header, "Result signature", results.resultSignature());
            row = row(summary, row, header, "Audit chain valid", String.valueOf(results.auditChainValid()));
            row = row(summary, row, header, "Export timestamp", Instant.now().toString());
            summary.setColumnWidth(0, 30 * 256);
            summary.setColumnWidth(1, 80 * 256);
            for (ResultDtos.PostResult post : results.posts()) {
                String sheetName = safeSheetName(post.title());
                Sheet sheet = workbook.createSheet(sheetName);
                int r = 0;
                r = row3(sheet, r, header, "Contestant", "Votes", "Share %");
                for (ResultDtos.ContestantResult candidate : post.candidates()) {
                    Row data = sheet.createRow(r++);
                    data.createCell(0).setCellValue(candidate.name());
                    data.createCell(1).setCellValue(candidate.votes());
                    data.createCell(2).setCellValue(candidate.percentage());
                }
                if (!post.panels().isEmpty()) {
                    r++;
                    r = row3(sheet, r, header, "Panel", "Panel votes", "Share %");
                    for (ResultDtos.ContestantResult panel : post.panels()) {
                        Row data = sheet.createRow(r++);
                        data.createCell(0).setCellValue(panel.name());
                        data.createCell(1).setCellValue(panel.votes());
                        data.createCell(2).setCellValue(panel.percentage());
                    }
                }
                sheet.setColumnWidth(0, 40 * 256);
                sheet.setColumnWidth(1, 14 * 256);
                sheet.setColumnWidth(2, 14 * 256);
                sheet.createFreezePane(0, 1);
            }
            POIFSFileSystem fs = new POIFSFileSystem();
            EncryptionInfo info = new EncryptionInfo(EncryptionMode.agile);
            Encryptor encryptor = info.getEncryptor();
            encryptor.confirmPassword(password);
            try (OutputStream encrypted = encryptor.getDataStream(fs)) {
                workbook.write(encrypted);
            }
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            fs.writeFilesystem(out);
            fs.close();
            return out.toByteArray();
        } catch (Exception e) {
            throw new IllegalStateException("Could not generate the Excel report.", e);
        }
    }

    private int row(Sheet sheet, int index, CellStyle style, String label, String value) {
        Row row = sheet.createRow(index);
        Cell first = row.createCell(0);
        first.setCellValue(label);
        first.setCellStyle(style);
        row.createCell(1).setCellValue(value);
        return index + 1;
    }

    private int row3(Sheet sheet, int index, CellStyle style, String firstValue, String secondValue, String thirdValue) {
        Row row = sheet.createRow(index);
        Cell first = row.createCell(0);
        first.setCellValue(firstValue);
        first.setCellStyle(style);
        row.createCell(1).setCellValue(secondValue);
        row.createCell(2).setCellValue(thirdValue);
        return index + 1;
    }

    private String safeSheetName(String value) {
        String cleaned = value.replaceAll("[\\\\/?*\\[\\]:]", "-");
        return cleaned.length() > 31 ? cleaned.substring(0, 31) : cleaned;
    }

    private String ascii(String value) {
        if (value == null) return "";
        return value.replaceAll("[^\\x20-\\x7E]", "?");
    }
}
