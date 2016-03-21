package jkind.lustre.visitors;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import jkind.lustre.ArrayAccessExpr;
import jkind.lustre.ArrayExpr;
import jkind.lustre.ArrayType;
import jkind.lustre.ArrayUpdateExpr;
import jkind.lustre.BinaryExpr;
import jkind.lustre.BoolExpr;
import jkind.lustre.CastExpr;
import jkind.lustre.CondactExpr;
import jkind.lustre.Constant;
import jkind.lustre.EnumType;
import jkind.lustre.Expr;
import jkind.lustre.IdExpr;
import jkind.lustre.IfThenElseExpr;
import jkind.lustre.InductDataExpr;
import jkind.lustre.InductType;
import jkind.lustre.InductTypeElement;
import jkind.lustre.IntExpr;
import jkind.lustre.NamedType;
import jkind.lustre.Node;
import jkind.lustre.NodeCallExpr;
import jkind.lustre.Program;
import jkind.lustre.QuantExpr;
import jkind.lustre.RealExpr;
import jkind.lustre.RecordAccessExpr;
import jkind.lustre.RecordExpr;
import jkind.lustre.RecordType;
import jkind.lustre.RecordUpdateExpr;
import jkind.lustre.SubrangeIntType;
import jkind.lustre.TupleExpr;
import jkind.lustre.TupleType;
import jkind.lustre.Type;
import jkind.lustre.TypeConstructor;
import jkind.lustre.TypeDef;
import jkind.lustre.UnaryExpr;
import jkind.lustre.VarDecl;
import jkind.util.Util;

/**
 * Assuming everything is well-typed, this class can be used to quickly
 * reconstruct the type of an expression (often useful during translations).
 * This class treats subrange and enumeration types as integer types.
 */
public class TypeReconstructor implements ExprVisitor<Type> {
	private final Map<String, Type> typeTable = new HashMap<>();
	private final Map<String, Type> inductDataTable = new HashMap<>();
	private final Map<String, Type> constantTable = new HashMap<>();
	private final Map<String, Expr> constantDefinitionTable = new HashMap<>();
	private final Map<String, EnumType> enumValueTable = new HashMap<>();
	private final Map<String, Type> variableTable = new HashMap<>();
	private final Map<String, Node> nodeTable = new HashMap<>();

	public TypeReconstructor(Program program) {
		populateTypeTable(program.types);
		populateInductDataTable(program.types);
		populateEnumValueTable(program.types);
		populateConstantTable(program.constants);
		nodeTable.putAll(Util.getNodeTable(program.nodes));
	}

	private void populateInductDataTable(List<TypeDef> typeDefs) {
		for(TypeDef typeDef : typeDefs){
			if(typeDef.type instanceof InductType){
				InductType inductType = (InductType)typeDef.type;
				for(TypeConstructor constructor : inductType.constructors){
					inductDataTable.put(constructor.name, inductType);
					inductDataTable.put(InductDataExpr.CONSTRUCTOR_PREDICATE_PREFIX+constructor.name, NamedType.BOOL);
					for(InductTypeElement element : constructor.elements){
						inductDataTable.put(element.name, element.type);
					}
				}
			}
		}
	}

	/**
	 * This constructor is for use after enumerated values, user types,
	 * constants, and nodes have all been inlined.
	 */
	public TypeReconstructor() {
	}

	private void populateTypeTable(List<TypeDef> typeDefs) {
		typeTable.putAll(Util.createResolvedTypeTable(typeDefs));
	}

	private void populateEnumValueTable(List<TypeDef> typeDefs) {
		for (EnumType et : Util.getEnumTypes(typeDefs)) {
			for (String id : et.values) {
				enumValueTable.put(id, et);
			}
		}
	}

	private void populateConstantTable(List<Constant> constants) {
		for (Constant c : constants) {
			constantDefinitionTable.put(c.id, c.expr);
		}

		for (Constant c : constants) {
			if (c.type == null) {
				constantTable.put(c.id, c.expr.accept(this));
			} else {
				constantTable.put(c.id, resolveType(c.type));
			}
		}
	}

	public void setNodeContext(Node node) {
		variableTable.clear();
		Util.getVarDecls(node).forEach(this::addVariable);
	}
	
	public void addVariable(VarDecl varDecl) {
		variableTable.put(varDecl.id, resolveType(varDecl.type));
	}

	public void addVariables(List<VarDecl> varDecls){
		for(VarDecl var : varDecls){
			addVariable(var);
		}
	}
	
	public void removeVariables(List<VarDecl> varDecls){
		for(VarDecl var : varDecls){
			variableTable.remove(var.id);
		}
	}
	
	@Override
	public Type visit(ArrayAccessExpr e) {
		ArrayType array = (ArrayType) e.array.accept(this);
		return array.base;
	}

	@Override
	public Type visit(ArrayExpr e) {
		return new ArrayType(e.elements.get(0).accept(this), e.elements.size());
	}

	@Override
	public Type visit(ArrayUpdateExpr e) {
		return e.array.accept(this);
	}

	@Override
	public Type visit(BinaryExpr e) {
		switch (e.op) {
		case PLUS:
		case MINUS:
		case MULTIPLY:
			return e.left.accept(this);

		case DIVIDE:
			return NamedType.REAL;

		case INT_DIVIDE:
		case MODULUS:
			return NamedType.INT;

		case EQUAL:
		case NOTEQUAL:
		case GREATER:
		case LESS:
		case GREATEREQUAL:
		case LESSEQUAL:
		case OR:
		case AND:
		case XOR:
		case IMPLIES:
			return NamedType.BOOL;

		case ARROW:
			return e.left.accept(this);

		default:
			throw new IllegalArgumentException();
		}
	}

	@Override
	public Type visit(BoolExpr e) {
		return NamedType.BOOL;
	}

	@Override
	public Type visit(CastExpr e) {
		return e.type;
	}

	@Override
	public Type visit(CondactExpr e) {
		return e.call.accept(this);
	}

	@Override
	public Type visit(IdExpr e) {
		if (variableTable.containsKey(e.id)) {
			return variableTable.get(e.id);
		} else if (constantTable.containsKey(e.id)) {
			return constantTable.get(e.id);
		} else if (constantDefinitionTable.containsKey(e.id)) {
			return constantDefinitionTable.get(e.id).accept(this);
		} else if (enumValueTable.containsKey(e.id)) {
			return NamedType.INT;
		} else {
			throw new IllegalArgumentException("Unknown variable: " + e.id);
		}
	}

	@Override
	public Type visit(IfThenElseExpr e) {
		return e.thenExpr.accept(this);
	}

	@Override
	public Type visit(IntExpr e) {
		return NamedType.INT;
	}

	@Override
	public Type visit(NodeCallExpr e) {
		Node node = nodeTable.get(e.node);
		List<Type> outputs = new ArrayList<>();
		for (VarDecl output : node.outputs) {
			outputs.add(resolveType(output.type));
		}
		return TupleType.compress(outputs);
	}

	@Override
	public Type visit(RealExpr e) {
		return NamedType.REAL;
	}

	@Override
	public Type visit(RecordAccessExpr e) {
		RecordType record = (RecordType) e.record.accept(this);
		return record.fields.get(e.field);
	}

	@Override
	public Type visit(RecordExpr e) {
		if (typeTable.containsKey(e.id)) {
			return typeTable.get(e.id);
		} else {
			// If user types have already been inlined, we reconstruct the type
			Map<String, Type> fields = new HashMap<>();
			for (String field : e.fields.keySet()) {
				fields.put(field, e.fields.get(field).accept(this));
			}
			return new RecordType(e.id, fields);
		}
	}

	@Override
	public Type visit(RecordUpdateExpr e) {
		return e.record.accept(this);
	}

	@Override
	public Type visit(TupleExpr e) {
		List<Type> types = new ArrayList<>();
		for (Expr expr : e.elements) {
			types.add(expr.accept(this));
		}
		return new TupleType(types);
	}

	@Override
	public Type visit(UnaryExpr e) {
		switch (e.op) {
		case NEGATIVE:
			return NamedType.INT;

		case NOT:
			return NamedType.BOOL;

		case PRE:
			return e.expr.accept(this);

		default:
			throw new IllegalArgumentException();
		}
	}

	private Type resolveType(Type type) {
		return type.accept(new TypeMapVisitor() {
			@Override
			public Type visit(SubrangeIntType e) {
				return NamedType.INT;
			}

			@Override
			public Type visit(EnumType e) {
				return NamedType.INT;
			}

			@Override
			public Type visit(NamedType e) {
				if (e.isBuiltin()) {
					return e;
				} else {
					return typeTable.get(e.name);
				}
			}
		});
	}

	@Override
	public Type visit(InductDataExpr e) {
		return inductDataTable.get(e.name);
	}

	@Override
	public Type visit(QuantExpr e) {
		addVariables(e.boundVars);
		e.expr.accept(this);
		removeVariables(e.boundVars);
		return NamedType.BOOL;
	}
}
