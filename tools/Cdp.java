// Minimal Chrome DevTools Protocol driver for Blockbench - drop-in replacement for
// bb_cdp.mjs (which needs Node.js; this machine has none, but has JDK 21).
//
//   java Cdp.java --port 9333 --file expr.js
//   java Cdp.java --port 9333 --expr "Blockbench.version"
//
// The evaluated JS is wrapped like the node driver does, but the payload travels back
// base64-encoded inside a single JSON string, so the response envelope can be read
// without a full JSON parser (base64 contains no quotes or backslashes).
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.net.http.WebSocket;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Base64;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.TimeUnit;

public final class Cdp {
    static final String MARK = "\"type\":\"string\",\"value\":\"";

    public static void main(String[] args) throws Exception {
        String port = "9222";
        String file = null;
        String expr = null;
        for (int i = 0; i < args.length - 1; i++) {
            switch (args[i]) {
                case "--port" -> port = args[++i];
                case "--file" -> file = args[++i];
                case "--expr" -> expr = args[++i];
                default -> { }
            }
        }
        if (file != null) {
            expr = Files.readString(Path.of(file), StandardCharsets.UTF_8);
        }
        if (expr == null) {
            System.err.println("need --file or --expr");
            System.exit(2);
        }

        String wsUrl = findPage(port);
        if (wsUrl == null) {
            System.err.println("no debuggable page found on port " + port);
            System.exit(3);
        }

        String wrapped = "(async () => { try { const v = await (async()=>{ " + expr
                + " })(); const s = (typeof v === 'undefined' || v === null) ? '' : "
                + "(typeof v === 'string' ? v : JSON.stringify(v)); "
                + "return 'OK:' + btoa(unescape(encodeURIComponent(s))); } "
                + "catch (e) { return 'ERR:' + btoa(unescape(encodeURIComponent(String(e && e.stack || e)))); } })()";

        String payload = evaluate(wsUrl, wrapped);
        if (payload == null) {
            System.err.println("EVAL EXCEPTION (no payload returned)");
            System.exit(4);
        }
        if (payload.startsWith("ERR:")) {
            System.err.println("JS ERROR:\n" + b64decode(payload.substring(4)));
            System.exit(5);
        }
        System.out.println(b64decode(payload.substring(3)));
    }

    static String b64decode(String s) {
        return new String(Base64.getDecoder().decode(s), StandardCharsets.UTF_8);
    }

    /** /json/list -> first page target's webSocketDebuggerUrl. */
    static String findPage(String port) throws Exception {
        HttpClient client = HttpClient.newHttpClient();
        HttpRequest req = HttpRequest.newBuilder(URI.create("http://127.0.0.1:" + port + "/json/list")).GET().build();
        HttpResponse<String> res = client.send(req, HttpResponse.BodyHandlers.ofString());
        // CDP pretty-prints /json/list by default ("key": "value"); squeeze the whitespace so the
        // substring scans below can look for compact markers (base64 payloads never contain spaces).
        String body = res.body().replaceAll("\\s+", "");
        int idx = 0;
        while (true) {
            int urlAt = body.indexOf("\"webSocketDebuggerUrl\":\"", idx);
            if (urlAt < 0) {
                return null;
            }
            int start = urlAt + "\"webSocketDebuggerUrl\":\"".length();
            int end = body.indexOf('"', start);
            String url = body.substring(start, end);
            // only page targets have a ws url in practice; make sure it is the page one
            int objStart = body.lastIndexOf('{', urlAt);
            String obj = body.substring(objStart, urlAt);
            if (obj.contains("\"type\":\"page\"")) {
                return url;
            }
            idx = end;
        }
    }

    /** Send one Runtime.evaluate and return the base64 payload marker string. */
    static String evaluate(String wsUrl, String expression) throws Exception {
        CompletableFuture<String> got = new CompletableFuture<>();
        StringBuilder buf = new StringBuilder();
        HttpClient client = HttpClient.newHttpClient();
        WebSocket ws = client.newWebSocketBuilder().connectTimeout(java.time.Duration.ofSeconds(30))
                .buildAsync(URI.create(wsUrl), new WebSocket.Listener() {
                    @Override
                    public CompletionStage<?> onText(WebSocket webSocket, CharSequence data, boolean last) {
                        buf.append(data);
                        if (last) {
                            String msg = buf.toString();
                            buf.setLength(0);
                            if (msg.contains("\"id\":1")) {
                                got.complete(msg);
                            }
                        }
                        webSocket.request(1);
                        return null;
                    }
                }).join();

        String request = "{\"id\":1,\"method\":\"Runtime.evaluate\",\"params\":{\"expression\":"
                + jsonString(expression) + ",\"awaitPromise\":true,\"returnByValue\":true,\"userGesture\":true}}";
        ws.sendText(request, true).join();
        String envelope = got.get(600, TimeUnit.SECONDS);
        ws.sendClose(WebSocket.NORMAL_CLOSURE, "bye");

        String flat = envelope.replaceAll("\\s+", "");
        int at = flat.indexOf(MARK);
        if (at < 0) {
            if (flat.contains("exceptionDetails")) {
                System.err.println("EVAL EXCEPTION: " + flat.substring(0, Math.min(2000, flat.length())));
            }
            return null;
        }
        int start = at + MARK.length();
        int end = flat.indexOf('"', start);
        return flat.substring(start, end);
    }

    /** JSON string literal (the only escaping we need for the outgoing request). */
    static String jsonString(String s) {
        StringBuilder sb = new StringBuilder("\"");
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            switch (c) {
                case '"' -> sb.append("\\\"");
                case '\\' -> sb.append("\\\\");
                case '\n' -> sb.append("\\n");
                case '\r' -> sb.append("\\r");
                case '\t' -> sb.append("\\t");
                default -> {
                    if (c < 0x20) {
                        sb.append(String.format("\\u%04x", (int) c));
                    } else {
                        sb.append(c);
                    }
                }
            }
        }
        return sb.append('"').toString();
    }
}
