package io.github.timekillr.valueforge.companion;

import org.json.JSONObject;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLSocket;
import javax.net.ssl.TrustManager;
import javax.net.ssl.X509TrustManager;
import java.io.BufferedInputStream;
import java.io.ByteArrayOutputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.cert.CertificateException;
import java.security.cert.X509Certificate;
import java.util.Locale;

final class Transport {
    static final class Failure extends Exception {
        final int status;
        Failure(int status, String message) { super(message); this.status = status; }
    }
    static String hash(byte[] data) throws Exception {
        byte[] digest = MessageDigest.getInstance("SHA-256").digest(data);
        StringBuilder hex = new StringBuilder();
        for (byte b : digest) hex.append(String.format(Locale.ROOT, "%02x", b & 255));
        return hex.toString();
    }
    static String shortFingerprint(String pin) {
        return pin.substring(0, 20).toUpperCase(Locale.ROOT).replaceAll("(.{4})(?!$)", "$1 ");
    }
    private static SSLSocket connect(Endpoint endpoint, String pin) throws Exception {
        SSLContext context = SSLContext.getInstance("TLS");
        context.init(null, new TrustManager[]{new X509TrustManager() {
            public X509Certificate[] getAcceptedIssuers() { return new X509Certificate[0]; }
            public void checkClientTrusted(X509Certificate[] chain, String auth) throws CertificateException { throw new CertificateException(); }
            public void checkServerTrusted(X509Certificate[] chain, String auth) throws CertificateException {
                try {
                    chain[0].checkValidity();
                    if (pin != null && !MessageDigest.isEqual(pin.getBytes(StandardCharsets.US_ASCII), hash(chain[0].getEncoded()).getBytes(StandardCharsets.US_ASCII)))
                        throw new CertificateException("The Steam Machine fingerprint changed. Check the plugin before pairing again.");
                } catch (Exception error) { throw new CertificateException(error); }
            }
        }}, null);
        SSLSocket socket = (SSLSocket) context.getSocketFactory().createSocket();
        socket.setSoTimeout(8000);
        try {
            socket.connect(new InetSocketAddress(endpoint.host, endpoint.port), 4000);
            socket.startHandshake();
            return socket;
        } catch (Exception error) { socket.close(); throw error; }
    }
    /** Trust-once probe sends only public /info, never pairing codes or tokens. */
    static JSONObject probe(Endpoint endpoint) throws Exception {
        try (SSLSocket socket = connect(endpoint, null)) {
            String pin = hash(socket.getSession().getPeerCertificates()[0].getEncoded());
            JSONObject info = exchange(socket, endpoint, "GET", "/v1/info", null, null);
            if (info.optInt("protocol") != 1 || !info.optString("name").equals("Value Forge"))
                throw new Failure(400, "This address is not a compatible Value Forge plugin.");
            info.put("fingerprint", pin);
            return info;
        }
    }
    static JSONObject request(Endpoint endpoint, String pin, String token, String method, String path, JSONObject body) throws Exception {
        if (pin == null || pin.length() != 64) throw new Failure(401, "Pair this phone first.");
        try (SSLSocket socket = connect(endpoint, pin)) {
            socket.setSoTimeout(path.equals("/v1/command") ? 200000 : 8000);
            return exchange(socket, endpoint, method, path, token, body);
        }
    }
    private static JSONObject exchange(SSLSocket socket, Endpoint endpoint, String method, String path, String token, JSONObject body) throws Exception {
        byte[] payload = body == null ? new byte[0] : body.toString().getBytes(StandardCharsets.UTF_8);
        String headers = method + " " + path + " HTTP/1.1\r\nHost: " + endpoint.authority() + "\r\nContent-Type: application/json\r\nContent-Length: " + payload.length + "\r\nConnection: close\r\n";
        if (token != null) headers += "Authorization: Bearer " + token + "\r\n";
        socket.getOutputStream().write((headers + "\r\n").getBytes(StandardCharsets.US_ASCII));
        socket.getOutputStream().write(payload); socket.getOutputStream().flush();
        BufferedInputStream input = new BufferedInputStream(socket.getInputStream());
        ByteArrayOutputStream head = new ByteArrayOutputStream();
        int state = 0, c;
        while ((c = input.read()) != -1) {
            head.write(c);
            if (head.size() > 16384) throw new Failure(400, "Response headers are too large.");
            state = (state == 0 || state == 2) ? (c == 13 ? state + 1 : 0) : (c == 10 ? state + 1 : 0);
            if (state == 4) break;
        }
        if (state != 4) throw new Failure(0, "The plugin closed the connection.");
        String[] lines = head.toString(StandardCharsets.US_ASCII.name()).split("\r\n");
        int status = Integer.parseInt(lines[0].split(" ")[1]), length = -1;
        for (String line : lines) if (line.toLowerCase(Locale.ROOT).startsWith("content-length:")) length = Integer.parseInt(line.substring(15).trim());
        if (length < 0 || length > 4 * 1024 * 1024) throw new Failure(400, "Invalid response length.");
        byte[] data = new byte[length]; int offset = 0;
        while (offset < length) { int n = input.read(data, offset, length - offset); if (n < 0) throw new Failure(0, "The response was interrupted."); offset += n; }
        JSONObject envelope = new JSONObject(new String(data, StandardCharsets.UTF_8));
        if (status != 200 || !envelope.optBoolean("ok")) throw new Failure(status, envelope.optString("error", "The plugin could not complete the request."));
        Object result = envelope.get("data");
        if (result instanceof org.json.JSONArray) return new JSONObject().put("processes", result);
        return (JSONObject) result;
    }
}
