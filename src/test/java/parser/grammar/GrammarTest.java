package parser.grammar;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.net.URISyntaxException;
import java.nio.file.Path;
import java.nio.file.Paths;

import static org.assertj.core.api.Assertions.assertThat;

class GrammarTest {

    private Path getPath(String name) {
        try {
            return Paths.get(this.getClass().getClassLoader().getResource("exampleGrammars/" + name).toURI());
        } catch (URISyntaxException e) {
            e.printStackTrace();
        }

        return null;
    }

    @Test
    void testSimpleGrammar0() throws IOException {
        Path path = this.getPath("SimpleGrammar0.grammar");

        Grammar grammar = Grammar.fromFile(path);
        assert grammar != null;

        assertThat(grammar.getEpsilonSymbol()).isEqualTo("epsilon");
        assertThat(grammar.getStartSymbol()).isEqualTo("S");
        assertThat(grammar.getTerminals()).containsOnly("a", "i", "t", "b");
        assertThat(grammar.getNonterminals()).containsOnly("S", "E");
        assertThat(grammar.getRules()).containsOnly(new GrammarRule("S", "a"),
                                                    new GrammarRule("S", "i", "E", "t", "S"),
                                                    new GrammarRule("E", "b"));
    }

    @Test
    void testSimpleGrammar1() throws IOException {
        Path path = this.getPath("SimpleGrammar1.grammar");

        Grammar grammar = Grammar.fromFile(path);
        assert grammar != null;

        System.out.println(grammar.getRules());

        assertThat(grammar.getEpsilonSymbol()).isEqualTo("epsilon");
        assertThat(grammar.getStartSymbol()).isEqualTo("E");
        assertThat(grammar.getTerminals()).containsOnly("id", "+", "*", "(", ")");
        assertThat(grammar.getNonterminals()).containsOnly("E", "E2", "T", "T2", "F");
        assertThat(grammar.getRules()).contains(new GrammarRule("E", "T", "E2"),
                                                new GrammarRule("E2", "+", "T", "E2"),
                                                new GrammarRule("E2", "epsilon"));
    }
}
