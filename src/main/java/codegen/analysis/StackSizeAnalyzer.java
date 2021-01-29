package codegen.analysis;

import parser.ast.AST;
import parser.ast.ASTNode;

import java.util.Set;

import static util.Logger.log;

public final class StackSizeAnalyzer {

    private static final Set<String> mod;
    private static final Set<String> bin;

    static {
        mod = Set.of("assignment", "expr", "INTEGER_LIT", "BOOLEAN_LIT", "STRING_LIT", "IDENTIFIER", "print");
        bin = Set.of("AND", "OR", "ADD", "SUB", "MUL", "DIV", "MOD", "LESS", "LESS_EQUAL", "GREATER", "GREATER_EQUAL", "EQUAL", "NOT_EQUAL");
    }

    private StackSizeAnalyzer() {}

    public static int runStackModel(AST tree) {
        log("\nDetermining required stack depth:");

        final StackModel stack = new StackModel();
        runStackModel(tree.getRoot().getChildren().get(3).getChildren().get(11), stack);

        return stack.getMax();
    }

    private static void runStackModel(ASTNode root, StackModel stack) {
        if (mod.contains(root.getName())) {
            switch (root.getName()) {
                case "assignment" -> assignment(root, stack);
                case "INTEGER_LIT", "BOOLEAN_LIT", "STRING_LIT", "IDENTIFIER" -> literal(root, stack);
                case "expr" -> expr(root, stack);
                case "print" -> println(root, stack);
                default -> throw new IllegalStateException("Unexpected value: " + root.getName());
            }
        } else {
            for (ASTNode child : root.getChildren()) {
                runStackModel(child, stack);
            }
        }
    }

    private static void literal(ASTNode root, StackModel stack) {
        log("literal():");
        stack.push(root);
    }

    private static void assignment(ASTNode root, StackModel stack) {
        runStackModel(root.getChildren().get(0), stack);

        log("assignment():");
        stack.pop();
    }

    private static void println(ASTNode root, StackModel stack) {
        stack.push(root); // Getstatic

        runStackModel(root.getChildren().get(1).getChildren().get(1), stack);

        log("println():");
        stack.pop(); // Objectref
        stack.pop(); // Argument
    }

    private static void expr(ASTNode root, StackModel stack) {
        if (root.getChildren().size() == 2 && bin.contains(root.getValue())) {
            runStackModel(root.getChildren().get(0), stack);
            runStackModel(root.getChildren().get(1), stack);

            log("expr():");
            stack.pop(); // Argument
            stack.pop(); // Argument
            stack.push(root); // Result
        } else if (root.getChildren().size() == 1 && "NOT".equals(root.getValue())) {
            runStackModel(root.getChildren().get(0), stack);

            log("expr():");
            stack.push(new ASTNode("1 (XOR)", 0)); // 1 for xor
            stack.pop(); // xor
            stack.pop(); // xor
            stack.push(root); // result
        } else if (root.getChildren().size() == 1) {
            runStackModel(root.getChildren().get(0), stack);
        }
    }
}