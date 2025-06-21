package com.easydocs.ai;

import java.util.*;
import java.util.regex.Pattern;
import java.util.regex.Matcher;
import java.io.*;
import java.nio.file.Files;
import java.nio.file.Paths;

// You'll need to add these dependencies to your project:
// For PDF: Apache PDFBox
// For DOC/DOCX: Apache POI
// For XML: Built-in Java XML parsing

public class SimpleNLP {
    private Set<String> stopWords;
    private Map<String, Set<String>> synonyms;
    private Map<String, DocumentContent> documentDatabase;
    private List<String> questionWords;

    public SimpleNLP() {
        initializeStopWords();
        initializeSynonyms();
        initializeQuestionWords();
        documentDatabase = new HashMap<>();
    }

    // Document content holder
    public static class DocumentContent {
        private String fileName;
        private String fullText;
        private List<String> sentences;
        private List<String> paragraphs;
        private Map<String, String> metadata;

        public DocumentContent(String fileName, String fullText) {
            this.fileName = fileName;
            this.fullText = fullText;
            this.sentences = splitIntoSentences(fullText);
            this.paragraphs = splitIntoParagraphs(fullText);
            this.metadata = new HashMap<>();
        }

        private List<String> splitIntoSentences(String text) {
            List<String> sentences = new ArrayList<>();
            String[] splits = text.split("[.!?]+");
            for (String sentence : splits) {
                String trimmed = sentence.trim();
                if (trimmed.length() > 10) {
                    sentences.add(trimmed);
                }
            }
            return sentences;
        }

        private List<String> splitIntoParagraphs(String text) {
            List<String> paragraphs = new ArrayList<>();
            String[] splits = text.split("\n\n+");
            for (String paragraph : splits) {
                String trimmed = paragraph.trim().replaceAll("\n", " ");
                if (trimmed.length() > 20) {
                    paragraphs.add(trimmed);
                }
            }
            return paragraphs;
        }

        // Getters
        public String getFileName() { return fileName; }
        public String getFullText() { return fullText; }
        public List<String> getSentences() { return sentences; }
        public List<String> getParagraphs() { return paragraphs; }
        public Map<String, String> getMetadata() { return metadata; }
    }

    // Answer holder
    public static class Answer {
        private String answer;
        private double confidence;
        private String source;
        private String context;

        public Answer(String answer, double confidence, String source, String context) {
            this.answer = answer;
            this.confidence = confidence;
            this.source = source;
            this.context = context;
        }

        public String getAnswer() { return answer; }
        public double getConfidence() { return confidence; }
        public String getSource() { return source; }
        public String getContext() { return context; }

        @Override
        public String toString() {
            return String.format("Answer: %s\nConfidence: %.2f\nSource: %s\nContext: %s",
                    answer, confidence, source, context);
        }
    }

    private static class ScoredSegment {
        String text;
        double score;
        String source;
        String segmentType;

        ScoredSegment(String text, double score, String source, String segmentType) {
            this.text = text;
            this.score = score;
            this.source = source;
            this.segmentType = segmentType;
        }
    }

    private enum QuestionType {
        WHAT, WHO, WHERE, WHEN, WHY, HOW, DEFINE, LIST, COMPARE, GENERAL
    }

    private void initializeStopWords() {
        stopWords = new HashSet<>(Arrays.asList(
                "the", "a", "an", "and", "or", "but", "in", "on", "at", "to", "for", "of", "with", "by",
                "is", "are", "was", "were", "be", "been", "have", "has", "had", "do", "does", "did",
                "will", "would", "could", "should", "may", "might", "must", "can", "this", "that",
                "these", "those", "i", "you", "he", "she", "it", "we", "they", "me", "him", "her",
                "us", "them", "my", "your", "his", "its", "our", "their", "mine", "yours", "ours",
                "theirs", "myself", "yourself", "himself", "herself", "itself", "ourselves",
                "yourselves", "themselves", "what", "which", "who", "whom", "whose", "where", "when",
                "why", "how", "all", "any", "both", "each", "few", "more", "most", "other", "some",
                "such", "no", "nor", "not", "only", "own", "same", "so", "than", "too", "very", "just"
        ));
    }

    private void initializeSynonyms() {
        synonyms = new HashMap<>();
        addSynonymGroup("big", "large", "huge", "enormous", "massive", "giant", "vast", "immense");
        addSynonymGroup("small", "little", "tiny", "minute", "compact", "mini", "petite");
        addSynonymGroup("good", "excellent", "great", "wonderful", "fantastic", "amazing", "superb", "outstanding");
        addSynonymGroup("bad", "terrible", "awful", "horrible", "poor", "dreadful", "terrible");
        addSynonymGroup("fast", "quick", "rapid", "swift", "speedy", "hasty", "brisk");
        addSynonymGroup("slow", "sluggish", "gradual", "leisurely", "delayed", "tardy");
        addSynonymGroup("happy", "joyful", "cheerful", "glad", "pleased", "delighted", "content");
        addSynonymGroup("sad", "unhappy", "depressed", "gloomy", "melancholy", "sorrowful");
        addSynonymGroup("important", "significant", "crucial", "vital", "essential", "key", "critical");
        addSynonymGroup("help", "assist", "aid", "support", "facilitate", "enable");
        addSynonymGroup("show", "display", "demonstrate", "exhibit", "present", "reveal");
        addSynonymGroup("create", "make", "build", "construct", "develop", "generate", "produce");
        addSynonymGroup("use", "utilize", "employ", "apply", "implement", "adopt");
        addSynonymGroup("find", "discover", "locate", "identify", "detect", "uncover");
        addSynonymGroup("explain", "describe", "clarify", "elaborate", "detail", "illustrate");
        addSynonymGroup("method", "approach", "technique", "procedure", "process", "way");
        addSynonymGroup("result", "outcome", "consequence", "effect", "conclusion", "finding");
        addSynonymGroup("problem", "issue", "challenge", "difficulty", "obstacle", "trouble");
        addSynonymGroup("solution", "answer", "resolution", "fix", "remedy", "approach");
    }

    private void initializeQuestionWords() {
        questionWords = Arrays.asList(
                "what", "who", "where", "when", "why", "how", "which", "whose", "whom",
                "define", "explain", "describe", "list", "name", "identify", "compare",
                "contrast", "analyze", "summarize", "evaluate", "discuss", "outline"
        );
    }

    private void addSynonymGroup(String... words) {
        Set<String> synonymSet = new HashSet<>(Arrays.asList(words));
        for (String word : words) {
            synonyms.put(word.toLowerCase(), synonymSet);
        }
    }

    // ==================== ORIGINAL SIMPLENL METHODS ====================

    public double calculateSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.trim().isEmpty() || text2.trim().isEmpty()) {
            return 0.0;
        }

        Set<String> words1 = extractKeywords(text1.toLowerCase());
        Set<String> words2 = extractKeywords(text2.toLowerCase());

        double basicSimilarity = calculateJaccardSimilarity(words1, words2);
        double synonymSimilarity = calculateSynonymSimilarity(words1, words2);
        double substringSimilarity = calculateSubstringSimilarity(text1, text2);
        double tfIdfSimilarity = calculateTfIdfSimilarity(words1, words2, text1, text2);

        return (basicSimilarity * 0.3 + synonymSimilarity * 0.3 + substringSimilarity * 0.2 + tfIdfSimilarity * 0.2);
    }

    public boolean isRelevant(String query, String text) {
        return calculateSimilarity(query, text) > 0.15;
    }

    public List<String> extractImportantPhrases(String text) {
        List<String> phrases = new ArrayList<>();
        String[] sentences = text.split("[.!?]+");

        for (String sentence : sentences) {
            String[] words = sentence.trim().split("\\s+");
            if (words.length >= 2 && words.length <= 5) {
                StringBuilder phrase = new StringBuilder();
                boolean hasImportantWord = false;

                for (String word : words) {
                    String cleanWord = word.replaceAll("[^a-zA-Z]", "").toLowerCase();
                    if (!stopWords.contains(cleanWord) && cleanWord.length() > 2) {
                        hasImportantWord = true;
                    }
                    if (phrase.length() > 0) phrase.append(" ");
                    phrase.append(cleanWord);
                }

                if (hasImportantWord) {
                    phrases.add(phrase.toString());
                }
            }
        }

        return phrases;
    }

    public double calculateSemanticSimilarity(String text1, String text2) {
        double lexicalSim = calculateSimilarity(text1, text2);
        double phraseSim = calculatePhraseSimilarity(text1, text2);
        double positionSim = calculatePositionalSimilarity(text1, text2);

        return (lexicalSim * 0.5 + phraseSim * 0.3 + positionSim * 0.2);
    }

    // ==================== DOCUMENT PROCESSING METHODS ====================

    public void addDocument(String filePath) throws IOException {
        String fileName = new File(filePath).getName();
        String extension = getFileExtension(fileName).toLowerCase();
        String content = "";

        switch (extension) {
            case "txt":
                content = readTextFile(filePath);
                break;
            case "pdf":
                content = readPDFFile(filePath);
                break;
            case "doc":
            case "docx":
                content = readWordFile(filePath);
                break;
            case "xml":
                content = readXMLFile(filePath);
                break;
            default:
                throw new IllegalArgumentException("Unsupported file format: " + extension);
        }

        DocumentContent docContent = new DocumentContent(fileName, content);
        documentDatabase.put(fileName, docContent);
    }

    public void addDocumentFromText(String fileName, String content) {
        DocumentContent docContent = new DocumentContent(fileName, content);
        documentDatabase.put(fileName, docContent);
    }

    // ==================== QUESTION ANSWERING METHODS ====================

    public Answer answerQuestion(String question) {
        if (documentDatabase.isEmpty()) {
            return new Answer("No documents loaded. Please add documents first.", 0.0, "System", "");
        }

        String normalizedQuestion = question.toLowerCase().trim();
        QuestionType questionType = identifyQuestionType(normalizedQuestion);

        List<ScoredSegment> candidateSegments = findRelevantSegments(normalizedQuestion);

        if (candidateSegments.isEmpty()) {
            return new Answer("I couldn't find relevant information to answer your question.", 0.0, "System", "");
        }

        candidateSegments.sort((a, b) -> Double.compare(b.score, a.score));

        return generateAnswer(normalizedQuestion, questionType, candidateSegments);
    }

    // ==================== UTILITY METHODS ====================

    public List<String> getLoadedDocuments() {
        return new ArrayList<>(documentDatabase.keySet());
    }

    public void clearDocuments() {
        documentDatabase.clear();
    }

    public DocumentContent getDocument(String fileName) {
        return documentDatabase.get(fileName);
    }

    // ==================== PRIVATE HELPER METHODS ====================

    private String getFileExtension(String fileName) {
        int lastDot = fileName.lastIndexOf('.');
        return lastDot > 0 ? fileName.substring(lastDot + 1) : "";
    }

    private String readTextFile(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    private String readPDFFile(String filePath) {
        // Placeholder - requires Apache PDFBox dependency
        return "PDF reading requires Apache PDFBox dependency. Please implement with:\n" +
                "PDDocument document = PDDocument.load(new File(filePath));\n" +
                "PDFTextStripper stripper = new PDFTextStripper();\n" +
                "String text = stripper.getText(document);\n" +
                "document.close();";
    }

    private String readWordFile(String filePath) {
        // Placeholder - requires Apache POI dependency
        return "Word file reading requires Apache POI dependency. Please implement with:\n" +
                "For DOCX: XWPFDocument and XWPFWordExtractor\n" +
                "For DOC: HWPFDocument and WordExtractor";
    }

    private String readXMLFile(String filePath) throws IOException {
        return new String(Files.readAllBytes(Paths.get(filePath)));
    }

    private QuestionType identifyQuestionType(String question) {
        String lowerQuestion = question.toLowerCase();

        if (lowerQuestion.startsWith("what") || lowerQuestion.contains("what is")) {
            return QuestionType.WHAT;
        } else if (lowerQuestion.startsWith("who") || lowerQuestion.contains("who is")) {
            return QuestionType.WHO;
        } else if (lowerQuestion.startsWith("where") || lowerQuestion.contains("where is")) {
            return QuestionType.WHERE;
        } else if (lowerQuestion.startsWith("when") || lowerQuestion.contains("when is")) {
            return QuestionType.WHEN;
        } else if (lowerQuestion.startsWith("why") || lowerQuestion.contains("why is")) {
            return QuestionType.WHY;
        } else if (lowerQuestion.startsWith("how") || lowerQuestion.contains("how to")) {
            return QuestionType.HOW;
        } else if (lowerQuestion.contains("define") || lowerQuestion.contains("definition")) {
            return QuestionType.DEFINE;
        } else if (lowerQuestion.contains("list") || lowerQuestion.contains("enumerate")) {
            return QuestionType.LIST;
        } else if (lowerQuestion.contains("compare") || lowerQuestion.contains("difference")) {
            return QuestionType.COMPARE;
        }

        return QuestionType.GENERAL;
    }

    private List<ScoredSegment> findRelevantSegments(String question) {
        List<ScoredSegment> segments = new ArrayList<>();

        for (DocumentContent doc : documentDatabase.values()) {
            for (String sentence : doc.getSentences()) {
                double score = calculateEnhancedSimilarity(question, sentence);
                if (score > 0.2) {
                    segments.add(new ScoredSegment(sentence, score, doc.getFileName(), "sentence"));
                }
            }

            for (String paragraph : doc.getParagraphs()) {
                double score = calculateEnhancedSimilarity(question, paragraph);
                if (score > 0.15) {
                    segments.add(new ScoredSegment(paragraph, score, doc.getFileName(), "paragraph"));
                }
            }
        }

        return segments;
    }

    private Answer generateAnswer(String question, QuestionType questionType, List<ScoredSegment> segments) {
        ScoredSegment bestSegment = segments.get(0);
        String answer = "";
        double confidence = bestSegment.score;

        switch (questionType) {
            case WHAT:
            case DEFINE:
                answer = extractDefinition(question, bestSegment.text);
                break;
            case WHO:
                answer = extractEntity(question, bestSegment.text, "person");
                break;
            case WHERE:
                answer = extractEntity(question, bestSegment.text, "location");
                break;
            case WHEN:
                answer = extractEntity(question, bestSegment.text, "time");
                break;
            case HOW:
                answer = extractProcess(question, bestSegment.text);
                break;
            case WHY:
                answer = extractReason(question, bestSegment.text);
                break;
            case LIST:
                answer = extractList(question, segments);
                break;
            case COMPARE:
                answer = extractComparison(question, segments);
                break;
            default:
                answer = bestSegment.text;
        }

        if (answer.isEmpty()) {
            answer = bestSegment.text;
        }

        return new Answer(answer, confidence, bestSegment.source,
                getContext(bestSegment, segments));
    }

    private String extractDefinition(String question, String text) {
        String[] definitionPatterns = {
                "is defined as", "refers to", "means", "is a", "are a",
                "is the", "are the", "is described as", "can be described as"
        };

        for (String pattern : definitionPatterns) {
            if (text.toLowerCase().contains(pattern)) {
                int index = text.toLowerCase().indexOf(pattern);
                String afterPattern = text.substring(index + pattern.length()).trim();
                String[] sentences = afterPattern.split("[.!?]");
                if (sentences.length > 0) {
                    return sentences[0].trim() + ".";
                }
            }
        }

        return text;
    }

    private String extractEntity(String question, String text, String entityType) {
        Set<String> questionKeywords = extractKeywords(question);

        String[] sentences = text.split("[.!?]+");
        for (String sentence : sentences) {
            Set<String> sentenceKeywords = extractKeywords(sentence);

            boolean hasRelevantKeywords = false;
            for (String qKeyword : questionKeywords) {
                if (sentenceKeywords.contains(qKeyword) ||
                        sentenceKeywords.stream().anyMatch(sk -> areSynonyms(qKeyword, sk))) {
                    hasRelevantKeywords = true;
                    break;
                }
            }

            if (hasRelevantKeywords) {
                return sentence.trim();
            }
        }

        return text;
    }

    private String extractProcess(String question, String text) {
        String[] processPatterns = {
                "first", "second", "third", "then", "next", "finally",
                "step", "process", "method", "procedure", "way to"
        };

        String lowerText = text.toLowerCase();
        for (String pattern : processPatterns) {
            if (lowerText.contains(pattern)) {
                return text;
            }
        }

        return text;
    }

    private String extractReason(String question, String text) {
        String[] reasonPatterns = {
                "because", "due to", "since", "as a result", "therefore",
                "consequently", "thus", "hence", "owing to", "reason"
        };

        String lowerText = text.toLowerCase();
        for (String pattern : reasonPatterns) {
            if (lowerText.contains(pattern)) {
                return text;
            }
        }

        return text;
    }

    private String extractList(String question, List<ScoredSegment> segments) {
        StringBuilder listAnswer = new StringBuilder();
        Set<String> addedItems = new HashSet<>();

        for (ScoredSegment segment : segments.subList(0, Math.min(3, segments.size()))) {
            String[] sentences = segment.text.split("[.!?]+");
            for (String sentence : sentences) {
                String trimmed = sentence.trim();
                if (trimmed.length() > 10 && !addedItems.contains(trimmed.toLowerCase())) {
                    if (listAnswer.length() > 0) {
                        listAnswer.append("\n• ");
                    } else {
                        listAnswer.append("• ");
                    }
                    listAnswer.append(trimmed);
                    addedItems.add(trimmed.toLowerCase());
                }
            }
        }

        return listAnswer.toString();
    }

    private String extractComparison(String question, List<ScoredSegment> segments) {
        StringBuilder comparison = new StringBuilder();

        for (ScoredSegment segment : segments.subList(0, Math.min(2, segments.size()))) {
            if (comparison.length() > 0) {
                comparison.append("\n\n");
            }
            comparison.append(segment.text);
        }

        return comparison.toString();
    }

    private String getContext(ScoredSegment bestSegment, List<ScoredSegment> allSegments) {
        if (allSegments.size() > 1) {
            return "Found in " + bestSegment.source + " with " + (allSegments.size() - 1) + " other relevant segments.";
        } else {
            return "Found in " + bestSegment.source;
        }
    }

    private double calculateEnhancedSimilarity(String text1, String text2) {
        if (text1 == null || text2 == null || text1.trim().isEmpty() || text2.trim().isEmpty()) {
            return 0.0;
        }

        Set<String> words1 = extractKeywords(text1.toLowerCase());
        Set<String> words2 = extractKeywords(text2.toLowerCase());

        double basicSimilarity = calculateJaccardSimilarity(words1, words2);
        double synonymSimilarity = calculateSynonymSimilarity(words1, words2);
        double substringSimilarity = calculateSubstringSimilarity(text1, text2);
        double tfIdfSimilarity = calculateTfIdfSimilarity(words1, words2, text1, text2);
        double questionBoost = calculateQuestionRelevanceBoost(text1, text2);

        return (basicSimilarity * 0.25 + synonymSimilarity * 0.25 +
                substringSimilarity * 0.2 + tfIdfSimilarity * 0.2 + questionBoost * 0.1);
    }

    private double calculateQuestionRelevanceBoost(String question, String text) {
        String lowerQuestion = question.toLowerCase();
        String lowerText = text.toLowerCase();

        double boost = 0.0;

        for (String qWord : questionWords) {
            if (lowerQuestion.contains(qWord) && lowerText.contains(qWord)) {
                boost += 0.1;
            }
        }

        return Math.min(boost, 0.3);
    }

    private double calculateJaccardSimilarity(Set<String> words1, Set<String> words2) {
        Set<String> intersection = new HashSet<>(words1);
        intersection.retainAll(words2);

        Set<String> union = new HashSet<>(words1);
        union.addAll(words2);

        if (union.isEmpty()) return 0.0;
        return (double) intersection.size() / union.size();
    }

    private double calculateSynonymSimilarity(Set<String> words1, Set<String> words2) {
        int synonymMatches = 0;
        int totalComparisons = 0;

        for (String word1 : words1) {
            for (String word2 : words2) {
                totalComparisons++;
                if (areSynonyms(word1, word2)) {
                    synonymMatches++;
                }
            }
        }

        if (totalComparisons == 0) return 0.0;
        return (double) synonymMatches / totalComparisons;
    }

    private double calculateSubstringSimilarity(String text1, String text2) {
        String lower1 = text1.toLowerCase();
        String lower2 = text2.toLowerCase();

        int matches = 0;
        String[] words1 = lower1.split("\\s+");
        String[] words2 = lower2.split("\\s+");

        for (String word1 : words1) {
            if (word1.length() > 3) {
                for (String word2 : words2) {
                    if (word2.contains(word1) || word1.contains(word2)) {
                        matches++;
                        break;
                    }
                }
            }
        }

        return Math.min(words1.length, words2.length) > 0 ?
                (double) matches / Math.min(words1.length, words2.length) : 0.0;
    }

    private double calculateTfIdfSimilarity(Set<String> words1, Set<String> words2, String text1, String text2) {
        Map<String, Double> tfidf1 = calculateSimpleTfIdf(words1, text1);
        Map<String, Double> tfidf2 = calculateSimpleTfIdf(words2, text2);

        double dotProduct = 0.0;
        double norm1 = 0.0;
        double norm2 = 0.0;

        Set<String> allWords = new HashSet<>(words1);
        allWords.addAll(words2);

        for (String word : allWords) {
            double tf1 = tfidf1.getOrDefault(word, 0.0);
            double tf2 = tfidf2.getOrDefault(word, 0.0);

            dotProduct += tf1 * tf2;
            norm1 += tf1 * tf1;
            norm2 += tf2 * tf2;
        }

        if (norm1 == 0.0 || norm2 == 0.0) return 0.0;
        return dotProduct / (Math.sqrt(norm1) * Math.sqrt(norm2));
    }

    private Map<String, Double> calculateSimpleTfIdf(Set<String> words, String text) {
        Map<String, Double> tfidf = new HashMap<>();
        String[] allWords = text.toLowerCase().split("\\s+");
        int totalWords = allWords.length;

        for (String word : words) {
            int count = 0;
            for (String w : allWords) {
                if (w.equals(word)) count++;
            }

            double tf = (double) count / totalWords;
            double idf = Math.log(1.0 + word.length() / 5.0);
            tfidf.put(word, tf * idf);
        }

        return tfidf;
    }

    private boolean areSynonyms(String word1, String word2) {
        if (word1.equals(word2)) return true;

        Set<String> synonyms1 = synonyms.get(word1);
        if (synonyms1 != null && synonyms1.contains(word2)) {
            return true;
        }

        Set<String> synonyms2 = synonyms.get(word2);
        return synonyms2 != null && synonyms2.contains(word1);
    }

    private Set<String> extractKeywords(String text) {
        Set<String> keywords = new HashSet<>();

        String cleanText = text.replaceAll("[^a-zA-Z0-9\\s]", "").toLowerCase();
        String[] words = cleanText.split("\\s+");

        for (String word : words) {
            if (word.length() > 2 && !stopWords.contains(word) && !isNumeric(word)) {
                String stemmed = applyStemming(word);
                keywords.add(stemmed);
            }
        }

        return keywords;
    }

    private boolean isNumeric(String str) {
        if (str == null || str.isEmpty()) {
            return false;
        }
        try {
            Double.parseDouble(str);
            return true;
        } catch (NumberFormatException e) {
            return false;
        }
    }

    private String applyStemming(String word) {
        if (word.length() <= 3) {
            return word;
        }

        if (word.endsWith("ing") && word.length() > 4) {
            return word.substring(0, word.length() - 3);
        } else if (word.endsWith("ed") && word.length() > 3) {
            return word.substring(0, word.length() - 2);
        } else if (word.endsWith("ly") && word.length() > 3) {
            return word.substring(0, word.length() - 2);
        } else if (word.endsWith("tion") && word.length() > 5) {
            return word.substring(0, word.length() - 4);
        } else if (word.endsWith("ment") && word.length() > 5) {
            return word.substring(0, word.length() - 4);
        } else if (word.endsWith("ness") && word.length() > 5) {
            return word.substring(0, word.length() - 4);
        } else if (word.endsWith("able") && word.length() > 5) {
            return word.substring(0, word.length() - 4);
        } else if (word.endsWith("ible") && word.length() > 5) {
            return word.substring(0, word.length() - 4);
        } else if (word.endsWith("er") && word.length() > 3) {
            return word.substring(0, word.length() - 2);
        } else if (word.endsWith("est") && word.length() > 4) {
            return word.substring(0, word.length() - 3);
        } else if (word.endsWith("s") && word.length() > 2 && !word.endsWith("ss")) {
            return word.substring(0, word.length() - 1);
        }

        return word;
    }

    private double calculatePhraseSimilarity(String text1, String text2) {
        List<String> phrases1 = extractImportantPhrases(text1);
        List<String> phrases2 = extractImportantPhrases(text2);

        if (phrases1.isEmpty() || phrases2.isEmpty()) {
            return 0.0;
        }

        int matches = 0;
        for (String phrase1 : phrases1) {
            for (String phrase2 : phrases2) {
                if (calculateSimilarity(phrase1, phrase2) > 0.6) {
                    matches++;
                    break;
                }
            }
        }

        return (double) matches / Math.max(phrases1.size(), phrases2.size());
    }

    private double calculatePositionalSimilarity(String text1, String text2) {
        String[] words1 = text1.toLowerCase().split("\\s+");
        String[] words2 = text2.toLowerCase().split("\\s+");

        if (words1.length == 0 || words2.length == 0) {
            return 0.0;
        }

        double positionScore = 0.0;
        int matches = 0;

        // Check for words appearing in similar relative positions
        for (int i = 0; i < words1.length; i++) {
            String word1 = words1[i];
            if (stopWords.contains(word1) || word1.length() <= 2) {
                continue;
            }

            double relativePos1 = (double) i / words1.length;

            for (int j = 0; j < words2.length; j++) {
                String word2 = words2[j];
                if (word1.equals(word2) || areSynonyms(word1, word2)) {
                    double relativePos2 = (double) j / words2.length;
                    double positionDiff = Math.abs(relativePos1 - relativePos2);

                    // Higher score for words in similar positions
                    positionScore += (1.0 - positionDiff);
                    matches++;
                    break;
                }
            }
        }

        return matches > 0 ? positionScore / matches : 0.0;
    }

    // Main method for testing
    public static void main(String[] args) {
        SimpleNLP nlp = new SimpleNLP();

        try {
            // Example usage
            nlp.addDocumentFromText("sample.txt",
                    "Java is a programming language. It is object-oriented and platform-independent. " +
                            "Java applications can run on any device that has the Java Virtual Machine installed.");

            System.out.println("Loaded documents: " + nlp.getLoadedDocuments());

            // Test question answering
            Answer answer1 = nlp.answerQuestion("What is Java?");
            System.out.println("\nQuestion: What is Java?");
            System.out.println(answer1);

            Answer answer2 = nlp.answerQuestion("How does Java work?");
            System.out.println("\nQuestion: How does Java work?");
            System.out.println(answer2);

        } catch (Exception e) {
            System.err.println("Error: " + e.getMessage());
        }
    }
}