package parser.grammar;

import parser.ParsingTable;
import util.Logger;

import java.util.AbstractMap;
import java.util.Arrays;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public final class GrammarAnalyzer {

    private final Grammar grammar;

    /**
     * Das first-Set enthält für jedes Nichtterminalsymbol alle Terminalsymbole, die als erstes bei diesen
     * Nichtterminal auftreten können.
     */
    private final Map<String, Set<String>> first;

    /**
     * Das follow-Set enhält für jedes Nichtterminalsymbol alle Terminalsymbole, die direkt auf dieses
     * Nichtterminal folgen können.
     */
    private final Map<String, Set<String>> follow;

    private final ParsingTable table;

    private GrammarAnalyzer(Grammar grammar) {
        this.grammar = grammar;

        Logger.logDebug("Beginning grammar analysis", GrammarAnalyzer.class);

        // Es muss zwingend in der Reihenfolge [First < Follow < Table] initialisiert werden
        this.first = this.initFirst();
        this.follow = this.initFollow();
        this.table = this.initParseTable();

        Logger.logDebug("Grammar analysis successful", GrammarAnalyzer.class);
    }

    public static GrammarAnalyzer fromGrammar(Grammar grammar) {
        return new GrammarAnalyzer(grammar);
    }

    private Map<String, Set<String>> initFirst() {
        Logger.logDebug(" :: Initializing first-set", GrammarAnalyzer.class);

        final Map<String, Set<String>> firstOut = new HashMap<>();

        // Die Methode funktioniert erst, nachdem first initialisiert ist.
        // Deshalb hier doppelt.
        final Predicate<String> nullable = sym -> sym.equals(Grammar.EPSILON_SYMBOL)
                                                  || sym.isBlank()
                                                  || firstOut.get(sym).contains(Grammar.EPSILON_SYMBOL);
        final Predicate<String[]> allNullable = split -> split.length == 0
                                                         || Arrays.stream(split).allMatch(nullable);

        // Initialisieren
        for (String nterm : this.grammar.getNonterminals()) {
            firstOut.put(nterm, new HashSet<>());
        }
        for (String term : this.grammar.getTerminals()) {
            // 1. If X is a terminal, then first(X) = {X}.

            firstOut.put(term, new HashSet<>());
            firstOut.get(term).add(term);
        }

        boolean change;

        do {
            change = false;

            for (String leftside : this.grammar.getLeftSides()) {
                // 2. (a) If X is a nonterminal...

                for (String rightside : this.grammar.getRightsides(leftside)) {
                    // ...and X -> Y1 Y2 ... Yk is a production...


                    if (!rightside.equals(Grammar.EPSILON_SYMBOL)) {
                        // ...for some k >= 1...

                        final String[] split = rightside.split(" ");

                        // !: Dumm implementiert, alles wird mehrfach auf nullable gecheckt:
                        // !: nullable(Y1), nullable(Y1 Y2), nullable(Y1 Y2 Y3)...
                        for (int i = 0; i < split.length; i++) {

                            // All Y1 ... Yi-1
                            final String[] sub = Arrays.copyOfRange(split, 0, i);

                            if (allNullable.test(sub)) {
                                // ...then place a in first(X) if a is in first(Yi) for some i...
                                // ...and epsilon is in all of first(Y1) ... first(Yi-1).

                                // Because a != epsilon
                                final Set<String> firstYiNoEps = firstOut.get(split[i]).stream()
                                                                         .filter(sym -> !sym.equals(Grammar.EPSILON_SYMBOL))
                                                                         .collect(Collectors.toSet());

                                final boolean changeNow = firstOut.get(leftside).addAll(firstYiNoEps);
                                change = change || changeNow;

                                Logger.logInfoIfTrue(changeNow, "Rule: \"" + leftside + " -> " + rightside + "\"", GrammarAnalyzer.class);
                                Logger.logInfoIfTrue(changeNow, " :: Added " + firstYiNoEps + " to \"first("
                                                                + leftside + ")\" (All before are nullable)", GrammarAnalyzer.class);
                            }

                            if (i == split.length - 1 && allNullable.test(split)) {
                                // 2. (b) If epsilon is in first(Y1) ... first(Yk), then add epsilon to first(X).

                                final boolean changeNow = firstOut.get(leftside).add(Grammar.EPSILON_SYMBOL);
                                change = change || changeNow;

                                Logger.logInfoIfTrue(changeNow, "Rule: \"" + leftside + " -> " + rightside + "\"", GrammarAnalyzer.class);
                                Logger.logInfoIfTrue(changeNow, " :: Added [" + Grammar.EPSILON_SYMBOL + "] to \"first("
                                                                + leftside + ")\" (All are nullable)", GrammarAnalyzer.class);
                            }
                        }
                    }

                    if (rightside.equals(Grammar.EPSILON_SYMBOL)) {
                        // 3. If X -> epsilon is a production, then add epsilon to first(X).

                        final boolean changeNow = firstOut.get(leftside).add(Grammar.EPSILON_SYMBOL);
                        change = change || changeNow;

                        Logger.logInfoIfTrue(changeNow, "Rule: \"" + leftside + " -> " + rightside + "\"", GrammarAnalyzer.class);
                        Logger.logInfoIfTrue(changeNow, " :: Added [" + Grammar.EPSILON_SYMBOL + "] to \"first("
                                                        + leftside + ")\" (X -> EPS exists)", GrammarAnalyzer.class);
                    }
                }
            }
        } while (change);

        Logger.logInfo("First Set: " + firstOut, GrammarAnalyzer.class);
        Logger.logDebug(" :: First-set initialized successfully", GrammarAnalyzer.class);

        return firstOut;
    }

    private Map<String, Set<String>> initFollow() {
        Logger.logDebug(" :: Initializing follow-set", GrammarAnalyzer.class);

        final Map<String, Set<String>> followOut = new HashMap<>();

        // Initialisieren
        for (String nterm : this.grammar.getNonterminals()) {
            followOut.put(nterm, new HashSet<>());
        }

        // 1. Place $ in follow(S), where S is the start symbol, and $ is the input right endmarker
        followOut.get(Grammar.START_SYMBOL).add("$");

        boolean change;

        do {
            change = false;

            for (String leftside : this.grammar.getLeftSides()) {

                for (String rightside : this.grammar.getRightsides(leftside)) {

                    final String[] split = rightside.split(" ");

                    for (int i = 1; i < split.length; i++) {
                        // 2. If there is a production A -> aBb, then everything in first(b) except epsilon
                        //    is in follow(B).

                        if (!this.grammar.getNonterminals().contains(split[i - 1])) {
                            // Follow nur für Nichtterminale berechnen

                            continue;
                        }

                        // !: Hier wird wieder alles doppelt geprüft
                        for (int k = i; k < split.length; k++) {
                            // Behandelt solche Fälle: X -> Y1 Y2 Y3, wo Y2 nullable ist.
                            // Dann beinhaltet follow(Y1) auch first(Y3)

                            final String[] sub = Arrays.copyOfRange(split, i, k);

                            if (this.allNullable(sub)) {

                                final Set<String> firstXkNoEps = this.first(split[k]).stream()
                                                                     .filter(sym -> !sym.equals(Grammar.EPSILON_SYMBOL))
                                                                     .collect(Collectors.toSet());

                                final boolean changeNow = followOut.get(split[i - 1]).addAll(firstXkNoEps);
                                change = change || changeNow;

                                Logger.logInfoIfTrue(changeNow, "Rule: \"" + leftside + " -> " + rightside + "\"", GrammarAnalyzer.class);
                                Logger.logInfoIfTrue(changeNow, " :: Added " + firstXkNoEps + " to \"follow("
                                                                + split[i - 1] + ")\" (All nullable inbetween)", GrammarAnalyzer.class);
                            }
                        }

                        // 3. (b) If there is a production A -> aBb, where b is nullable, then everything in
                        //        follow(A) is in follow(B)
                        final String[] sub = Arrays.copyOfRange(split, i, split.length);

                        if (this.allNullable(sub)) {

                            final boolean changeNow = followOut.get(split[i - 1]).addAll(followOut.get(leftside));
                            change = change || changeNow;

                            Logger.logInfoIfTrue(changeNow, "Rule: \"" + leftside + " -> " + rightside + "\"", GrammarAnalyzer.class);
                            Logger.logInfoIfTrue(changeNow, " :: Added " + leftside + " to \"follow("
                                                            + split[i - 1] + ")\" (All following are nullable)", GrammarAnalyzer.class);
                        }
                    }

                    if (this.grammar.getNonterminals().contains(split[split.length - 1])) {
                        // 3. (a) If there is a production A -> aB, then everything in follow(A) is in follow(B).

                        final boolean changeNow = followOut.get(split[split.length - 1]).addAll(followOut.get(leftside));
                        change = change || changeNow;

                        Logger.logInfoIfTrue(changeNow, "Rule: \"" + leftside + " -> " + rightside + "\"", GrammarAnalyzer.class);
                        Logger.logInfoIfTrue(changeNow, " :: Added " + followOut.get(leftside) + " to \"follow("
                                                        + split[split.length - 1] + ")\" (Last item in production)", GrammarAnalyzer.class);
                    }
                }
            }

        } while (change);

        Logger.logInfo("Follow Set: " + followOut, GrammarAnalyzer.class);
        Logger.logDebug(" :: Follow-set initialized successfully", GrammarAnalyzer.class);

        return followOut;
    }

    private ParsingTable initParseTable() {
        Logger.logDebug(" :: Initializing parse-table", GrammarAnalyzer.class);

        final Map<Map.Entry<String, String>, String> tableOut = new HashMap<>();

        for (String leftside : this.grammar.getLeftSides()) {

            for (String rightside : this.grammar.getRightsides(leftside)) {
                // For each production A -> a of the grammar, do the following:

                final Set<String> firstRightside = this.stringFirst(rightside);

                for (String sym : firstRightside) {
                    // 1. For each terminal t in first(a), add A -> a to table[A, t]

                    final String prev = tableOut.put(new AbstractMap.SimpleEntry<>(leftside, sym), rightside);

                    Logger.logInfo("Rule: \"" + leftside + " -> " + rightside + "\"", GrammarAnalyzer.class);
                    Logger.logInfo(" :: Add " + rightside + " to cell (" + leftside + ", " + sym + ") (" + sym
                                   + " in \"first(" + rightside + ")\")", GrammarAnalyzer.class);
                    Logger.logInfoNullable(prev, " :: Overwritten " + prev + "!", GrammarAnalyzer.class);
                }

                final Set<String> followLeftside = this.follow(leftside);

                if (firstRightside.contains(Grammar.EPSILON_SYMBOL)) {
                    // 2. If epsilon in first(a), then...

                    for (String sym : followLeftside) {
                        // ...for each terminal b in follow(A), add A -> a to table[A, b].

                        final String prev = tableOut.put(new AbstractMap.SimpleEntry<>(leftside, sym), rightside);

                        Logger.logInfo("Rule: \"" + leftside + " -> " + rightside + "\"", GrammarAnalyzer.class);
                        Logger.logInfo(" :: Add " + rightside + " to cell (" + leftside + ", " + sym + ") (" + sym
                                       + " in \"follow(" + leftside + ")\")", GrammarAnalyzer.class);
                        Logger.logInfoNullable(prev, " :: Overwritten " + prev + "!", GrammarAnalyzer.class);
                    }

                    if (followLeftside.contains("$")) {
                        // If epsilon is in first(a) and $ is in follow(A), add A -> a to table[A, $].

                        final String prev = tableOut.put(new AbstractMap.SimpleEntry<>(leftside, "$"), rightside);

                        Logger.logInfo("Rule: \"" + leftside + " -> " + rightside + "\"", GrammarAnalyzer.class);
                        Logger.logInfo(" :: Add " + rightside + " to cell (" + leftside
                                       + ", $) (epsilon in \"first(" + rightside + ")\" and $ in \"follow("
                                       + leftside + ")\")", GrammarAnalyzer.class);
                        Logger.logInfoNullable(prev, " :: Overwritten " + prev + "!", GrammarAnalyzer.class);
                    }
                }
            }
        }

        final ParsingTable parsingTable = new ParsingTable(this.grammar, tableOut);

        Logger.logInfo("ParsingTable:\n" + parsingTable, GrammarAnalyzer.class);
        Logger.logDebug(" :: Parse-table initialized successfully", GrammarAnalyzer.class);

        return parsingTable;
    }


    public boolean nullable(String sym) {
        return sym.isBlank()
               || sym.equals(Grammar.EPSILON_SYMBOL)
               || this.first.get(sym).contains(Grammar.EPSILON_SYMBOL);
    }

    public boolean allNullable(String[] split) {
        return split.length == 0
               || Arrays.stream(split).allMatch(this::nullable);
    }

    public Set<String> first(String sym) {
        return this.first.get(sym);
    }

    public Set<String> stringFirst(String rightside) {
        return this.stringFirst(rightside.split(" "));
    }

    public Set<String> stringFirst(String[] split) {
        final Set<String> firstOut = new HashSet<>();

        // !: Hier wird wieder doppelt getestet
        for (int i = 0; i < split.length; i++) {
            final String[] sub = Arrays.copyOfRange(split, 0, i);

            if (this.allNullable(sub)) {
                // X1 ... Xi-1 are nullable, so first(X1 ... Xn) contains first(Xi)

                final Set<String> firstXiNoEps;
                if (split.length == 1 && split[0].equals(Grammar.EPSILON_SYMBOL)) {
                    // Stream collect has to be evaluated, doesn't work on empty stream

                    firstXiNoEps = Collections.emptySet();
                } else {
                    // Only non-epsilon symbols

                    firstXiNoEps = this.first(split[i]).stream()
                                       .filter(sym -> !sym.equals(Grammar.EPSILON_SYMBOL))
                                       .collect(Collectors.toSet());
                }

                firstOut.addAll(firstXiNoEps);

                if (i == split.length - 1 && this.allNullable(split)) {
                    // Finally, add epsilon to first(X1 X2 ... Xn) if, for all i, epsilon is in first(Xi).

                    firstOut.add(Grammar.EPSILON_SYMBOL);
                }
            }
        }

        return firstOut;
    }

    public Set<String> follow(String sym) {
        return this.follow.get(sym);
    }


    public Map<String, Set<String>> getFirst() {
        return this.first;
    }

    public Map<String, Set<String>> getFollow() {
        return this.follow;
    }

    public ParsingTable getTable() {
        return this.table;
    }
}
