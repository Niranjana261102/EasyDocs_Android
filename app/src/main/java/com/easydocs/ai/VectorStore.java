package com.easydocs.ai;

import android.util.Log;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.HashMap;
import java.util.stream.Collectors;
import java.util.Arrays;
import java.util.Collections;
import java.util.regex.Pattern;
import java.util.regex.Matcher;

public class VectorStore {
    private List<DocumentChunk> chunks;
    private SimpleNLP nlpProcessor;
    private DocumentProcessor documentProcessor;
    private static final int CHUNK_SIZE = 400;
    private static final int CHUNK_OVERLAP = 50;
    private static final int DEFAULT_TOP_K = 5;

    public VectorStore() {
        this.chunks = new ArrayList<>();
        this.nlpProcessor = new SimpleNLP();
        this.documentProcessor = new DocumentProcessor();
    }

    public void addDocument(DocumentItem document) {
        try {
            // Process document based on its type
            String extractedContent = documentProcessor.extractContent(document);
            if (extractedContent != null && !extractedContent.trim().isEmpty()) {
                List<DocumentChunk> documentChunks = createSmartChunks(document, extractedContent);
                chunks.addAll(documentChunks);
                Log.i("VectorStore", "Added " + documentChunks.size() + " chunks from " + document.getFileName());
            } else {
                Log.w("VectorStore", "No content extracted from document: " + document.getFileName());
            }
        } catch (Exception e) {
            Log.e("VectorStore", "Error processing document: " + document.getFileName(), e);
        }
    }

    /**
     * Main method to answer questions based on document content
     */
    public String answerQuestion(String question) {
        return answerQuestion(question, DEFAULT_TOP_K);
    }

    public String answerQuestion(String question, int topK) {
        if (chunks.isEmpty()) {
            return "I don't have any documents to search through. Please upload some documents first.";
        }

        if (question == null || question.trim().isEmpty()) {
            return "Please provide a valid question.";
        }

        // Retrieve relevant chunks
        List<DocumentChunk> relevantChunks = retrieveRelevantChunks(question, topK);

        if (relevantChunks.isEmpty()) {
            return "I couldn't find relevant information to answer your question in the uploaded documents.";
        }

        // Generate answer based on relevant chunks
        return generateAnswer(question, relevantChunks);
    }

    public List<String> retrieveRelevant(String query, int topK) {
        return retrieveRelevantChunks(query, topK).stream()
                .map(DocumentChunk::getContent)
                .collect(Collectors.toList());
    }

    private List<DocumentChunk> retrieveRelevantChunks(String query, int topK) {
        if (chunks.isEmpty()) {
            return new ArrayList<>();
        }

        Map<DocumentChunk, Double> scores = new HashMap<>();

        // Calculate similarity scores for all chunks
        for (DocumentChunk chunk : chunks) {
            double score = calculateEnhancedSimilarity(query, chunk);
            scores.put(chunk, score);
        }

        // Sort by relevance score and return top results
        return scores.entrySet().stream()
                .sorted(Map.Entry.<DocumentChunk, Double>comparingByValue().reversed())
                .limit(topK)
                .map(Map.Entry::getKey)
                .collect(Collectors.toList());
    }

    private String generateAnswer(String question, List<DocumentChunk> relevantChunks) {
        StringBuilder answer = new StringBuilder();

        // Check if it's a direct factual question
        if (isDirectFactualQuestion(question)) {
            String directAnswer = extractDirectAnswer(question, relevantChunks);
            if (directAnswer != null) {
                answer.append(directAnswer).append("\n\n");
            }
        }

        // Provide comprehensive answer with context
        answer.append("Based on the documents, here's what I found:\n\n");

        for (int i = 0; i < Math.min(3, relevantChunks.size()); i++) {
            DocumentChunk chunk = relevantChunks.get(i);
            String relevantPart = extractRelevantPart(question, chunk.getContent());

            answer.append("From ").append(chunk.getDocumentName()).append(":\n");
            answer.append(relevantPart).append("\n\n");
        }

        // Add source information
        if (relevantChunks.size() > 3) {
            answer.append("(Information found in ").append(relevantChunks.size())
                    .append(" sections across your documents)");
        }

        return answer.toString().trim();
    }

    private boolean isDirectFactualQuestion(String question) {
        String lowerQuestion = question.toLowerCase();
        String[] factualStarters = {
                "what is", "who is", "when is", "where is", "how much", "how many",
                "what are", "who are", "define", "explain", "what does", "how does"
        };

        for (String starter : factualStarters) {
            if (lowerQuestion.startsWith(starter)) {
                return true;
            }
        }
        return false;
    }

    private String extractDirectAnswer(String question, List<DocumentChunk> chunks) {
        String lowerQuestion = question.toLowerCase();

        for (DocumentChunk chunk : chunks) {
            String content = chunk.getContent();
            String lowerContent = content.toLowerCase();

            // Look for direct definitions or explanations
            if (lowerQuestion.contains("what is") || lowerQuestion.contains("define")) {
                String term = extractTerm(question);
                if (term != null) {
                    String definition = findDefinition(term, content);
                    if (definition != null) {
                        return definition;
                    }
                }
            }

            // Look for numerical answers
            if (lowerQuestion.contains("how many") || lowerQuestion.contains("how much")) {
                String numericalAnswer = findNumericalAnswer(question, content);
                if (numericalAnswer != null) {
                    return numericalAnswer;
                }
            }
        }

        return null;
    }

    private String extractTerm(String question) {
        String[] patterns = {
                "what is (.+?)\\?",
                "define (.+?)\\?",
                "what does (.+?) mean"
        };

        for (String pattern : patterns) {
            Pattern p = Pattern.compile(pattern, Pattern.CASE_INSENSITIVE);
            Matcher m = p.matcher(question);
            if (m.find()) {
                return m.group(1).trim();
            }
        }
        return null;
    }

    private String findDefinition(String term, String content) {
        String[] sentences = content.split("\\. ");
        String lowerTerm = term.toLowerCase();

        for (String sentence : sentences) {
            String lowerSentence = sentence.toLowerCase();
            if (lowerSentence.contains(lowerTerm)) {
                // Look for definition patterns
                if (lowerSentence.contains(" is ") || lowerSentence.contains(" means ") ||
                        lowerSentence.contains(" refers to ") || lowerSentence.contains(" defined as ")) {
                    return sentence.trim() + ".";
                }
            }
        }
        return null;
    }

    private String findNumericalAnswer(String question, String content) {
        // Look for numbers in the content that might answer the question
        Pattern numberPattern = Pattern.compile("\\b\\d+(?:\\.\\d+)?\\b");
        Matcher matcher = numberPattern.matcher(content);

        String[] sentences = content.split("\\. ");
        for (String sentence : sentences) {
            if (hasKeywordsFromQuestion(question, sentence)) {
                Matcher numMatcher = numberPattern.matcher(sentence);
                if (numMatcher.find()) {
                    return sentence.trim() + ".";
                }
            }
        }
        return null;
    }

    private boolean hasKeywordsFromQuestion(String question, String sentence) {
        String[] questionWords = question.toLowerCase().split("\\s+");
        String lowerSentence = sentence.toLowerCase();

        int matchCount = 0;
        for (String word : questionWords) {
            if (word.length() > 2 && !isStopWord(word) && lowerSentence.contains(word)) {
                matchCount++;
            }
        }

        return matchCount >= Math.min(2, questionWords.length / 2);
    }

    private boolean isStopWord(String word) {
        String[] stopWords = {"is", "are", "was", "were", "the", "a", "an", "and", "or", "but",
                "in", "on", "at", "to", "for", "of", "with", "by", "how", "what",
                "when", "where", "why", "who", "which", "that", "this", "these", "those"};
        return Arrays.asList(stopWords).contains(word.toLowerCase());
    }

    private String extractRelevantPart(String question, String content) {
        String[] sentences = content.split("\\. ");
        List<String> relevantSentences = new ArrayList<>();

        for (String sentence : sentences) {
            if (isRelevantSentence(question, sentence)) {
                relevantSentences.add(sentence.trim());
            }
        }

        if (relevantSentences.isEmpty()) {
            // If no specific sentences found, return first few sentences
            return String.join(". ", Arrays.copyOf(sentences, Math.min(3, sentences.length))) + ".";
        }

        return String.join(". ", relevantSentences) + ".";
    }

    private boolean isRelevantSentence(String question, String sentence) {
        String[] questionWords = question.toLowerCase().split("\\s+");
        String lowerSentence = sentence.toLowerCase();

        int relevantWords = 0;
        for (String word : questionWords) {
            if (word.length() > 2 && !isStopWord(word) && lowerSentence.contains(word)) {
                relevantWords++;
            }
        }

        return relevantWords >= 2 || relevantWords >= questionWords.length * 0.3;
    }

    private List<DocumentChunk> createSmartChunks(DocumentItem document, String content) {
        List<DocumentChunk> documentChunks = new ArrayList<>();

        // First, try to split by paragraphs
        String[] paragraphs = content.split("\n\n+");

        for (String paragraph : paragraphs) {
            paragraph = paragraph.trim();
            if (paragraph.isEmpty()) continue;

            if (paragraph.length() <= CHUNK_SIZE) {
                // Paragraph fits in one chunk
                documentChunks.add(new DocumentChunk(document.getFileName(), paragraph, document.getFileType()));
            } else {
                // Split large paragraphs by sentences
                List<DocumentChunk> sentenceChunks = splitBySentences(document.getFileName(), paragraph, document.getFileType());
                documentChunks.addAll(sentenceChunks);
            }
        }

        // If no paragraphs found, fall back to sentence-based chunking
        if (documentChunks.isEmpty()) {
            documentChunks = splitBySentences(document.getFileName(), content, document.getFileType());
        }

        return documentChunks;
    }

    private List<DocumentChunk> splitBySentences(String documentName, String content, String fileType) {
        List<DocumentChunk> chunks = new ArrayList<>();

        // Split by sentences (improved regex)
        String[] sentences = content.split("(?<=[.!?])\\s+");

        StringBuilder currentChunk = new StringBuilder();
        List<String> currentSentences = new ArrayList<>();

        for (String sentence : sentences) {
            sentence = sentence.trim();
            if (sentence.isEmpty()) continue;

            // Check if adding this sentence would exceed chunk size
            if (currentChunk.length() + sentence.length() + 1 > CHUNK_SIZE && currentChunk.length() > 0) {
                // Create chunk with current sentences
                chunks.add(new DocumentChunk(documentName, currentChunk.toString().trim(), fileType));

                // Start new chunk with overlap
                currentChunk = new StringBuilder();
                currentSentences = createOverlappedStart(currentSentences);

                // Add overlapped sentences to new chunk
                for (String overlappedSentence : currentSentences) {
                    currentChunk.append(overlappedSentence).append(" ");
                }
            }

            currentChunk.append(sentence).append(" ");
            currentSentences.add(sentence);
        }

        // Add the last chunk if it has content
        if (currentChunk.length() > 0) {
            chunks.add(new DocumentChunk(documentName, currentChunk.toString().trim(), fileType));
        }

        return chunks;
    }

    private List<String> createOverlappedStart(List<String> sentences) {
        if (sentences.size() <= 1) return new ArrayList<>();

        // Take last 1-2 sentences for overlap
        int overlapStart = Math.max(0, sentences.size() - 2);
        return new ArrayList<>(sentences.subList(overlapStart, sentences.size()));
    }

    private double calculateEnhancedSimilarity(String query, DocumentChunk chunk) {
        String chunkContent = chunk.getContent();

        // Basic similarity score
        double basicScore = nlpProcessor.calculateSimilarity(query, chunkContent);

        // Boost score for exact matches
        double exactMatchBoost = calculateExactMatchBoost(query, chunkContent);

        // Boost score for important keywords
        double keywordBoost = calculateKeywordBoost(query, chunkContent);

        // Boost score for document title relevance
        double titleBoost = calculateTitleBoost(query, chunk.getDocumentName());

        // Boost score for question-specific terms
        double questionBoost = calculateQuestionSpecificBoost(query, chunkContent);

        // Combine scores with weights
        return basicScore * 0.4 + exactMatchBoost * 0.25 + keywordBoost * 0.15 +
                titleBoost * 0.1 + questionBoost * 0.1;
    }

    private double calculateQuestionSpecificBoost(String query, String content) {
        String lowerQuery = query.toLowerCase();
        String lowerContent = content.toLowerCase();

        // Boost for question words and their related content
        if (lowerQuery.contains("what") && (lowerContent.contains("definition") || lowerContent.contains("meaning"))) {
            return 0.3;
        }
        if (lowerQuery.contains("how") && (lowerContent.contains("process") || lowerContent.contains("method"))) {
            return 0.3;
        }
        if (lowerQuery.contains("why") && (lowerContent.contains("because") || lowerContent.contains("reason"))) {
            return 0.3;
        }
        if (lowerQuery.contains("when") && (lowerContent.contains("date") || lowerContent.contains("time"))) {
            return 0.3;
        }

        return 0.0;
    }

    private double calculateExactMatchBoost(String query, String content) {
        String lowerQuery = query.toLowerCase();
        String lowerContent = content.toLowerCase();

        // Check for exact phrase matches
        if (lowerContent.contains(lowerQuery)) {
            return 1.0;
        }

        // Check for exact word matches
        String[] queryWords = lowerQuery.split("\\s+");
        int exactMatches = 0;

        for (String word : queryWords) {
            if (word.length() > 2 && !isStopWord(word) && lowerContent.contains(" " + word + " ")) {
                exactMatches++;
            }
        }

        return queryWords.length > 0 ? (double) exactMatches / queryWords.length : 0.0;
    }

    private double calculateKeywordBoost(String query, String content) {
        // List of important keywords that should boost relevance
        String[] importantKeywords = {"important", "key", "main", "primary", "essential", "crucial", "significant", "definition", "meaning"};

        String lowerContent = content.toLowerCase();
        int keywordCount = 0;

        for (String keyword : importantKeywords) {
            if (lowerContent.contains(keyword)) {
                keywordCount++;
            }
        }

        return Math.min(keywordCount * 0.1, 0.5); // Cap at 0.5
    }

    private double calculateTitleBoost(String query, String documentName) {
        if (documentName == null) return 0.0;

        return nlpProcessor.calculateSimilarity(query, documentName) * 0.3;
    }

    public void clearChunks() {
        chunks.clear();
    }

    public int getChunkCount() {
        return chunks.size();
    }

    public List<String> getAllChunks() {
        return chunks.stream()
                .map(DocumentChunk::getContent)
                .collect(Collectors.toList());
    }

    public List<String> getDocumentNames() {
        return chunks.stream()
                .map(DocumentChunk::getDocumentName)
                .distinct()
                .collect(Collectors.toList());
    }

    // Inner class for document chunks
    private static class DocumentChunk {
        private String documentName;
        private String content;
        private String fileType;
        private long timestamp;

        public DocumentChunk(String documentName, String content, String fileType) {
            this.documentName = documentName;
            this.content = content;
            this.fileType = fileType;
            this.timestamp = System.currentTimeMillis();
        }

        public String getDocumentName() {
            return documentName;
        }

        public String getContent() {
            return content;
        }

        public String getFileType() {
            return fileType;
        }

        public long getTimestamp() {
            return timestamp;
        }

        @Override
        public String toString() {
            return "DocumentChunk{" +
                    "documentName='" + documentName + '\'' +
                    ", fileType='" + fileType + '\'' +
                    ", contentLength=" + content.length() +
                    ", timestamp=" + timestamp +
                    '}';
        }
    }

    // Document processor for different file types
    private static class DocumentProcessor {

        public String extractContent(DocumentItem document) {
            String fileType = document.getFileType();
            String content = document.getContent();

            if (content == null || content.trim().isEmpty()) {
                Log.w("DocumentProcessor", "Document content is null or empty for: " + document.getFileName());
                return "";
            }

            switch (fileType.toLowerCase()) {
                case "pdf":
                    return processPdfContent(content);
                case "doc":
                case "docx":
                    return processWordContent(content);
                case "xml":
                    return processXmlContent(content);
                case "html":
                    return processHtmlContent(content);
                case "txt":
                    return processTextContent(content);
                default:
                    return processGenericContent(content);
            }
        }

        private String processPdfContent(String content) {
            // Remove PDF-specific artifacts and clean up text
            return content.replaceAll("\\s+", " ")
                    .replaceAll("\\n+", "\n\n")
                    .trim();
        }

        private String processWordContent(String content) {
            // Remove Word-specific formatting artifacts
            return content.replaceAll("\\r\\n", "\n")
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        private String processXmlContent(String content) {
            // Remove XML tags and extract text content
            return content.replaceAll("<[^>]+>", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        private String processHtmlContent(String content) {
            // Remove HTML tags and extract text content
            return content.replaceAll("<[^>]+>", "")
                    .replaceAll("&[^;]+;", "")
                    .replaceAll("\\s+", " ")
                    .trim();
        }

        private String processTextContent(String content) {
            // Basic text processing
            return content.trim();
        }

        private String processGenericContent(String content) {
            // Generic content processing
            return content.replaceAll("\\s+", " ").trim();
        }
    }
}