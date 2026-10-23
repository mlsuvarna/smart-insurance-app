package com.smartinsurance.service;

import com.smartinsurance.enums.DocumentScanStatus;
import java.io.ByteArrayOutputStream;
import java.io.DataOutputStream;
import java.io.InputStream;
import java.io.IOException;
import java.net.InetSocketAddress;
import java.net.Socket;
import java.nio.charset.StandardCharsets;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(name = "app.scanner.mode", havingValue = "clamav")
public class ClamAvDocumentScanner implements DocumentScanner {
    private final String host;
    private final int port;
    public ClamAvDocumentScanner(@Value("${SCANNER_HOST:localhost}") String host,
                                 @Value("${SCANNER_PORT:3310}") int port) {
        this.host = host; this.port = port;
    }
    @Override
    public ScanResult scan(byte[] content) {
        try (Socket socket = new Socket();
             ByteArrayOutputStream response = new ByteArrayOutputStream()) {
            socket.connect(new InetSocketAddress(host, port), 3000);
            socket.setSoTimeout(10000);
            DataOutputStream output = new DataOutputStream(socket.getOutputStream());
            InputStream input = socket.getInputStream();
            output.write("zINSTREAM\0".getBytes(StandardCharsets.US_ASCII));
            int offset = 0;
            while (offset < content.length) {
                int length = Math.min(8192, content.length - offset);
                output.writeInt(length);
                output.write(content, offset, length);
                offset += length;
            }
            output.writeInt(0);
            output.flush();
            int next;
            while ((next = input.read()) != -1 && next != 0 && next != '\n') response.write(next);
            String result = response.toString(StandardCharsets.US_ASCII).trim();
            if (result.endsWith("OK")) return new ScanResult(DocumentScanStatus.CLEAN, "CLAMAV");
            if (result.contains("FOUND")) return new ScanResult(DocumentScanStatus.INFECTED, "CLAMAV");
            return new ScanResult(DocumentScanStatus.FAILED, "CLAMAV");
        } catch (IOException ex) {
            throw new IllegalStateException("ClamAV scan service is unavailable", ex);
        }
    }
}
