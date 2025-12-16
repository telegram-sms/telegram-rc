import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.file.Files;
import java.nio.file.Paths;
import java.util.ArrayList;
import java.util.List;

public class ChangelogGenerate {
    public static void main(String[] args) throws IOException, InterruptedException {
        // Get the latest tag or fallback to HEAD~10
        String latestTag = getLatestTag();
        String range = latestTag + "..HEAD";

        // Get git log
        List<String> commits = getGitLog(range);

        // Generate changelog
        StringBuilder changelog = new StringBuilder();
        changelog.append("# Changelog\n\n");
        for (String commit : commits) {
            changelog.append("- ").append(commit).append("\n");
        }

        // Write to CHANGELOG.md
        Files.write(Paths.get("CHANGELOG.md"), changelog.toString().getBytes());
    }

    private static String getLatestTag() throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "describe", "--tags", "--abbrev=0").start();
        process.waitFor();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String tag = reader.readLine();
            return tag != null ? tag : "HEAD~10";
        }
    }

    private static List<String> getGitLog(String range) throws IOException, InterruptedException {
        Process process = new ProcessBuilder("git", "log", "--pretty=format:%h - %s (%an)", range).start();
        process.waitFor();
        List<String> commits = new ArrayList<>();
        try (BufferedReader reader = new BufferedReader(new InputStreamReader(process.getInputStream()))) {
            String line;
            while ((line = reader.readLine()) != null) {
                commits.add(line);
            }
        }
        return commits;
    }
}
