package org.evochora.compiler.frontend.semantics;

import org.evochora.compiler.diagnostics.DiagnosticsEngine;
import org.evochora.compiler.frontend.parser.ast.AstNode;
import org.evochora.compiler.frontend.parser.ast.InstructionNode;
import org.evochora.compiler.frontend.parser.features.def.DefineNode;
import org.evochora.compiler.frontend.parser.features.label.LabelNode;
import org.evochora.compiler.frontend.parser.features.proc.ProcedureNode;
import org.evochora.compiler.frontend.parser.features.scope.ScopeNode;
import org.evochora.compiler.frontend.semantics.analysis.*;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

public class SemanticAnalyzer {

    private final DiagnosticsEngine diagnostics;
    private final SymbolTable symbolTable;
    private final Map<Class<? extends AstNode>, IAnalysisHandler> handlers = new HashMap<>();

    public SemanticAnalyzer(DiagnosticsEngine diagnostics) {
        this.diagnostics = diagnostics;
        this.symbolTable = new SymbolTable(diagnostics);
        registerDefaultHandlers();
    }

    private void registerDefaultHandlers() {
        handlers.put(DefineNode.class, new DefineAnalysisHandler());
        handlers.put(LabelNode.class, new LabelAnalysisHandler());
        handlers.put(ScopeNode.class, new ScopeAnalysisHandler());
        handlers.put(ProcedureNode.class, new ScopeAnalysisHandler());
        handlers.put(InstructionNode.class, new InstructionAnalysisHandler(symbolTable, diagnostics));
    }

    public void analyze(List<AstNode> statements) {
        // First pass: collect labels (and respect scopes) to allow forward references
        collectLabels(statements);
        // Second pass: full analysis (types, arity, scope rules)
        traverseAndAnalyze(statements);
    }

    private void collectLabels(List<AstNode> nodes) {
        for (AstNode node : nodes) {
            if (node == null) continue;

            if (node instanceof ScopeNode || node instanceof ProcedureNode) {
                symbolTable.enterScope();
            }
            if (node instanceof org.evochora.compiler.frontend.parser.features.label.LabelNode lbl) {
                symbolTable.define(new Symbol(lbl.labelToken(), Symbol.Type.LABEL));
            }
            collectLabels(node.getChildren());
            if (node instanceof ScopeNode || node instanceof ProcedureNode) {
                symbolTable.leaveScope();
            }
        }
    }

    private void traverseAndAnalyze(List<AstNode> nodes) {
        for (AstNode node : nodes) {
            if (node == null) continue;

            IAnalysisHandler handler = handlers.get(node.getClass());

            if (handler != null) {
                handler.analyze(node, symbolTable, diagnostics);
            }

            traverseAndAnalyze(node.getChildren());

            if (handler instanceof ScopeAnalysisHandler scopeHandler) {
                scopeHandler.afterChildren(symbolTable);
            }
        }
    }
}