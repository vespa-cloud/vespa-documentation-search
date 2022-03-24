package ai.vespa.cloud.docsearch;

import com.google.inject.Inject;
import com.yahoo.docproc.DocumentProcessor;
import com.yahoo.docproc.Processing;
import com.yahoo.document.Document;
import com.yahoo.document.DocumentOperation;
import com.yahoo.document.DocumentPut;
import com.yahoo.document.DocumentUpdate;
import com.yahoo.document.datatypes.FieldValue;
import com.yahoo.document.update.FieldUpdate;
import com.yahoo.document.update.ValueUpdate;

import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.HashSet;
import java.util.Set;

public class QueryDocumentProcessor extends DocumentProcessor {

    private static final String TERM_DOCUMENT_TYPE  = "term";
    private static final String acceptedWordsFile = "files/accepted_words.txt";
    private final Set<String> acceptedWords;

    @Inject
    public QueryDocumentProcessor() {
        this.acceptedWords = getAcceptedWords();
    }

    @Override
    public Progress process(Processing processing) {
        for (DocumentOperation op : processing.getDocumentOperations()) {
            if (op instanceof DocumentPut) {
                DocumentPut put = (DocumentPut) op;
                Document document = put.getDocument();
                if (document.getDataType().isA(TERM_DOCUMENT_TYPE)) {
                    if ( ! containsOnlyAcceptedWords(document.getFieldValue("term"))) {
                        processing.getDocumentOperations().clear();
                        return Progress.DONE;
                    }
                }
            }
            else if (op instanceof DocumentUpdate) {
                DocumentUpdate update = (DocumentUpdate) op;
                if (update.getDocumentType().isA(TERM_DOCUMENT_TYPE)) {
                    FieldUpdate fieldUpdate = update.getFieldUpdate("term");
                    if (fieldUpdate != null) {
                        for (ValueUpdate<?> valueUpdate : fieldUpdate.getValueUpdates()) {
                            if ( ! containsOnlyAcceptedWords(valueUpdate.getValue())) {
                                processing.getDocumentOperations().clear();
                                return Progress.DONE;
                            }
                        }
                    }
                }
            }
        }
        return Progress.DONE;
    }

    private Boolean containsOnlyAcceptedWords(FieldValue termValue){
        if (!acceptedWords.isEmpty()) {
            String query = termValue.toString().toLowerCase();
            String[] terms = query.split("\\s+");
            for (String term : terms) {
                if (!acceptedWords.contains(term)) {
                    return false;
                }
            }
        }
        return true;
    }

    private Set<String> getAcceptedWords() {
        if ( ! resourceExists()) return Set.of();

        Set<String> acceptedWords = new HashSet<>();
        try (BufferedReader br = createReaderOf(acceptedWordsFile)) {
            String term;
            while ((term = br.readLine()) != null) {
                acceptedWords.add(term);
            }
        } catch (IOException e) {
            throw new RuntimeException(e);
        }
        return acceptedWords;
    }

    private BufferedReader createReaderOf(String filePath) {
        return new BufferedReader(new InputStreamReader(getClass().getClassLoader().getResourceAsStream(filePath),
                                                        StandardCharsets.UTF_8));
    }

    private boolean resourceExists() {
        return getClass().getClassLoader().getResource(QueryDocumentProcessor.acceptedWordsFile) != null;
    }

    @Override
    public void deconstruct() {
        super.deconstruct();
    }

}