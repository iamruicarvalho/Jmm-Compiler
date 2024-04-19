package pt.up.fe.comp2024.optimization;

import pt.up.fe.comp.jmm.analysis.table.SymbolTable;
import pt.up.fe.comp.jmm.analysis.table.Type;
import pt.up.fe.comp.jmm.ast.JmmNode;
import pt.up.fe.comp.jmm.ast.PreorderJmmVisitor;
import pt.up.fe.comp2024.ast.TypeUtils;

import static pt.up.fe.comp2024.ast.Kind.*;

/**
 * Generates OLLIR code from JmmNodes that are expressions.
 */
public class OllirExprGeneratorVisitor extends PreorderJmmVisitor<Void, OllirExprResult> {

    private static final String SPACE = " ";
    private static final String ASSIGN = ":=";
    private final String END_STMT = ";\n";

    private final SymbolTable table;

    public OllirExprGeneratorVisitor(SymbolTable table) {
        this.table = table;
    }

    @Override
    protected void buildVisitor() {
        addVisit(VAR_REF_EXPR, this::visitVarRef);
        addVisit(BINARY_EXPR, this::visitBinExpr);
        addVisit(INTEGER_LITERAL, this::visitInteger);
        addVisit(METHOD_CALL, this::visitMethodCall);

        setDefaultVisit(this::defaultVisit);
    }

    private OllirExprResult visitInteger(JmmNode node, Void unused) {
        var intType = new Type(TypeUtils.getIntTypeName(), false);
        String ollirIntType = OptUtils.toOllirType(intType);
        String code = node.get("value") + ollirIntType;
        return new OllirExprResult(code);
    }

    private OllirExprResult visitBinExpr(JmmNode node, Void unused) {

        var lhs = visit(node.getJmmChild(0));
        var rhs = visit(node.getJmmChild(1));

        StringBuilder computation = new StringBuilder();

        // code to compute the children
        computation.append(lhs.getComputation());
        computation.append(rhs.getComputation());

        // code to compute self
        Type resType = TypeUtils.getExprType(node, table);
        String resOllirType = OptUtils.toOllirType(resType);
        String code = OptUtils.getTemp() + resOllirType;

        computation.append(code).append(SPACE)
                .append(ASSIGN).append(resOllirType).append(SPACE)
                .append(lhs.getCode()).append(SPACE);

        Type type = TypeUtils.getExprType(node, table);
        computation.append(node.get("op")).append(OptUtils.toOllirType(type)).append(SPACE)
                .append(rhs.getCode()).append(END_STMT);

        return new OllirExprResult(code, computation);
    }

    private OllirExprResult visitMethodCall(JmmNode node, Void unused) {
        StringBuilder code = new StringBuilder();

        // Extract the function name (e.g., "println")
        String functionName = node.get("value");

        // Build the OLLIR instruction for the function call
        if (node.getJmmChild(0).getAttributes().contains("name")) {
            if (checkIfImport(node.getJmmChild(0).get("name"))) {
                code.append("invokestatic(");
                code.append(node.getJmmChild(0).get("name"));
            }
        } else {

            code.append("invokevirtual(");
            if (node.getJmmChild(0).getKind().equals("VarRefExpr")) {
                code.append(node.getJmmChild(0).get("name")).append(".");
                code.append(table.getClassName());
            } else {
                code.append(node.getJmmChild(0).get("value")).append(".");
                code.append(table.getClassName());
            }
        }
        //String importFunc = node.getJmmChild(0).get("name");
        //code.append(importFunc); // No target object for static method call
        code.append(", \"");
        code.append(functionName); // Method name (e.g., "println")
        code.append("\"");

        // Extract and append the argument of the function call
        for (int i = 1; i < node.getNumChildren(); i++) {
            code.append(", ");
            code.append(visit(node.getJmmChild(1)).getCode());
        }

        if (table.getReturnType(node.get("value")) != null) {
            code.append(")").append(OptUtils.toOllirType(table.getReturnType(functionName)));
        } else {
            code.append(").V");
        }

        /*
        if (checkIfImport(node.getJmmChild(0).get("name"))) {
            code.append(").V");
        } else {
            code.append(").i32");
        }

         */
        code.append(END_STMT);

        return new OllirExprResult(code.toString());

    }

    private OllirExprResult visitVarRef(JmmNode node, Void unused) {

        var id = node.get("name");
        Type type = TypeUtils.getExprType(node, table);
        String ollirType = OptUtils.toOllirType(type);

        String code = id + ollirType;

        return new OllirExprResult(code);
    }

    private boolean checkIfImport(String name) {
        for (var importID : table.getImports()) {
            if (importID.equals(name)) {
                return true;
            }
        }
        return false;
    }

    /**
     * Default visitor. Visits every child node and return an empty result.
     *
     * @param node
     * @param unused
     * @return
     */
    private OllirExprResult defaultVisit(JmmNode node, Void unused) {

        for (var child : node.getChildren()) {
            visit(child);
        }

        return OllirExprResult.EMPTY;
    }

}
