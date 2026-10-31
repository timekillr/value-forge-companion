package io.github.timekillr.valueforge.companion;
import org.junit.Test;
import static org.junit.Assert.*;
public class EndpointTest {
    @Test public void acceptsAddressesWithoutChangingApiPaths() {
        assertEquals("192.168.1.20:52881", Endpoint.parse("192.168.1.20").authority());
        assertEquals("machine.local:52881", Endpoint.parse("https://machine.local:52881/").authority());
        assertEquals("[::1]:52881", Endpoint.parse("[::1]:52881").authority());
    }
    @Test public void rejectsCredentialsCleartextAndInjectedPaths() {
        for (String text : new String[]{"http://machine", "https://user:pass@machine", "machine/path", "machine?token=x", "machine:0", "machine:65536", ""}) {
            try { Endpoint.parse(text); fail(text); } catch (IllegalArgumentException expected) { }
        }
    }
}
