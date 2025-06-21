package com.easydocs.ai;

import android.content.Context;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashMap;
import java.util.Map;
import java.util.Arrays;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class AIEngine {
    private Context context;
    private ExecutorService executor;
    private DocumentManager documentManager;
    private SimpleNLP nlpProcessor;
    private AdvancedRAG ragProcessor;

    public interface AICallback {
        void onResponse(String response);
        void onError(String error);
    }

    public AIEngine(Context context) {
        this.context = context;
        this.executor = Executors.newSingleThreadExecutor();
        this.documentManager = DocumentManager.getInstance();
        this.nlpProcessor = new SimpleNLP();
        this.ragProcessor = new AdvancedRAG();
    }

    public void processQuery(String query, AICallback callback) {
        executor.execute(() -> {
            try {
                // Check if documents are available
                if (!documentManager.hasDocuments()) {
                    callback.onResponse("I don't have any documents uploaded yet. Please upload some documents first so I can help answer your questions.");
                    return;
                }

                // Retrieve relevant documents using enhanced RAG
                VectorStore vectorStore = documentManager.getVectorStore();
                List<String> relevantChunks = vectorStore.retrieveRelevant(query, 8); // Increased for better coverage

                // Generate response using enhanced RAG
                String response = ragProcessor.generateResponse(query, relevantChunks, nlpProcessor);
                callback.onResponse(response);

            } catch (Exception e) {
                callback.onError(e.getMessage());
            }
        });
    }

    // Inner class for Advanced RAG processing
    private static class AdvancedRAG {

        public String generateResponse(String query, List<String> relevantChunks, SimpleNLP nlpProcessor) {

            if (relevantChunks.isEmpty()) {
                return "I couldn't find relevant information in the uploaded documents to answer your question. Try rephrasing your question or upload more relevant documents.";
            }

            // Rank chunks by relevance with improved scoring
            Map<String, Double> chunkScores = new HashMap<>();
            for (String chunk : relevantChunks) {
                double score = calculateEnhancedSimilarity(query, chunk, nlpProcessor);
                chunkScores.put(chunk, score);
            }

            // Sort chunks by score
            List<Map.Entry<String, Double>> sortedChunks = new ArrayList<>(chunkScores.entrySet());
            sortedChunks.sort(Map.Entry.<String, Double>comparingByValue().reversed());

            // Filter out low-relevance chunks with dynamic threshold
            List<String> filteredChunks = new ArrayList<>();
            double maxScore = sortedChunks.isEmpty() ? 0 : sortedChunks.get(0).getValue();
            double threshold = Math.max(0.1, maxScore * 0.3); // Dynamic threshold

            for (Map.Entry<String, Double> entry : sortedChunks) {
                if (entry.getValue() >= threshold) {
                    filteredChunks.add(entry.getKey());
                }
            }

            if (filteredChunks.isEmpty()) {
                return "I found some related information in your documents, but couldn't find a specific answer to your question. Try rephrasing your question or upload more relevant documents.";
            }

            // Generate comprehensive response
            return generateComprehensiveResponse(query, filteredChunks, nlpProcessor);
        }

        private double calculateEnhancedSimilarity(String query, String chunk, SimpleNLP nlpProcessor) {
            double baseScore = nlpProcessor.calculateSimilarity(query, chunk);

            // Boost score for exact keyword matches
            String[] queryWords = query.toLowerCase().split("\\s+");
            String chunkLower = chunk.toLowerCase();
            int exactMatches = 0;

            for (String word : queryWords) {
                if (word.length() > 2 && chunkLower.contains(word)) {
                    exactMatches++;
                }
            }

            double keywordBoost = (double) exactMatches / queryWords.length * 0.3;
            return Math.min(1.0, baseScore + keywordBoost);
        }

        private String generateComprehensiveResponse(String query, List<String> chunks, SimpleNLP nlpProcessor) {
            StringBuilder response = new StringBuilder();

            // Analyze query type with enhanced detection
            QueryAnalysis analysis = analyzeQueryComprehensively(query);

            // Generate response based on query type
            switch (analysis.primaryType) {
                case "definition":
                    response.append(generateDefinitionResponse(query, chunks, analysis));
                    break;
                case "comparison":
                    response.append(generateComparisonResponse(query, chunks, analysis));
                    break;
                case "list":
                    response.append(generateListResponse(query, chunks, analysis));
                    break;
                case "reason":
                    response.append(generateReasonResponse(query, chunks, analysis));
                    break;
                case "explanation":
                    response.append(generateExplanationResponse(query, chunks, analysis));
                    break;
                case "procedure":
                    response.append(generateProcedureResponse(query, chunks, analysis));
                    break;
                case "factual":
                    response.append(generateFactualResponse(query, chunks, analysis));
                    break;
                case "analysis":
                    response.append(generateAnalysisResponse(query, chunks, analysis));
                    break;
                case "summary":
                    response.append(generateSummaryResponse(query, chunks, analysis));
                    break;
                case "numerical":
                    response.append(generateNumericalResponse(query, chunks, analysis));
                    break;
                case "temporal":
                    response.append(generateTemporalResponse(query, chunks, analysis));
                    break;
                default:
                    response.append(generateAdaptiveResponse(query, chunks, analysis));
                    break;
            }

            // Add related information if available
            if (chunks.size() > 3) {
                response.append("\n\n📌 Additional relevant information:\n");
                for (int i = 3; i < Math.min(6, chunks.size()); i++) {
                    String additionalInfo = truncateText(chunks.get(i), 150);
                    response.append("• ").append(additionalInfo).append("\n");
                }
            }

            return response.toString().trim();
        }

        private QueryAnalysis analyzeQueryComprehensively(String query) {
            String lowerQuery = query.toLowerCase().trim();
            QueryAnalysis analysis = new QueryAnalysis();

            // Question word patterns
            if (lowerQuery.startsWith("what")) {
                if (lowerQuery.contains("what is") || lowerQuery.contains("what are") ||
                        lowerQuery.contains("what does") || lowerQuery.contains("define")) {
                    analysis.primaryType = "definition";
                } else if (lowerQuery.contains("what are the steps") || lowerQuery.contains("what is the procedure")) {
                    analysis.primaryType = "procedure";
                } else {
                    analysis.primaryType = "factual";
                }
            } else if (lowerQuery.startsWith("how")) {
                if (lowerQuery.contains("how to") || lowerQuery.contains("how do") || lowerQuery.contains("how can")) {
                    analysis.primaryType = "procedure";
                } else if (lowerQuery.contains("how does") || lowerQuery.contains("how is")) {
                    analysis.primaryType = "explanation";
                } else if (lowerQuery.contains("how many") || lowerQuery.contains("how much")) {
                    analysis.primaryType = "numerical";
                }
            } else if (lowerQuery.startsWith("why")) {
                analysis.primaryType = "reason";
            } else if (lowerQuery.startsWith("when")) {
                analysis.primaryType = "temporal";
            } else if (lowerQuery.startsWith("where")) {
                analysis.primaryType = "factual";
            } else if (lowerQuery.startsWith("who")) {
                analysis.primaryType = "factual";
            }

            // Content-based patterns
            if (lowerQuery.contains("compare") || lowerQuery.contains("difference") ||
                    lowerQuery.contains("vs") || lowerQuery.contains("versus")) {
                analysis.primaryType = "comparison";
            } else if (lowerQuery.contains("list") || lowerQuery.contains("types of") ||
                    lowerQuery.contains("examples") || lowerQuery.contains("kinds of")) {
                analysis.primaryType = "list";
            } else if (lowerQuery.contains("analyze") || lowerQuery.contains("analysis") ||
                    lowerQuery.contains("evaluate") || lowerQuery.contains("assess")) {
                analysis.primaryType = "analysis";
            } else if (lowerQuery.contains("summarize") || lowerQuery.contains("summary") ||
                    lowerQuery.contains("overview") || lowerQuery.contains("brief")) {
                analysis.primaryType = "summary";
            }

            // Extract key entities and concepts
            analysis.keyTerms = extractKeyTerms(lowerQuery);
            analysis.questionWords = extractQuestionWords(lowerQuery);

            return analysis;
        }

        private List<String> extractKeyTerms(String query) {
            List<String> keyTerms = new ArrayList<>();
            String[] words = query.split("\\s+");

            for (String word : words) {
                word = word.replaceAll("[^a-zA-Z0-9]", "").toLowerCase();
                if (word.length() > 3 && !isStopWord(word)) {
                    keyTerms.add(word);
                }
            }

            return keyTerms;
        }

        private List<String> extractQuestionWords(String query) {
            List<String> questionWords = Arrays.asList("what", "how", "why", "when", "where", "who", "which");
            List<String> found = new ArrayList<>();

            for (String qw : questionWords) {
                if (query.contains(qw)) {
                    found.add(qw);
                }
            }

            return found;
        }

        private boolean isStopWord(String word) {
            String[] stopWords = {"the", "and", "for", "are", "but", "not", "you", "all", "can", "had",
                    "her", "was", "one", "our", "out", "day", "get", "has", "him", "his",
                    "how", "its", "may", "new", "now", "old", "see", "two", "way", "who",
                    "boy", "did", "she", "use", "her", "now", "oil", "sit", "set"};

            for (String stop : stopWords) {
                if (word.equals(stop)) {
                    return true;
                }
            }
            return false;
        }

        // Enhanced response generators
        private String generateDefinitionResponse(String query, List<String> chunks, QueryAnalysis analysis) {
            StringBuilder response = new StringBuilder();
            response.append("📖 **Definition:**\n\n");

            String bestDefinition = findBestDefinition(chunks, analysis.keyTerms);
            if (bestDefinition != null) {
                response.append(bestDefinition).append("\n\n");
            }

            // Add supporting context
            for (int i = 0; i < Math.min(2, chunks.size()); i++) {
                if (!chunks.get(i).equals(bestDefinition)) {
                    response.append("**Additional context:** ").append(truncateText(chunks.get(i), 200)).append("\n\n");
                }
            }

            return response.toString();
        }

        private String findBestDefinition(List<String> chunks, List<String> keyTerms) {
            for (String chunk : chunks) {
                String lower = chunk.toLowerCase();
                if (lower.contains("is defined as") || lower.contains("refers to") ||
                        lower.contains("means") || lower.contains("definition of")) {
                    return chunk.trim();
                }
            }

            // Fallback to chunk with most key terms
            String bestChunk = null;
            int maxMatches = 0;

            for (String chunk : chunks) {
                int matches = 0;
                String chunkLower = chunk.toLowerCase();
                for (String term : keyTerms) {
                    if (chunkLower.contains(term)) {
                        matches++;
                    }
                }
                if (matches > maxMatches) {
                    maxMatches = matches;
                    bestChunk = chunk;
                }
            }

            return bestChunk;
        }

        private String generateComparisonResponse(String query, List<String> chunks, QueryAnalysis analysis) {
            StringBuilder response = new StringBuilder();
            response.append("🔍 **Comparison Analysis:**\n\n");

            for (int i = 0; i < Math.min(4, chunks.size()); i++) {
                response.append("**Point ").append(i + 1).append(":** ")
                        .append(truncateText(chunks.get(i), 250)).append("\n\n");
            }

            return response.toString();
        }

        private String generateListResponse(String query, List<String> chunks, QueryAnalysis analysis) {
            StringBuilder response = new StringBuilder();
            response.append("📋 **List of Items:**\n\n");

            List<String> listItems = extractListItems(chunks);

            if (listItems.size() > 0) {
                for (int i = 0; i < Math.min(8, listItems.size()); i++) {
                    response.append("• ").append(listItems.get(i)).append("\n");
                }
            } else {
                for (int i = 0; i < Math.min(5, chunks.size()); i++) {
                    response.append("• ").append(truncateText(chunks.get(i), 200)).append("\n\n");
                }
            }

            return response.toString();
        }

        private List<String> extractListItems(List<String> chunks) {
            List<String> items = new ArrayList<>();

            for (String chunk : chunks) {
                // Look for numbered lists
                Pattern numberedPattern = Pattern.compile("\\d+[.):]\\s*(.+?)(?=\\d+[.):]|$)", Pattern.DOTALL);
                Matcher matcher = numberedPattern.matcher(chunk);
                while (matcher.find()) {
                    items.add(matcher.group(1).trim());
                }

                // Look for bullet points
                Pattern bulletPattern = Pattern.compile("[•·-]\\s*(.+?)(?=[•·-]|$)", Pattern.DOTALL);
                matcher = bulletPattern.matcher(chunk);
                while (matcher.find()) {
                    items.add(matcher.group(1).trim());
                }
            }

            return items;
        }

        private String generateReasonResponse(String query, List<String> chunks, QueryAnalysis analysis) {
            StringBuilder response = new StringBuilder();
            response.append("💭 **Reasons/Explanations:**\n\n");

            for (String chunk : chunks) {
                String lower = chunk.toLowerCase();
                if (lower.contains("because") || lower.contains("reason") || lower.contains("due to") ||
                        lower.contains("since") || lower.contains("as a result") || lower.contains("therefore")) {
                    response.append("• ").append(truncateText(chunk, 300)).append("\n\n");
                }
            }

            // Fallback if no reason-specific content found
            if (response.length() < 50 && !chunks.isEmpty()) {
                response.append("• ").append(truncateText(chunks.get(0), 300)).append("\n\n");
            }

            return response.toString();
        }

        private String generateExplanationResponse(String query, List<String> chunks, QueryAnalysis analysis) {
            StringBuilder response = new StringBuilder();
            response.append("💡 **Explanation:**\n\n");

            for (int i = 0; i < Math.min(3, chunks.size()); i++) {
                response.append(chunks.get(i).trim()).append("\n\n");
            }

            return response.toString();
        }

        private String generateProcedureResponse(String query, List<String> chunks, QueryAnalysis analysis) {
            StringBuilder response = new StringBuilder();
            response.append("🛠 **Procedure/Steps:**\n\n");

            List<String> steps = extractSteps(chunks);

            if (steps.size() > 0) {
                for (int i = 0; i < steps.size(); i++) {
                    response.append("**Step ").append(i + 1).append(":** ").append(steps.get(i)).append("\n\n");
                }
            } else {
                for (String chunk : chunks) {
                    if (chunk.toLowerCase().contains("step") || chunk.toLowerCase().contains("procedure") ||
                            chunk.toLowerCase().contains("process") || chunk.toLowerCase().contains("method")) {
                        response.append("• ").append(truncateText(chunk, 250)).append("\n\n");
                    }
                }
            }

            return response.toString();
        }

        private List<String> extractSteps(List<String> chunks) {
            List<String> steps = new ArrayList<>();

            for (String chunk : chunks) {
                Pattern stepPattern = Pattern.compile("(?:step\\s*\\d+|\\d+[.):]|first|second|third|then|next|finally)[:\\.\\s]*(.+?)(?=(?:step\\s*\\d+|\\d+[.):]|first|second|third|then|next|finally)|$)",
                        Pattern.CASE_INSENSITIVE | Pattern.DOTALL);
                Matcher matcher = stepPattern.matcher(chunk);
                while (matcher.find()) {
                    String step = matcher.group(1).trim();
                    if (step.length() > 10) {
                        steps.add(step);
                    }
                }
            }

            return steps;
        }

        private String generateFactualResponse(String query, List<String> chunks, QueryAnalysis analysis) {
            StringBuilder response = new StringBuilder();
            response.append("📊 **Facts:**\n\n");

            for (int i = 0; i < Math.min(4, chunks.size()); i++) {
                response.append("• ").append(truncateText(chunks.get(i), 200)).append("\n\n");
            }

            return response.toString();
        }

        private String generateAnalysisResponse(String query, List<String> chunks, QueryAnalysis analysis) {
            StringBuilder response = new StringBuilder();
            response.append("🔬 **Analysis:**\n\n");

            for (int i = 0; i < Math.min(3, chunks.size()); i++) {
                response.append("**Aspect ").append(i + 1).append(":** ")
                        .append(chunks.get(i).trim()).append("\n\n");
            }

            return response.toString();
        }

        private String generateSummaryResponse(String query, List<String> chunks, QueryAnalysis analysis) {
            StringBuilder response = new StringBuilder();
            response.append("📝 **Summary:**\n\n");

            // Combine key information
            StringBuilder summary = new StringBuilder();
            for (String chunk : chunks) {
                summary.append(chunk).append(" ");
            }

            String combinedText = summary.toString();
            response.append(truncateText(combinedText, 500)).append("\n\n");

            return response.toString();
        }

        private String generateNumericalResponse(String query, List<String> chunks, QueryAnalysis analysis) {
            StringBuilder response = new StringBuilder();
            response.append("🔢 **Numerical Information:**\n\n");

            for (String chunk : chunks) {
                if (containsNumbers(chunk)) {
                    response.append("• ").append(truncateText(chunk, 200)).append("\n\n");
                }
            }

            return response.toString();
        }

        private String generateTemporalResponse(String query, List<String> chunks, QueryAnalysis analysis) {
            StringBuilder response = new StringBuilder();
            response.append("⏰ **Time-related Information:**\n\n");

            for (String chunk : chunks) {
                if (containsTimeReferences(chunk)) {
                    response.append("• ").append(truncateText(chunk, 200)).append("\n\n");
                }
            }

            return response.toString();
        }

        private String generateAdaptiveResponse(String query, List<String> chunks, QueryAnalysis analysis) {
            StringBuilder response = new StringBuilder();
            response.append("📄 **Based on your documents:**\n\n");

            for (int i = 0; i < Math.min(4, chunks.size()); i++) {
                response.append("• ").append(truncateText(chunks.get(i), 250)).append("\n\n");
            }

            return response.toString();
        }

        private boolean containsNumbers(String text) {
            return text.matches(".*\\d+.*");
        }

        private boolean containsTimeReferences(String text) {
            String lower = text.toLowerCase();
            return lower.contains("year") || lower.contains("month") || lower.contains("day") ||
                    lower.contains("time") || lower.contains("date") || lower.contains("when") ||
                    lower.contains("before") || lower.contains("after") || lower.contains("during");
        }

        private String truncateText(String text, int maxLength) {
            if (text.length() <= maxLength) {
                return text.trim();
            }

            String truncated = text.substring(0, maxLength);
            int lastSpace = truncated.lastIndexOf(' ');
            if (lastSpace > maxLength * 0.8) {
                truncated = truncated.substring(0, lastSpace);
            }

            return truncated.trim() + "...";
        }

        // Inner class for query analysis
        private static class QueryAnalysis {
            String primaryType = "general";
            List<String> keyTerms = new ArrayList<>();
            List<String> questionWords = new ArrayList<>();
        }
    }

    public void shutdown() {
        if (executor != null && !executor.isShutdown()) {
            executor.shutdown();
        }
    }
}