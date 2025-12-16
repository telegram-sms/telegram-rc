import java.io.*;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.charset.StandardCharsets;

public class changelogGenerate {

    // Configure OneAPI parameters
    private static final String API_BASE_URL = "https://your-oneapi-url.com/v1/chat/completions";
    private static final String API_KEY = "your-api-key-here";
    private static final String MODEL = "gpt-3.5-turbo";

    public static void main(String[] args) {
        try {
            // Parameters: Git repository path, commit count
            String repoPath = "."; // Current directory, or specify path like "/path/to/your/repo"
            int commitCount = 20; // Get last 20 commits
            String branch = "HEAD"; // Or specify branch like "main", "master"

            System.out.println("Fetching Git commit history...");
            String commits = getGitCommits(repoPath, commitCount, branch);

            if (commits.isEmpty()) {
                System.err.println("No commit history found");
                return;
            }

            System.out.println("\nFetched commits:\n" + commits);
            System.out.println("\nCalling OneAPI for summarization...");

            String summary = summarizeChangelog(commits);
            System.out.println("\n=== Changelog Summary ===\n" + summary);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
            e.printStackTrace();
        }
    }

    /**
     * Get Git commit history
     */
    public static String getGitCommits(String repoPath, int count, String branch)
            throws IOException, InterruptedException {
        // Use git log command with custom format
        // %h: short hash, %an: author name, %ad: author date, %s: subject
        String format = "%h | %an | %ad | %s";

        ProcessBuilder processBuilder = new ProcessBuilder(
                "git",
                "-C", repoPath,  // Specify repository path
                "log",
                "-" + count,     // Get last N commits
                branch,
                "--pretty=format:" + format,
                "--date=short"   // Date format: YYYY-MM-DD
        );

        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        int exitCode = process.waitFor();
        if (exitCode != 0) {
            throw new IOException("Git command failed with exit code: " + exitCode);
        }

        return output.toString().trim();
    }

    /**
     * Get commits between two references (tags, commits, branches)
     */
    public static String getGitCommitRange(String repoPath, String fromCommit, String toCommit)
            throws IOException, InterruptedException {
        String format = "%h | %an | %ad | %s";

        ProcessBuilder processBuilder = new ProcessBuilder(
                "git",
                "-C", repoPath,
                "log",
                fromCommit + ".." + toCommit,
                "--pretty=format:" + format,
                "--date=short"
        );

        processBuilder.redirectErrorStream(true);
        Process process = processBuilder.start();

        StringBuilder output = new StringBuilder();
        try (BufferedReader reader = new BufferedReader(
                new InputStreamReader(process.getInputStream(), StandardCharsets.UTF_8))) {
            String line;
            while ((line = reader.readLine()) != null) {
                output.append(line).append("\n");
            }
        }

        process.waitFor();
        return output.toString().trim();
    }

    /**
     * Call OneAPI to summarize commits
     */
    public static String summarizeChangelog(String commits) throws IOException {
        URL url = new URL(API_BASE_URL);
        HttpURLConnection conn = (HttpURLConnection) url.openConnection();

        try {
            conn.setRequestMethod("POST");
            conn.setRequestProperty("Content-Type", "application/json");
            conn.setRequestProperty("Authorization", "Bearer " + API_KEY);
            conn.setRequestProperty("Accept", "application/json");
            conn.setDoOutput(true);
            conn.setDoInput(true);
            conn.setConnectTimeout(30000);
            conn.setReadTimeout(60000);

            String jsonPayload = buildJsonPayload(commits);

            try (OutputStream os = conn.getOutputStream()) {
                byte[] input = jsonPayload.getBytes(StandardCharsets.UTF_8);
                os.write(input, 0, input.length);
            }

            int responseCode = conn.getResponseCode();
            if (responseCode == HttpURLConnection.HTTP_OK) {
                return parseResponse(conn.getInputStream());
            } else {
                String error = readStream(conn.getErrorStream());
                throw new IOException("API request failed, status code: " + responseCode + ", error: " + error);
            }

        } finally {
            conn.disconnect();
        }
    }

    private static String buildJsonPayload(String commits) {
        String escapedCommits = escapeJson(commits);
        String prompt = escapeJson(
                "Please analyze the following Git commit history and generate a structured changelog summary. " +
                        "Format: commit hash | author | date | commit message\n\n" +
                        "Organize the summary into these categories:\n" +
                        "1. Features (new functionality)\n" +
                        "2. Bug Fixes\n" +
                        "3. Performance Improvements\n" +
                        "4. Documentation\n" +
                        "5. Other Changes\n\n" +
                        "List relevant commits under each category."
        );

        return String.format("""
            {
              "model": "%s",
              "messages": [
                {
                  "role": "system",
                  "content": "You are a professional technical documentation assistant specialized in analyzing Git commit history and generating clear changelogs."
                },
                {
                  "role": "user",
                  "content": "%s\\n\\nCommit History:\\n%s"
                }
              ],
              "temperature": 0.5,
              "max_tokens": 2000
            }
            """, MODEL, prompt, escapedCommits);
    }

    private static String parseResponse(InputStream is) throws IOException {
        String response = readStream(is);

        int contentIndex = response.indexOf("\"content\"");
        if (contentIndex == -1) {
            return response;
        }

        int startQuote = response.indexOf("\"", contentIndex + 10);
        int endQuote = findClosingQuote(response, startQuote + 1);

        if (startQuote != -1 && endQuote != -1) {
            String content = response.substring(startQuote + 1, endQuote);
            return unescapeJson(content);
        }

        return response;
    }

    private static String readStream(InputStream is) throws IOException {
        if (is == null) return "";

        try (BufferedReader br = new BufferedReader(
                new InputStreamReader(is, StandardCharsets.UTF_8))) {
            StringBuilder response = new StringBuilder();
            String line;
            while ((line = br.readLine()) != null) {
                response.append(line);
            }
            return response.toString();
        }
    }

    private static String escapeJson(String text) {
        return text.replace("\\", "\\\\")
                .replace("\"", "\\\"")
                .replace("\n", "\\n")
                .replace("\r", "\\r")
                .replace("\t", "\\t");
    }

    private static String unescapeJson(String text) {
        return text.replace("\\n", "\n")
                .replace("\\r", "\r")
                .replace("\\t", "\t")
                .replace("\\\"", "\"")
                .replace("\\\\", "\\");
    }

    private static int findClosingQuote(String str, int start) {
        for (int i = start; i < str.length(); i++) {
            if (str.charAt(i) == '"' && str.charAt(i - 1) != '\\') {
                return i;
            }
        }
        return -1;
    }
}
