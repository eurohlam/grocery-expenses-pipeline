package org.roag.pipeline;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.roag.ocr.ReceiptOCR;
import org.roag.parser.ReceiptParser;

import javax.net.ssl.SSLContext;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.util.function.Consumer;

public class ReceiptPipeline {

    private static Logger LOG = LogManager.getLogger(ReceiptPipeline.class);

    private String sourceFile;
    private String restEndpoint;
    private ReceiptOCR receiptOCR = new ReceiptOCR();
    private ReceiptParser receiptParser = new ReceiptParser();


    public ReceiptPipeline withFile(String sourceFile) {
        this.sourceFile = sourceFile;
        return this;
    }

    public ReceiptPipeline withEndpoint(String restEndpoint) {
        this.restEndpoint = restEndpoint;
        return this;
    }

    public ReceiptPipeline process() {
        LOG.info("===> Started processing file {}", sourceFile);
        var ocrText = new ReceiptOCR()
                .withSource(sourceFile)
                .threshold(127)
                .ocr()
                .getOcrAsString();
        var json = new ReceiptParser()
                .withText(ocrText)
                .removeNoiseSymbols()
                .processMultilines()
                .parseAndConvertToTable()
                .toJson();
        LOG.info("  ===> Resulted JSON\n {}", json);
        LOG.info("===> Finished processing file {}", sourceFile);
        return this;
    }

    public ReceiptPipeline ocr(final Consumer<ReceiptOCR> consumer) {
        receiptOCR.withSource(sourceFile);
        consumer.accept(receiptOCR);
        return this;
    }

    public ReceiptPipeline parse(final Consumer<ReceiptParser> consumer) {
        consumer.accept(receiptParser);
        return this;
    }

    public ReceiptPipeline send(String json) {
        //TODO
        LOG.info("Sending json {} to REST-service", json);
        try {
            TrustManager[] trustAllCerts = new TrustManager[]{
                    new X509TrustManager() {
                        @Override
                        public java.security.cert.X509Certificate[] getAcceptedIssuers() {
                            return null;
                        }

                        @Override
                        public void checkClientTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                        }

                        @Override
                        public void checkServerTrusted(
                                java.security.cert.X509Certificate[] certs, String authType) {
                        }
                    }
            };

            SSLContext sslContext = SSLContext.getInstance("TLSv1.2");
            sslContext.init(null, trustAllCerts, new java.security.SecureRandom());

            HttpClient client = HttpClient.newBuilder()
                    .version(HttpClient.Version.HTTP_1_1)
                    .followRedirects(HttpClient.Redirect.NORMAL)
                    .connectTimeout(Duration.ofSeconds(20))
                    .sslContext(sslContext)
//                .proxy(ProxySelector.of(new InetSocketAddress("proxy.example.com", 80)))
//                .authenticator(Authenticator.getDefault())
                    .build();
            HttpRequest request = HttpRequest.newBuilder()
                    .uri(URI.create(restEndpoint))
                    .header("Content-Type", "application/json")
                    .POST(HttpRequest.BodyPublishers.ofString(json))
                    .build();
            HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString());
            LOG.info("Response from REST-service {} with statusCode={}", response.body(), response.statusCode());
        } catch (Exception e) {
            LOG.error("Failed to communicate with REST-service", e);
        }
        return this;
    }

}
