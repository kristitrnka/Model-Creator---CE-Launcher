package ce.launcher;

import javax.swing.*;
import java.awt.*;
import java.io.*;
import java.net.*;
import java.nio.charset.StandardCharsets;
import java.nio.file.*;
import java.security.MessageDigest;
import java.util.*;
import java.util.List;
import java.util.regex.*;

public final class Main {
    private static final String API = "https://api.github.com/repos/kristitrnka/ModelCreator-CE/releases/latest";
    private static final String BUNDLED_VERSION = "2.2.2";
    private static final String LAUNCHER_VERSION = "1.3.6";

    private JFrame frame;
    private JLabel status;
    private JLabel versions;
    private JProgressBar progress;
    private Path dataDir, editorJar, versionFile, ignoredFile, logFile;

    public static void main(String[] args) {
        SwingUtilities.invokeLater(new Runnable() { public void run() { new Main().start(); } });
    }

    private void start() {
        setupPaths();
        buildUi();
        new Thread(new Runnable() { public void run() { runLauncher(); } }, "launcher-worker").start();
    }

    private void setupPaths() {
        dataDir = Paths.get(System.getProperty("user.home"), ".modelcreator-ce");
        editorJar = dataDir.resolve("ModelCreator-Community-Edition.jar");
        versionFile = dataDir.resolve("installed-version.txt");
        ignoredFile = dataDir.resolve("ignored-version.txt");
        logFile = dataDir.resolve("launcher.log");
        try { Files.createDirectories(dataDir); } catch (IOException ignored) {}
    }

    private void buildUi() {
        frame = new JFrame("Model Creator CE Launcher " + LAUNCHER_VERSION);
        frame.setDefaultCloseOperation(WindowConstants.EXIT_ON_CLOSE);
        frame.setResizable(false);
        JPanel p = new JPanel();
        p.setBorder(BorderFactory.createEmptyBorder(22, 26, 22, 26));
        p.setLayout(new BoxLayout(p, BoxLayout.Y_AXIS));
        JLabel title = new JLabel("Model Creator Community Edition");
        title.setFont(title.getFont().deriveFont(Font.BOLD, 18f));
        title.setAlignmentX(Component.CENTER_ALIGNMENT);
        status = new JLabel("Preparing update check…");
        status.setAlignmentX(Component.CENTER_ALIGNMENT);
        versions = new JLabel("Bundled " + BUNDLED_VERSION);
        versions.setForeground(Color.DARK_GRAY);
        versions.setAlignmentX(Component.CENTER_ALIGNMENT);
        progress = new JProgressBar(0, 100);
        progress.setStringPainted(true);
        progress.setString("Starting…");
        progress.setAlignmentX(Component.CENTER_ALIGNMENT);
        progress.setPreferredSize(new Dimension(390, 20));
        p.add(title); p.add(Box.createVerticalStrut(10)); p.add(status);
        p.add(Box.createVerticalStrut(6)); p.add(versions);
        p.add(Box.createVerticalStrut(16)); p.add(progress);
        frame.setContentPane(p);
        frame.pack(); frame.setSize(470, 185); frame.setLocationRelativeTo(null); frame.setVisible(true);
        frame.toFront();
    }

    private void ui(final String text, final int value, final String bar) {
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            status.setText(text); progress.setValue(value); progress.setString(bar);
        }});
    }

    private void setVersions(final String installed, final String latest) {
        SwingUtilities.invokeLater(new Runnable() { public void run() {
            versions.setText("Installed " + installed + "   |   Latest " + latest);
        }});
    }

    private void runLauncher() {
        try (PrintStream log = new PrintStream(new FileOutputStream(logFile.toFile(), true), true, "UTF-8")) {
            log.println("\n--- Launcher " + LAUNCHER_VERSION + " ---");
            ensureBundledEditor(log);
            String installed = readFile(versionFile, BUNDLED_VERSION);

            ui("Checking GitHub for updates…", 15, "Checking…");
            Release release;
            try {
                release = fetchLatestRelease(log);
            } catch (Exception e) {
                e.printStackTrace(log);
                ui("Could not check for updates. Launching installed build…", 100, "Offline");
                launchEditor(log);
                closeLater();
                return;
            }

            setVersions(installed, release.version);
            ui("Update check complete", 35, "Checked");

            if (compareVersions(release.version, installed) > 0) {
                String ignored = readFile(ignoredFile, "");
                if (!release.version.equals(ignored)) {
                    int choice = askUpdate(installed, release);
                    if (choice == 0) {
                        installUpdate(release, log);
                        installed = release.version;
                    } else if (choice == 2) {
                        Files.write(ignoredFile, release.version.getBytes(StandardCharsets.UTF_8));
                        log.println("Ignored version " + release.version);
                    } else {
                        log.println("Update postponed");
                    }
                } else {
                    log.println("Version " + release.version + " is ignored");
                }
            } else {
                showInfo("You are up to date", "Installed version " + installed + " is the latest version.");
            }

            ui("Launching Model Creator…", 100, "Launching…");
            launchEditor(log);
            closeLater();
        } catch (Throwable t) {
            try (PrintWriter pw = new PrintWriter(new FileOutputStream(logFile.toFile(), true))) { t.printStackTrace(pw); } catch (Exception ignored) {}
            showError("Launcher error", String.valueOf(t.getMessage()));
        }
    }

    private int askUpdate(final String installed, final Release release) throws Exception {
        final int[] result = new int[1];
        SwingUtilities.invokeAndWait(new Runnable() { public void run() {
            frame.setAlwaysOnTop(true); frame.toFront(); frame.requestFocus();
            Object[] options = {"YES — Install", "NO — Not now", "IGNORE THIS VERSION"};
            String notes = release.notes == null || release.notes.trim().isEmpty() ? "No release notes." : release.notes.trim();
            if (notes.length() > 900) notes = notes.substring(0, 900) + "…";
            result[0] = JOptionPane.showOptionDialog(frame,
                    "A new update is available.\n\nInstalled: " + installed + "\nLatest: " + release.version +
                            "\n\nRelease notes:\n" + notes,
                    "Install Model Creator update?",
                    JOptionPane.DEFAULT_OPTION, JOptionPane.QUESTION_MESSAGE, null, options, options[0]);
            frame.setAlwaysOnTop(false);
            if (result[0] < 0) result[0] = 1;
        }});
        return result[0];
    }

    private void installUpdate(Release release, PrintStream log) throws Exception {
        ui("Downloading " + release.version + "…", 40, "0%");
        Path temp = dataDir.resolve("update-download.jar");
        download(release.downloadUrl, temp, log);
        ui("Verifying update…", 90, "Verifying…");
        if (release.sha256 != null && !release.sha256.isEmpty()) {
            String actual = sha256(temp);
            if (!actual.equalsIgnoreCase(release.sha256)) throw new IOException("SHA-256 mismatch");
        }
        ui("Installing update…", 96, "Installing…");
        replaceAtomically(temp, editorJar);
        Files.write(versionFile, release.version.getBytes(StandardCharsets.UTF_8));
        Files.deleteIfExists(ignoredFile);
        log.println("Installed update " + release.version);
    }

    private void ensureBundledEditor(PrintStream log) throws IOException {
        if (Files.exists(editorJar) && Files.exists(versionFile)) return;
        ui("Installing bundled editor…", 5, "Installing…");
        try (InputStream in = Main.class.getResourceAsStream("/payload/editor.jar")) {
            if (in == null) throw new FileNotFoundException("Bundled editor is missing");
            Files.copy(in, editorJar, StandardCopyOption.REPLACE_EXISTING);
        }
        Files.write(versionFile, BUNDLED_VERSION.getBytes(StandardCharsets.UTF_8));
        log.println("Installed bundled editor " + BUNDLED_VERSION);
    }

    private Release fetchLatestRelease(PrintStream log) throws IOException {
        String json = getText(API);
        String tag = first(json, "\\\"tag_name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
        if (tag == null) throw new IOException("Latest release has no tag_name");
        String version = normalizeVersion(tag);
        String notes = unescapeJson(first(json, "\\\"body\\\"\\s*:\\s*\\\"((?:\\\\.|[^\\\"])*)\\\""));

        Pattern names = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"");
        Matcher nm = names.matcher(json);
        while (nm.find()) log.println("Release asset seen: " + nm.group(1));

        Pattern p = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]+\\.jar)\\\"(?:(?!\\\"name\\\"\\s*:).){0,6000}?\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", Pattern.DOTALL);
        Matcher m = p.matcher(json);
        String assetName = null, assetUrl = null;
        while (m.find()) {
            String n = m.group(1);
            String low = n.toLowerCase(Locale.ROOT);
            if (low.contains("launcher") || low.endsWith(".jar.sha256")) continue;
            if (low.startsWith("modelcreator-community-edition-") || low.startsWith("model-creator-community-edition-") || low.equals("modelcreator-community-edition.jar")) {
                assetName = n; assetUrl = m.group(2); break;
            }
        }
        if (assetUrl == null) throw new IOException("Release " + version + " has no compatible editor JAR asset");

        String sha = null;
        Pattern cp = Pattern.compile("\\\"name\\\"\\s*:\\s*\\\"([^\\\"]*(?:sha256|SHA256SUMS)[^\\\"]*)\\\"(?:(?!\\\"name\\\"\\s*:).){0,6000}?\\\"browser_download_url\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"", Pattern.DOTALL | Pattern.CASE_INSENSITIVE);
        Matcher cm = cp.matcher(json);
        while (cm.find()) {
            String cn = cm.group(1);
            if (cn.equals(assetName + ".sha256") || cn.toLowerCase(Locale.ROOT).startsWith("sha256sums")) {
                String txt = getText(cm.group(2));
                Matcher hm = Pattern.compile("(?i)\\b([a-f0-9]{64})\\b").matcher(txt);
                if (hm.find()) sha = hm.group(1);
                break;
            }
        }
        log.println("Latest release " + version + ", selected asset=" + assetName + ", url=" + assetUrl);
        return new Release(version, assetUrl, sha, notes);
    }

    private String getText(String url) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(30000);
        c.setRequestProperty("Accept", "application/vnd.github+json");
        c.setRequestProperty("User-Agent", "ModelCreator-CE-Launcher/" + LAUNCHER_VERSION);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("HTTP " + code + " from " + url);
        try (InputStream in = c.getInputStream(); ByteArrayOutputStream out = new ByteArrayOutputStream()) {
            byte[] b = new byte[8192]; int n; while ((n = in.read(b)) >= 0) out.write(b, 0, n);
            return new String(out.toByteArray(), StandardCharsets.UTF_8);
        }
    }

    private void download(String url, Path target, PrintStream log) throws IOException {
        HttpURLConnection c = (HttpURLConnection) new URL(url).openConnection();
        c.setConnectTimeout(10000); c.setReadTimeout(180000);
        c.setRequestProperty("User-Agent", "ModelCreator-CE-Launcher/" + LAUNCHER_VERSION);
        int code = c.getResponseCode();
        if (code < 200 || code >= 300) throw new IOException("Download HTTP " + code);
        long total = c.getContentLengthLong();
        try (InputStream in = c.getInputStream(); OutputStream out = Files.newOutputStream(target)) {
            byte[] b = new byte[65536]; long done = 0; int n;
            while ((n = in.read(b)) >= 0) {
                out.write(b, 0, n); done += n;
                final int pct = total > 0 ? (int)Math.min(100, done * 100 / total) : 0;
                SwingUtilities.invokeLater(new Runnable() { public void run() { progress.setValue(40 + pct / 2); progress.setString(pct + "%"); }});
            }
        }
        log.println("Downloaded " + target + " bytes=" + Files.size(target));
    }

    private void replaceAtomically(Path source, Path target) throws IOException {
        Path backup = target.resolveSibling(target.getFileName() + ".old");
        Files.deleteIfExists(backup);
        if (Files.exists(target)) Files.move(target, backup, StandardCopyOption.REPLACE_EXISTING);
        try {
            Files.move(source, target, StandardCopyOption.REPLACE_EXISTING);
            Files.deleteIfExists(backup);
        } catch (IOException e) {
            if (Files.exists(backup)) Files.move(backup, target, StandardCopyOption.REPLACE_EXISTING);
            throw e;
        }
    }

    private void launchEditor(PrintStream log) throws IOException {
        List<String> cmd = new ArrayList<String>();
        String os = System.getProperty("os.name", "").toLowerCase(Locale.ROOT);
        String arch = System.getProperty("os.arch", "").toLowerCase(Locale.ROOT);
        if (os.contains("mac") && (arch.contains("aarch64") || arch.contains("arm64"))) {
            Path java8 = Paths.get("/Library/Java/JavaVirtualMachines/temurin-8.jdk/Contents/Home/bin/java");
            if (!Files.exists(java8)) throw new IOException("Intel Temurin Java 8 is required on Apple Silicon.");
            cmd.add("/usr/bin/arch"); cmd.add("-x86_64"); cmd.add(java8.toString());
        } else {
            cmd.add(Paths.get(System.getProperty("java.home"), "bin", os.contains("win") ? "java.exe" : "java").toString());
        }
        cmd.add("-jar"); cmd.add(editorJar.toString());
        ProcessBuilder pb = new ProcessBuilder(cmd);
        pb.directory(dataDir.toFile()); pb.redirectErrorStream(true);
        pb.redirectOutput(ProcessBuilder.Redirect.appendTo(dataDir.resolve("editor.log").toFile()));
        pb.start(); log.println("Started editor command=" + cmd);
    }

    private void closeLater() {
        try { Thread.sleep(900); } catch (InterruptedException ignored) {}
        SwingUtilities.invokeLater(new Runnable() { public void run() { frame.dispose(); } });
    }

    private void showInfo(final String title, final String message) throws Exception {
        SwingUtilities.invokeAndWait(new Runnable() { public void run() { JOptionPane.showMessageDialog(frame, message, title, JOptionPane.INFORMATION_MESSAGE); }});
    }
    private void showError(final String title, final String message) {
        SwingUtilities.invokeLater(new Runnable() { public void run() { JOptionPane.showMessageDialog(frame, message + "\n\nLog: " + logFile, title, JOptionPane.ERROR_MESSAGE); }});
    }

    private static String readFile(Path p, String fallback) {
        try { return new String(Files.readAllBytes(p), StandardCharsets.UTF_8).trim(); } catch (IOException e) { return fallback; }
    }
    private static String first(String s, String regex) { Matcher m = Pattern.compile(regex, Pattern.DOTALL).matcher(s); return m.find() ? m.group(1) : null; }
    private static String unescapeJson(String s) {
        if (s == null) return "";
        return s.replace("\\r\\n", "\n").replace("\\n", "\n").replace("\\t", "\t").replace("\\\"", "\"").replace("\\\\", "\\");
    }
    private static String normalizeVersion(String s) {
        s = s == null ? "" : s.replaceFirst("^[vV]", "");
        Matcher m = Pattern.compile("[0-9]+(?:\\.[0-9]+)*").matcher(s); return m.find() ? m.group() : "0.0.0";
    }
    private static int compareVersions(String a, String b) {
        String[] aa = normalizeVersion(a).split("\\."), bb = normalizeVersion(b).split("\\.");
        int n = Math.max(aa.length, bb.length);
        for (int i = 0; i < n; i++) { int x = i < aa.length ? Integer.parseInt(aa[i]) : 0; int y = i < bb.length ? Integer.parseInt(bb[i]) : 0; if (x != y) return x < y ? -1 : 1; }
        return 0;
    }
    private static String sha256(Path p) throws Exception {
        MessageDigest md = MessageDigest.getInstance("SHA-256");
        try (InputStream in = Files.newInputStream(p)) { byte[] b = new byte[65536]; int n; while ((n = in.read(b)) >= 0) md.update(b, 0, n); }
        StringBuilder sb = new StringBuilder(); for (byte x : md.digest()) sb.append(String.format("%02x", x)); return sb.toString();
    }

    private static final class Release {
        final String version, downloadUrl, sha256, notes;
        Release(String v, String u, String s, String n) { version = v; downloadUrl = u; sha256 = s; notes = n; }
    }
}
