package com.reclip.app;

import android.util.Log;

import org.json.JSONObject;

import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;
import java.net.NetworkInterface;
import java.nio.charset.StandardCharsets;
import java.security.SecureRandom;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.atomic.AtomicInteger;

import fi.iki.elonen.NanoHTTPD;

class DesktopServerManager {
    private static final String TAG = "ReClipDesktop";
    private static final int DEFAULT_PORT = 8899;
    private static final int MAX_PORT = 8919;

    private final MainActivity activity;
    private final ExecutorService jobExecutor = Executors.newCachedThreadPool();
    private final SecureRandom random = new SecureRandom();
    private final Map<String, JobState> jobs = new ConcurrentHashMap<>();
    private final AtomicInteger activeJobs = new AtomicInteger(0);
    private final AtomicInteger totalJobs = new AtomicInteger(0);
    private final AtomicInteger completedJobs = new AtomicInteger(0);
    private final AtomicInteger failedJobs = new AtomicInteger(0);

    private LocalServer server;
    private int port = DEFAULT_PORT;
    private String ip = "127.0.0.1";
    private String pin = "";
    private String token = "";
    private boolean paired = false;
    private String lastError = "";
    private long lastCompletedAt = 0L;
    private long lastFailedAt = 0L;
    private long pairedAt = 0L;
    private long serverStartedAt = 0L;

    DesktopServerManager(MainActivity activity) {
        this.activity = activity;
    }

    synchronized void startServer() throws Exception {
        if (server != null && server.wasStarted()) return;
        ip = detectLocalIp();
        pin = String.format("%06d", random.nextInt(1_000_000));
        token = UUID.randomUUID().toString();
        paired = false;
        pairedAt = 0L;
        lastError = "";
        lastCompletedAt = 0L;
        lastFailedAt = 0L;
        serverStartedAt = System.currentTimeMillis();
        jobs.clear();
        totalJobs.set(0);
        completedJobs.set(0);
        failedJobs.set(0);

        Exception lastStartError = null;
        for (int candidate = DEFAULT_PORT; candidate <= MAX_PORT; candidate++) {
            try {
                LocalServer candidateServer = new LocalServer(candidate);
                candidateServer.start(NanoHTTPD.SOCKET_READ_TIMEOUT, false);
                server = candidateServer;
                port = candidate;
                Log.i(TAG, "Desktop server started at " + getUrl());
                return;
            } catch (Exception e) {
                lastError = e.getMessage() == null ? "Port bind failed" : e.getMessage();
                lastStartError = e;
                Log.w(TAG, "Port " + candidate + " unavailable", e);
            }
        }
        throw lastStartError != null ? lastStartError : new IllegalStateException("No desktop server port available");
    }

    synchronized void stopServer() {
        if (server != null) {
            server.stop();
            server = null;
        }
        paired = false;
        pairedAt = 0L;
        pin = "";
        token = "";
        jobs.clear();
        activeJobs.set(0);
        totalJobs.set(0);
        completedJobs.set(0);
        failedJobs.set(0);
        Log.i(TAG, "Desktop server stopped");
    }

    synchronized Status getStatus() {
        boolean running = server != null && server.wasStarted();
        return new Status(
            running,
            running ? getUrl() : "",
            pin,
            paired,
            activeJobs.get(),
            lastError,
            lastCompletedAt,
            lastFailedAt,
            pairedAt,
            serverStartedAt
        );
    }

    synchronized String getStatusJson() {
        return getStatus().toJson().toString();
    }

    private String getUrl() {
        return "http://" + ip + ":" + port;
    }

    private String detectLocalIp() {
        try {
            for (NetworkInterface iface : Collections.list(NetworkInterface.getNetworkInterfaces())) {
                if (!iface.isUp() || iface.isLoopback()) continue;
                for (InetAddress addr : Collections.list(iface.getInetAddresses())) {
                    if (addr instanceof Inet4Address && !addr.isLoopbackAddress()) {
                        String host = addr.getHostAddress();
                        if (addr.isSiteLocalAddress()) return host;
                    }
                }
            }
        } catch (Exception e) {
            Log.w(TAG, "IP detection failed", e);
        }
        return "127.0.0.1";
    }

    static class Status {
        final boolean running;
        final String url;
        final String pin;
        final boolean paired;
        final int activeJobs;
        final String lastError;
        final long lastCompletedAt;
        final long lastFailedAt;
        final long pairedAt;
        final long serverStartedAt;

        Status(
            boolean running,
            String url,
            String pin,
            boolean paired,
            int activeJobs,
            String lastError,
            long lastCompletedAt,
            long lastFailedAt,
            long pairedAt,
            long serverStartedAt
        ) {
            this.running = running;
            this.url = url == null ? "" : url;
            this.pin = pin == null ? "" : pin;
            this.paired = paired;
            this.activeJobs = activeJobs;
            this.lastError = lastError == null ? "" : lastError;
            this.lastCompletedAt = lastCompletedAt;
            this.lastFailedAt = lastFailedAt;
            this.pairedAt = pairedAt;
            this.serverStartedAt = serverStartedAt;
        }

        static Status off() {
            return new Status(false, "", "", false, 0, "", 0L, 0L, 0L, 0L);
        }

        JSONObject toJson() {
            JSONObject out = new JSONObject();
            try {
                out.put("success", true);
                out.put("running", running);
                out.put("url", url);
                out.put("pin", pin);
                out.put("paired", paired);
                out.put("activeJobs", activeJobs);
                out.put("lastError", lastError);
                out.put("lastCompletedAt", lastCompletedAt);
                out.put("lastFailedAt", lastFailedAt);
                out.put("pairedAt", pairedAt);
                out.put("serverStartedAt", serverStartedAt);
            } catch (Exception ignored) {}
            return out;
        }
    }

    private class JobState {
        final String id;
        volatile String status = "queued";
        volatile int percent = 0;
        volatile String message = "Queued";
        volatile JSONObject result = null;

        JobState(String id) {
            this.id = id;
        }

        JSONObject toJson() {
            JSONObject out = new JSONObject();
            try {
                out.put("success", true);
                out.put("jobId", id);
                out.put("status", status);
                out.put("percent", percent);
                out.put("message", message);
                if (result != null) out.put("result", result);
            } catch (Exception ignored) {}
            return out;
        }
    }

    private class LocalServer extends NanoHTTPD {
        LocalServer(int port) {
            super("0.0.0.0", port);
        }

        @Override
        public Response serve(IHTTPSession session) {
            try {
                Method method = session.getMethod();
                String uri = session.getUri();
                if (Method.OPTIONS.equals(method)) return cors(newFixedLengthResponse(""));
                if ("/".equals(uri) && Method.GET.equals(method)) return dashboard();
                if ("/logo.png".equals(uri) && Method.GET.equals(method)) return asset("www/logo.png", "image/png");
                if ("/api/pair".equals(uri) && Method.POST.equals(method)) return pair(session);

                if (!authorized(session)) {
                    return json(401, error("Pairing required"));
                }

                if ("/api/status".equals(uri) && Method.GET.equals(method)) return status();
                if ("/api/metrics".equals(uri) && Method.GET.equals(method)) return metrics();
                if ("/api/info".equals(uri) && Method.POST.equals(method)) return info(session);
                if ("/api/download".equals(uri) && Method.POST.equals(method)) return download(session);
                if (uri.startsWith("/api/progress/") && Method.GET.equals(method)) {
                    return progress(uri.substring("/api/progress/".length()));
                }
                if ("/api/history".equals(uri) && Method.GET.equals(method)) return history();
                if (uri.startsWith("/api/file/") && Method.GET.equals(method)) {
                    return file(uri.substring("/api/file/".length()));
                }
                return json(404, error("Not found"));
            } catch (Exception e) {
                Log.e(TAG, "Desktop route failed", e);
                return json(500, error(e.getMessage()));
            }
        }

        private Response dashboard() throws Exception {
            InputStream in = activity.getAssets().open("www/desktop.html");
            byte[] bytes = readAll(in);
            Response res = newFixedLengthResponse(
                Response.Status.OK,
                "text/html; charset=utf-8",
                new String(bytes, StandardCharsets.UTF_8)
            );
            return cors(res);
        }

        private Response asset(String path, String mime) throws Exception {
            InputStream in = activity.getAssets().open(path);
            return cors(newChunkedResponse(Response.Status.OK, mime, in));
        }

        private Response pair(IHTTPSession session) throws Exception {
            JSONObject body = body(session);
            if (!pin.equals(body.optString("pin", ""))) {
                return json(403, error("Wrong PIN"));
            }
            paired = true;
            pairedAt = System.currentTimeMillis();
            lastError = "";
            JSONObject out = getStatus().toJson();
            out.put("token", token);
            return json(200, out);
        }

        private Response status() throws Exception {
            JSONObject out = new JSONObject(activity.getRuntimeInfoJson());
            out.put("desktop", getStatus().toJson());
            return json(200, out);
        }

        private Response metrics() {
            try {
                JSONObject out = new JSONObject();
                out.put("success", true);
                out.put("activeJobs", activeJobs.get());
                out.put("totalJobs", totalJobs.get());
                out.put("completedJobs", completedJobs.get());
                out.put("failedJobs", failedJobs.get());
                out.put("paired", paired);
                out.put("serverUrl", getUrl());
                out.put("historyCount", new org.json.JSONArray(activity.getDownloadHistoryJson()).length());
                int done = completedJobs.get();
                int failed = failedJobs.get();
                int denom = done + failed;
                out.put("successRate", denom > 0 ? (done * 100.0 / denom) : JSONObject.NULL);
                return json(200, out);
            } catch (Exception e) {
                return json(500, error(e.getMessage()));
            }
        }

        private Response info(IHTTPSession session) throws Exception {
            JSONObject body = body(session);
            String url = body.optString("url", "");
            if (url.trim().isEmpty()) return json(400, error("URL required"));
            return jsonRaw(200, activity.fetchInfoForDesktop(url));
        }

        private Response download(IHTTPSession session) throws Exception {
            JSONObject body = body(session);
            String id = "job_" + System.currentTimeMillis() + "_" + Math.abs(random.nextInt(9999));
            JobState job = new JobState(id);
            jobs.put(id, job);
            activeJobs.incrementAndGet();
            totalJobs.incrementAndGet();
            final String destination = body.optString("destination", "computer");
            jobExecutor.execute(() -> {
                job.status = "running";
                job.percent = 5;
                job.message = "Downloading on phone engine";
                String resultJson = activity.startDownloadForDesktop(
                    body.optString("url", ""),
                    body.optString("formatChoice", "video"),
                    body.optString("formatId", ""),
                    body.optString("title", ""),
                    body.optString("audioProfile", "")
                );
                try {
                    JSONObject result = new JSONObject(resultJson);
                    job.result = result;
                    boolean ok = result.optBoolean("success", false);
                    job.status = ok ? "done" : "error";
                    job.percent = ok ? 100 : 0;
                    if (ok) {
                        completedJobs.incrementAndGet();
                        lastCompletedAt = System.currentTimeMillis();
                        lastError = "";
                        String historyId = result.optString("historyId", "");
                        if ("computer".equalsIgnoreCase(destination)) {
                            if (!historyId.isEmpty()) {
                                String encodedId = java.net.URLEncoder.encode(historyId, StandardCharsets.UTF_8.name());
                                String encodedToken = java.net.URLEncoder.encode(token, StandardCharsets.UTF_8.name());
                                job.message = "Done";
                                result.put("desktopFileUrl", "/api/file/" + encodedId + "?token=" + encodedToken);
                            } else {
                                job.message = "Done (desktop link unavailable)";
                            }
                        } else {
                            job.message = "Saved to phone";
                        }
                    } else {
                        failedJobs.incrementAndGet();
                        job.message = result.optString("error", "Download failed");
                        lastFailedAt = System.currentTimeMillis();
                        lastError = job.message;
                    }
                } catch (Exception e) {
                    job.status = "error";
                    job.percent = 0;
                    job.message = e.getMessage();
                    failedJobs.incrementAndGet();
                    lastFailedAt = System.currentTimeMillis();
                    lastError = job.message;
                } finally {
                    activeJobs.decrementAndGet();
                }
            });
            return json(202, job.toJson());
        }

        private Response progress(String jobId) {
            JobState job = jobs.get(jobId);
            if (job == null) return json(404, error("Unknown job"));
            return json(200, job.toJson());
        }

        private Response history() {
            return jsonRaw(200, activity.getDownloadHistoryJson());
        }

        private Response file(String id) throws Exception {
            InputStream stream = activity.openHistoryItemStream(id);
            if (stream == null) return json(404, error("File unavailable"));
            String mime = activity.getHistoryItemMime(id);
            String name = activity.getHistoryItemName(id).replace("\"", "");
            Response res = newChunkedResponse(Response.Status.OK, mime, stream);
            res.addHeader("Content-Disposition", "attachment; filename=\"" + name + "\"");
            return cors(res);
        }

        private boolean authorized(IHTTPSession session) {
            String header = session.getHeaders().get("authorization");
            if (header != null && header.startsWith("Bearer ")) {
                return token.equals(header.substring("Bearer ".length()).trim());
            }
            String xToken = session.getHeaders().get("x-reclip-token");
            if (token.equals(xToken)) return true;
            return token.equals(session.getParms().get("token"));
        }

        private JSONObject body(IHTTPSession session) throws Exception {
            Map<String, String> files = new HashMap<>();
            session.parseBody(files);
            String raw = files.get("postData");
            return raw == null || raw.trim().isEmpty() ? new JSONObject() : new JSONObject(raw);
        }

        private Response json(int code, JSONObject obj) {
            return cors(newFixedLengthResponse(httpStatus(code), "application/json", obj.toString()));
        }

        private Response jsonRaw(int code, String raw) {
            return cors(newFixedLengthResponse(httpStatus(code), "application/json", raw));
        }

        private Response.Status httpStatus(int code) {
            if (code == 202) return Response.Status.ACCEPTED;
            if (code == 400) return Response.Status.BAD_REQUEST;
            if (code == 401) return Response.Status.UNAUTHORIZED;
            if (code == 403) return Response.Status.FORBIDDEN;
            if (code == 404) return Response.Status.NOT_FOUND;
            if (code == 500) return Response.Status.INTERNAL_ERROR;
            return Response.Status.OK;
        }

        private Response cors(Response res) {
            res.addHeader("Access-Control-Allow-Origin", "*");
            res.addHeader("Access-Control-Allow-Headers", "Content-Type, Authorization, X-ReClip-Token");
            res.addHeader("Access-Control-Allow-Methods", "GET, POST, OPTIONS");
            return res;
        }

        private JSONObject error(String message) {
            JSONObject out = new JSONObject();
            try {
                out.put("success", false);
                out.put("error", message == null || message.isEmpty() ? "Unknown error" : message);
            } catch (Exception ignored) {}
            return out;
        }

        private byte[] readAll(InputStream input) throws Exception {
            try (InputStream in = input; java.io.ByteArrayOutputStream out = new java.io.ByteArrayOutputStream()) {
                byte[] buf = new byte[8192];
                int n;
                while ((n = in.read(buf)) != -1) out.write(buf, 0, n);
                return out.toByteArray();
            }
        }
    }
}
