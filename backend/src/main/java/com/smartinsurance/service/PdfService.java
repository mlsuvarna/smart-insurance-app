package com.smartinsurance.service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPage;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.apache.pdfbox.pdmodel.font.PDType1Font;
import org.apache.pdfbox.pdmodel.font.Standard14Fonts;
import org.springframework.stereotype.Service;

@Service
public class PdfService {
    public byte[] create(String title, List<String> lines) {
        try (PDDocument document = new PDDocument(); ByteArrayOutputStream output = new ByteArrayOutputStream()) {
            java.util.ArrayList<String> wrapped = new java.util.ArrayList<>();
            for (String rawLine : lines) wrapped.addAll(wrap(ascii(rawLine), 90));
            int lineOffset = 0;
            int pageNumber = 0;
            while (lineOffset < wrapped.size() || pageNumber == 0) {
                PDPage page = new PDPage(PDRectangle.LETTER);
                document.addPage(page);
                try (PDPageContentStream stream = new PDPageContentStream(document, page)) {
                    stream.beginText();
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA_BOLD), 16);
                    stream.newLineAtOffset(50, 740);
                    stream.showText(ascii(title));
                    stream.setFont(new PDType1Font(Standard14Fonts.FontName.HELVETICA), 10);
                    stream.newLineAtOffset(0, -28);
                    int pageEnd = Math.min(lineOffset + 42, wrapped.size());
                    while (lineOffset < pageEnd) {
                        String line = wrapped.get(lineOffset++);
                        stream.showText(line);
                        stream.newLineAtOffset(0, -16);
                    }
                    stream.endText();
                }
                pageNumber++;
            }
            document.save(output);
            return output.toByteArray();
        } catch (IOException ex) {
            throw new IllegalStateException("Unable to generate PDF document", ex);
        }
    }

    private String ascii(String value) {
        return value == null ? "" : value.replaceAll("[^\\x20-\\x7E]", "?");
    }

    private List<String> wrap(String text, int width) {
        if (text.length() <= width) return List.of(text);
        java.util.ArrayList<String> lines = new java.util.ArrayList<>();
        for (int start = 0; start < text.length(); start += width) {
            lines.add(text.substring(start, Math.min(start + width, text.length())));
        }
        return lines;
    }
}
