package io.github.timekillr.valueforge.companion;

import java.net.InetAddress;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.security.KeyStore;
import java.util.concurrent.atomic.AtomicInteger;
import javax.net.ssl.KeyManagerFactory;
import javax.net.ssl.SSLContext;
import javax.net.ssl.SSLServerSocket;
import javax.net.ssl.SSLSocket;
import org.json.JSONObject;
import org.junit.Before;
import org.junit.After;
import org.junit.Test;
import static org.junit.Assert.*;

/** The bundled certificate/key are a public test fixture, never a release identity. */
public class TransportTest {
    private SSLServerSocket server;
    private Thread acceptor;
    private Endpoint endpoint;
    private String pin;
    private String receivedBody = "", receivedAuth;
    private final AtomicInteger requests = new AtomicInteger();
    @Before public void start() throws Exception {
        KeyStore keys = KeyStore.getInstance("PKCS12");
        keys.load(getClass().getResourceAsStream("/test-server.p12"), "test-only-password".toCharArray());
        pin = Transport.hash(keys.getCertificate("test-only").getEncoded());
        KeyManagerFactory manager = KeyManagerFactory.getInstance(KeyManagerFactory.getDefaultAlgorithm());
        manager.init(keys, "test-only-password".toCharArray());
        SSLContext context = SSLContext.getInstance("TLS");context.init(manager.getKeyManagers(),null,null);
        server = (SSLServerSocket) context.getServerSocketFactory().createServerSocket(0,8,InetAddress.getByName("127.0.0.1"));
        acceptor = new Thread(() -> {
            while (!server.isClosed()) try (SSLSocket socket = (SSLSocket) server.accept()) {
                BufferedReader input = new BufferedReader(new InputStreamReader(socket.getInputStream(),StandardCharsets.UTF_8));
                String first = input.readLine(), line; int length = 0;
                receivedAuth = null;
                while ((line=input.readLine()) != null && !line.isEmpty()) {
                    if (line.startsWith("Authorization: ")) receivedAuth = line.substring(15);
                    if (line.startsWith("Content-Length: ")) length = Integer.parseInt(line.substring(16));
                }
                requests.incrementAndGet();
                char[] chars = new char[length];int count=0;
                while (count<length) count += input.read(chars,count,length-count);
                receivedBody = new String(chars);
                boolean info = first.startsWith("GET /v1/info ");
                int code = info || "Bearer test-token".equals(receivedAuth) ? 200 : 401;
                JSONObject body = new JSONObject().put("ok",code == 200);
                if (code == 200) body.put("data",new JSONObject().put("name","Value Forge").put("protocol",1).put("server_id","test-server"));
                else body.put("error","Pair this phone first.");
                byte[] bytes = body.toString().getBytes(StandardCharsets.UTF_8);
                socket.getOutputStream().write(("HTTP/1.1 "+code+" Response\r\nContent-Length: "+bytes.length+"\r\n\r\n").getBytes(StandardCharsets.US_ASCII));
                socket.getOutputStream().write(bytes);socket.getOutputStream().flush();
            } catch (Exception ignored) { }
        });
        acceptor.setDaemon(true);acceptor.start();endpoint = Endpoint.parse("127.0.0.1:"+server.getLocalPort());
    }
    @After public void stop() throws Exception { if (server != null) server.close(); if (acceptor != null) acceptor.join(2000); }
    @Test public void publicProbeReturnsActualFingerprintWithoutSendingCredentials() throws Exception {
        JSONObject info = Transport.probe(endpoint);
        assertEquals(pin,info.getString("fingerprint"));assertNull(receivedAuth);
        assertEquals("",receivedBody);assertEquals(24,Transport.shortFingerprint(pin).length());
    }
    @Test public void rememberedPinAllowsAuthenticatedRequests() throws Exception {
        JSONObject data = Transport.request(endpoint,pin,"test-token","GET","/v1/state",null);
        assertEquals("test-server",data.getString("server_id"));
        assertEquals("Bearer test-token",receivedAuth);
    }
    @Test public void changedCertificateIsRejectedBeforeAnyTokenIsSent() throws Exception {
        try {
            Transport.request(endpoint,"0".repeat(64),"test-token","GET","/v1/state",null);
            fail("A changed certificate must not receive credentials.");
        } catch (javax.net.ssl.SSLException expected) { }
        assertEquals(0,requests.get());
    }
    @Test public void preservesExactIntegerStringsAndReportsRevocation() throws Exception {
        Transport.request(endpoint,pin,"test-token","POST","/v1/command",new JSONObject().put("value","18446744073709551601"));
        assertEquals("18446744073709551601",new JSONObject(receivedBody).getString("value"));
        try { Transport.request(endpoint,pin,"revoked","GET","/v1/state",null);fail(); }
        catch (Transport.Failure error) { assertEquals(401,error.status); }
    }
}
