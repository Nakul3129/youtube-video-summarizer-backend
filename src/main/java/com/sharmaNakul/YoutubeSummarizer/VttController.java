package com.sharmaNakul.YoutubeSummarizer;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
 
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.client.RestTemplate;

import java.io.*;
import java.util.regex.Pattern;

@CrossOrigin(origins = "http://localhost:3000")
@RestController
@RequestMapping("/api")
public class VttController {

    @Value("${gemini.api.key}")
    private String geminiApiKey;

    private final RestTemplate restTemplate = new RestTemplate();

    @PostMapping("/summarize")
    public ResponseEntity<String> summarizeYouTubeVideo(@RequestParam("videoUrl") String videoUrl) {
        try {
            downloadYoutubeCaptions(videoUrl);

            File vttFile = findDownloadedVttFile();

            if (vttFile == null || !vttFile.exists()) {
                return ResponseEntity.status(500).body("Error: Captions file not found.");
            }

            String extractedText = extractTextFromVtt(vttFile);

            if (extractedText.isEmpty()) {
                return ResponseEntity.status(500).body("❌ Error: Extracted text is empty!");
            }


            String summary = getGeminiSummary(extractedText);

            return ResponseEntity.ok(summary);

        } catch (Exception e) {
            return ResponseEntity.status(500).body("Error processing request: " + e.getMessage());
        }
    }

    private File downloadYoutubeCaptions(String videoUrl) throws IOException, InterruptedException {
        String command = "/usr/bin/yt-dlp --write-auto-sub --sub-lang en --sub-format vtt --skip-download " + videoUrl;

        System.out.println("Executing command: " + command);
        Process process = Runtime.getRuntime().exec(command);
        process.waitFor(); // Wait for yt-dlp to complete

        File dir = new File(System.getProperty("user.dir"));
        File[] files = dir.listFiles((d, name) -> name.endsWith(".vtt"));

        if (files != null && files.length > 0) {
            File latestFile = files[0];
            for (File file : files) {
                if (file.lastModified() > latestFile.lastModified()) {
                    latestFile = file;
                }
            }
            System.out.println("Found subtitles file: " + latestFile.getAbsolutePath());
            return latestFile;
        } else {
            System.err.println("No .vtt file found!");
            return null;
        }
    }



    private String extractTextFromVtt(File file) throws IOException {
        StringBuilder transcript = new StringBuilder();
        Pattern timestampPattern = Pattern.compile("\\d{2}:\\d{2}:\\d{2}\\.\\d{3} --> \\d{2}:\\d{2}:\\d{2}\\.\\d{3}");

        try (BufferedReader br = new BufferedReader(new FileReader(file))) {
            String line;
            while ((line = br.readLine()) != null) {
                // ✅ Remove timestamps
                if (timestampPattern.matcher(line).find()) {
                    continue;
                }
                // ✅ Remove unwanted VTT tags like <c>
                line = line.replaceAll("<[^>]+>", "").trim();

                // ✅ Append cleaned line if it's not empty
                if (!line.isEmpty()) {
                    transcript.append(line).append(" ");
                }
            }
        }

        // ✅ Debugging: Print extracted transcript
        System.out.println("📝 [DEBUG] Cleaned Transcript:\n" + transcript.toString().trim());

        return transcript.toString().trim();
    }



    private String getGeminiSummary(String extractedText) {
        if (extractedText == null || extractedText.trim().isEmpty()) {
            System.err.println("Error: extractedText is empty!");
            return "Error: No text available for summarization.";
        }

        System.out.println("Sending text to Gemini API:\n" + extractedText);

        String url = "https://generativelanguage.googleapis.com/v1beta/models/gemini-2.0-flash:generateContent?key=" + geminiApiKey;

        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);

        String requestBody = "{ \"contents\": [{ \"parts\": [{ \"text\": \"Summarize this: " + extractedText + "\" }]}]}";
        HttpEntity<String> requestEntity = new HttpEntity<>(requestBody, headers);

        ResponseEntity<String> response = restTemplate.postForEntity(url, requestEntity, String.class);

        // Extract only the summary text from the JSON response
        try {
            ObjectMapper objectMapper = new ObjectMapper();
            JsonNode root = objectMapper.readTree(response.getBody());

            // Navigate to the text inside JSON response
            JsonNode textNode = root.path("candidates").get(0).path("content").path("parts").get(0).path("text");

            return textNode.asText();
        } catch (Exception e) {
            return "Error parsing response: " + e.getMessage();
        }
    }



    private File findDownloadedVttFile() {
        File dir = new File(System.getProperty("user.dir")); // Current directory
        File[] files = dir.listFiles((d, name) -> name.endsWith(".en.vtt")); // Only look for English subtitles

        if (files != null && files.length > 0) {
            // Pick the latest VTT file
            File latestFile = files[0];
            for (File file : files) {
                if (file.lastModified() > latestFile.lastModified()) {
                    latestFile = file;
                }
            }
            System.out.println("✅ Using VTT file: " + latestFile.getAbsolutePath());
            return latestFile;
        }

        System.err.println("❌ Error: No valid VTT file found!");
        return null;
    }


}
