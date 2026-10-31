package io.github.timekillr.valueforge.companion;

import java.net.URI;

/** Host/port only. User input can never choose an API path or send a token over HTTP. */
public final class Endpoint {
    public final String host;
    public final int port;
    private Endpoint(String host, int port) { this.host = host; this.port = port; }
    public static Endpoint parse(String input) {
        try {
            String text = input.trim();
            if (!text.contains("://")) text = "https://" + text;
            URI uri = new URI(text);
            if (!"https".equals(uri.getScheme()) || uri.getHost() == null || uri.getUserInfo() != null
                    || uri.getQuery() != null || uri.getFragment() != null
                    || !(uri.getPath().isEmpty() || uri.getPath().equals("/"))) throw new Exception();
            int port = uri.getPort() == -1 ? 52881 : uri.getPort();
            if (port < 1 || port > 65535) throw new Exception();
            String host = uri.getHost();
            if (host.startsWith("[")) host = host.substring(1, host.length() - 1);
            return new Endpoint(host, port);
        } catch (Exception error) {
            throw new IllegalArgumentException("Enter the Steam Machine address shown in Decky, such as 192.168.1.20:52881.");
        }
    }
    public String authority() { return (host.contains(":") ? "[" + host + "]" : host) + ":" + port; }
}
