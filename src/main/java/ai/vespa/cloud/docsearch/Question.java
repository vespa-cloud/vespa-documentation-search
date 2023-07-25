package ai.vespa.cloud.docsearch;

import com.yahoo.language.Language;
import com.yahoo.language.Linguistics;
import com.yahoo.language.process.StemMode;
import com.yahoo.language.process.Token;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

public class Question {

    public static boolean isStopWord(String word) {
        return QUESTION_WORDS.contains(word);
    }
    public static final Set<String> QUESTION_WORDS = new HashSet<String>() {{
        add("what");
        add("when");
        add("where");
        add("who");
        add("why");
        add("how");
        add("which");
        add("is");
        add("are");
        add("am");
        add("do");
        add("does");
        add("did");
        add("can");
        add("could");
        add("should");
        add("would");
        add("will");
        add("shall");
        add("may");
        add("might");
        add("must");
        add("have");
        add("has");
        add("had");
        add("was");
        add("were");
        add("been");
        add("being");
        add("there");
        add("their");
        add("they're");
        add("it");
        add("its");
        add("you");
        add("your");
        add("yours");
        add("we");
        add("us");
        add("our");
        add("ours");
        add("me");
        add("my");
    }};

    public static List<String> tokenize(String userQuery, Linguistics linguistics) {
        List<String> result = new ArrayList<>(6);
        Iterable<Token> tokens = linguistics.getTokenizer().
                tokenize(userQuery, Language.fromLanguageTag("en"), StemMode.NONE,false);
        for(Token t: tokens) {
            if (t.isIndexable())
                result.add(t.getTokenString());
        }
        return result;
    }
}
