package com.strata.model;

import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;
import java.util.function.ToDoubleFunction;

/** A designer's scaling formula: an arithmetic expression over named variables. */
public final class Formula {

    private final String source;
    private final ToDoubleFunction<Map<String, Double>> eval;

    private Formula(String source, ToDoubleFunction<Map<String, Double>> eval) {
        this.source = source;
        this.eval = eval;
    }

    /** The expression as the designer wrote it. */
    public String source() {
        return source;
    }

    /** The value for these variables. */
    public double eval(Map<String, Double> vars) {
        return eval.applyAsDouble(vars);
    }

    /** Parses an expression, or throws {@link Problem} naming what is wrong and where. */
    public static Formula parse(String source, Set<String> variables) {
        if (source == null || source.isBlank()) {
            throw new Problem("The formula is empty", 0);
        }
        Parser p = new Parser(source, variables);
        ToDoubleFunction<Map<String, Double>> e = p.sum();
        p.skip();
        if (p.pos < source.length()) {
            throw new Problem("Unexpected '" + source.charAt(p.pos) + "'", p.pos);
        }
        return new Formula(source.strip(), e);
    }

    /** Why an expression cannot be used, or null when it can. */
    public static String problem(String source, Set<String> variables) {
        try {
            parse(source, variables);
            return null;
        } catch (Problem e) {
            return e.getMessage();
        }
    }

    /** A parse error with its character position. */
    public static final class Problem extends RuntimeException {
        public final int position;

        Problem(String message, int position) {
            super(message + " (at character " + (position + 1) + ")");
            this.position = position;
        }
    }

    private static final class Parser {
        final String s;
        final Set<String> vars;
        int pos;

        Parser(String s, Set<String> vars) {
            this.s = s;
            this.vars = vars;
        }

        void skip() {
            while (pos < s.length() && Character.isWhitespace(s.charAt(pos))) pos++;
        }

        boolean eat(char c) {
            skip();
            if (pos < s.length() && s.charAt(pos) == c) {
                pos++;
                return true;
            }
            return false;
        }

        ToDoubleFunction<Map<String, Double>> sum() {
            ToDoubleFunction<Map<String, Double>> left = product();
            while (true) {
                if (eat('+')) {
                    var a = left; var b = product();
                    left = v -> a.applyAsDouble(v) + b.applyAsDouble(v);
                } else if (eat('-')) {
                    var a = left; var b = product();
                    left = v -> a.applyAsDouble(v) - b.applyAsDouble(v);
                } else {
                    return left;
                }
            }
        }

        ToDoubleFunction<Map<String, Double>> product() {
            ToDoubleFunction<Map<String, Double>> left = unary();
            while (true) {
                if (eat('*')) {
                    var a = left; var b = unary();
                    left = v -> a.applyAsDouble(v) * b.applyAsDouble(v);
                } else if (eat('/')) {
                    var a = left; var b = unary();
                    left = v -> a.applyAsDouble(v) / b.applyAsDouble(v);
                } else {
                    return left;
                }
            }
        }

        ToDoubleFunction<Map<String, Double>> unary() {
            if (eat('-')) {
                var a = unary();
                return v -> -a.applyAsDouble(v);
            }
            if (eat('+')) {
                return unary();
            }
            return power();
        }

        ToDoubleFunction<Map<String, Double>> power() {
            ToDoubleFunction<Map<String, Double>> base = atom();
            if (eat('^')) {
                var b = unary();
                return v -> Math.pow(base.applyAsDouble(v), b.applyAsDouble(v));
            }
            return base;
        }

        ToDoubleFunction<Map<String, Double>> atom() {
            skip();
            int start = pos;
            if (eat('(')) {
                var e = sum();
                if (!eat(')')) throw new Problem("A ')' is missing", pos);
                return e;
            }
            if (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) {
                while (pos < s.length() && (Character.isDigit(s.charAt(pos)) || s.charAt(pos) == '.')) pos++;
                double n;
                try {
                    n = Double.parseDouble(s.substring(start, pos));
                } catch (NumberFormatException e) {
                    throw new Problem("Bad number '" + s.substring(start, pos) + "'", start);
                }
                return v -> n;
            }
            if (pos < s.length() && (Character.isLetter(s.charAt(pos)) || s.charAt(pos) == '_')) {
                while (pos < s.length() && (Character.isLetterOrDigit(s.charAt(pos)) || s.charAt(pos) == '_')) pos++;
                String name = s.substring(start, pos).toLowerCase(Locale.ROOT);
                if (eat('(')) {
                    List<ToDoubleFunction<Map<String, Double>>> args = new ArrayList<>();
                    if (!eat(')')) {
                        do {
                            args.add(sum());
                        } while (eat(','));
                        if (!eat(')')) throw new Problem("A ')' is missing", pos);
                    }
                    return function(name, args, start);
                }
                if (!vars.contains(name)) {
                    throw new Problem("Unknown name '" + name + "'. Use one of: " + String.join(", ", vars.stream().sorted().toList()), start);
                }
                return v -> v.getOrDefault(name, 0.0);
            }
            if (pos >= s.length()) throw new Problem("The formula ends too soon", pos);
            throw new Problem("Unexpected '" + s.charAt(pos) + "'", pos);
        }

        ToDoubleFunction<Map<String, Double>> function(String name, List<ToDoubleFunction<Map<String, Double>>> a, int at) {
            int n = switch (name) {
                case "floor", "ceil", "round", "abs", "sqrt", "log", "exp" -> 1;
                case "min", "max" -> 2;
                case "clamp", "if" -> 3;
                default -> throw new Problem("Unknown function '" + name + "'", at);
            };
            if (a.size() != n) {
                throw new Problem(name + " takes " + n + " value" + (n == 1 ? "" : "s") + ", not " + a.size(), at);
            }
            var x = a.get(0);
            return switch (name) {
                case "floor" -> v -> Math.floor(x.applyAsDouble(v));
                case "ceil" -> v -> Math.ceil(x.applyAsDouble(v));
                case "round" -> v -> Math.round(x.applyAsDouble(v));
                case "abs" -> v -> Math.abs(x.applyAsDouble(v));
                case "sqrt" -> v -> Math.sqrt(x.applyAsDouble(v));
                case "log" -> v -> Math.log(x.applyAsDouble(v));
                case "exp" -> v -> Math.exp(x.applyAsDouble(v));
                case "min" -> v -> Math.min(x.applyAsDouble(v), a.get(1).applyAsDouble(v));
                case "max" -> v -> Math.max(x.applyAsDouble(v), a.get(1).applyAsDouble(v));
                case "clamp" -> v -> Math.max(a.get(1).applyAsDouble(v), Math.min(a.get(2).applyAsDouble(v), x.applyAsDouble(v)));
                default -> v -> x.applyAsDouble(v) > 0 ? a.get(1).applyAsDouble(v) : a.get(2).applyAsDouble(v);
            };
        }
    }
}
