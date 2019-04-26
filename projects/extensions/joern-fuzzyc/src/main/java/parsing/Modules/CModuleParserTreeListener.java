package parsing.Modules;

import java.util.Iterator;
import java.util.List;
import java.util.Stack;

import org.antlr.v4.runtime.ParserRuleContext;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import antlr.ModuleBaseListener;
import antlr.ModuleParser;
import antlr.ModuleParser.Class_defContext;
import antlr.ModuleParser.DeclByClassContext;
import antlr.ModuleParser.Init_declarator_listContext;
import antlr.ModuleParser.Type_nameContext;
import ast.ASTNode;
import ast.c.preprocessor.blockstarter.PreEndIfStatement;
import ast.c.preprocessor.blockstarter.PreIfStatement;
import ast.declarations.IdentifierDecl;
import ast.logical.statements.CompoundStatement;
import ast.preprocessor.PreBlockstarter;
import ast.preprocessor.PreStatementBase;
import ast.statements.IdentifierDeclStatement;
import parsing.ANTLRParserDriver;
import parsing.ASTNodeFactory;
import parsing.CompoundItemAssembler;
import parsing.ModuleFunctionParserInterface;
import parsing.Functions.ANTLRCFunctionParserDriver;
import parsing.Functions.builder.FunctionContentBuilder;
import parsing.Modules.builder.FunctionDefBuilder;
import parsing.Shared.builders.ClassDefBuilder;
import parsing.Shared.builders.IdentifierDeclBuilder;

// Converts Parse Trees to ASTs for Modules

public class CModuleParserTreeListener extends ModuleBaseListener {

	ANTLRParserDriver p;
	// For variability analysis and preprocessor statements AST nesting
	ANTLRCFunctionParserDriver fDriver;
	/**
	 * This stack contains PreBlockstarters that can implement variability
	 */
	private Stack<ASTNode> variabilityItemStack = new Stack<ASTNode>();
	/**
	 * This stack contains PreBlockstarters that can be nested on AST level (including #endif)
	 */
	private Stack<ASTNode> preASTItemStack = new Stack<ASTNode>();	
	private static final Logger logger = LoggerFactory.getLogger(CModuleParserTreeListener.class);


	public CModuleParserTreeListener(ANTLRParserDriver aP) {
		p = aP;
	}

	@Override
	public void enterCode(ModuleParser.CodeContext ctx) {
		p.notifyObserversOfUnitStart(ctx);
	}

	@Override
	public void exitCode(ModuleParser.CodeContext ctx) {
		p.notifyObserversOfUnitEnd(ctx);
	}

	// /////////////////////////////////////////////////////////////
	// This is where the ModuleParser invokes the FunctionParser
	// /////////////////////////////////////////////////////////////
	// This function is invoked when a Function_Def parse tree node
	// is entered. This is where we hand over the function contents to
	// the function parser and connect the AST node created for the
	// function definition to the AST created by the function parser.
	// ////////////////////////////////////////////////////////////////

// --------------------------------Preprocessor-----------------------------------------------------------------
	/**
	 * This builder calls the @FunctionParser, because PreStatements follow the same
	 * rules on module and on function level. As we dont want cloned code, we simple
	 * parse the pre statements with the function parser and connect the result on
	 * module level.
	 */
	@Override
	public void enterPre_statement(ModuleParser.Pre_statementContext ctx) {
		// Driver for calling function parser
		fDriver = new ANTLRCFunctionParserDriver();
		// Get code of PreStatement
		PreStatementBase thisItem = new PreStatementBase();
		ASTNodeFactory.initializeFromContext(thisItem, ctx);
		String text = thisItem.getEscapedCodeStr();
		// Try to reuse the function parser rules for parsing the preprocessor statement
		try {
			fDriver.parseAndWalkString(text);
			FunctionContentBuilder fb = (FunctionContentBuilder) fDriver.builderStack.pop();
			thisItem = (PreStatementBase) fb.getItem().getChild(0);
			//#elif/#else/#endif are not on the builderStack and therefore null, we get them via a separate attribute
			if (thisItem == null)
				thisItem = (PreStatementBase) fb.currentItem;
		} catch (Exception e) {
			System.err.println("Cannot create PreStatement " + text + " in ModuleParser");
			e.printStackTrace();
		}
		
		//VARIABILITY ANALYSIS first
		variabilityAnalysis(thisItem);
		//AST ANALYSIS second
		astAnalysis(thisItem);				
	}
	
	/**
	 * Decides, how to behave for the variability analysis
	 */
	private void variabilityAnalysis(ASTNode thisItem) {
		// If the current item is an #endif
		if (thisItem instanceof PreEndIfStatement) {
			// Remove items from stack until the next #if/#ifdef
			closeVariabilityBlock();
			logger.debug("Block closed!");
		} else if (thisItem instanceof PreBlockstarter) {
			// Collect all Pre Blockstarters on the Stack
			variabilityItemStack.push(thisItem);
			logger.debug("#if collected");
		} else {
			// Connect all other pre statements to parent blockstarters if they exist
			checkVariability(thisItem);
		}
	}
	
	/**
	 * Decides, how to behave for the AST analysis
	 */
	private void astAnalysis(ASTNode thisItem) {
		//Resolve AST nesting of preprocessor blockstarters on module level. This has to be done before the db nodes are built.
		if (thisItem instanceof PreEndIfStatement) {
			preASTItemStack.push(thisItem);
			// Remove items from stack until the next #if/#ifdef
			closeASTBlock();
			logger.debug("AST block closed!");
		} else if (thisItem instanceof PreBlockstarter) {
			// Collect all Pre Blockstarters on the AST stack
			preASTItemStack.push(thisItem);
			logger.debug("#if collected in AST stack");
		} else {
			//Notify OutModASTNodeVisitor, to call AST to database converter (PreStatementExporter class). 
			//Do this now for every pre statement that has no AST nesting
			p.notifyObserversOfItem(thisItem);
		}	
	}
	

	/**
	 * Check if the current ASTNode is inside an #ifdef block and therefore variable
	 * 
	 * @param currentNode
	 */
	private void checkVariability(ASTNode currentNode) {
		if (!variabilityItemStack.isEmpty()) {
			PreBlockstarter parent = (PreBlockstarter) variabilityItemStack.peek();
			parent.addVariableStatement(currentNode);

			logger.debug("Connected variability child: "+currentNode.getEscapedCodeStr()+" with parent: "+parent.getEscapedCodeStr());
		} else {
			logger.debug(currentNode.getEscapedCodeStr()+" is not variable!");
		}
	}

	/**
	 * Removes collected PreBlockstarters from the stack. 
	 * Stop if the stack is empty or if we reach an PreIfStatement.
	 */
	private void closeVariabilityBlock() {
		if (!variabilityItemStack.isEmpty()) {
			PreBlockstarter currentNode = (PreBlockstarter) variabilityItemStack.pop();
			
			// Stop if we reach an PreIfStatement
			if (currentNode instanceof PreIfStatement) {
				checkVariability(currentNode);
			} else {
				closeVariabilityBlock();
			}
		}
	}
	
	/**
	 * Removes collected PreBlockstarters from the stack and connects them with AST relations.
	 * When this method is first called, the top stack item should be an #endif
	 * Stop if the AST stack is empty or if we reach an PreIfStatement.
	 */
	private void closeASTBlock() {
		if (preASTItemStack.size() > 1) {
			//Remove the current AST node from the stack
			PreBlockstarter currentNode = (PreBlockstarter) preASTItemStack.pop();
			//Look at the next node on the stack
			PreBlockstarter topOfStack = (PreBlockstarter) preASTItemStack.peek();		
			//Connect the current node with its parent
			topOfStack.addChild(currentNode);	
			logger.debug("Connected AST child: "+currentNode.getEscapedCodeStr()+" with parent: "+topOfStack.getEscapedCodeStr());
		
			// Stop if we reach an PreIfStatement
			if (topOfStack instanceof PreIfStatement) {
				//Remove the PreIfStatement node from the stack and stop the iteration
				currentNode = (PreBlockstarter) preASTItemStack.pop();
				logger.debug("Found #if for #endif, notify observers");
				//Notify OutModASTNodeVisitor, to call AST to database converter (PreStatementExporter class). 
				//Do this now (and not sooner), because otherwise the preprocessor database node would be initialized without its children or twice
				//Do not do this for child #else/#elif/#endif, they will be automatically added, as they are AST children of the first PreIfStatement
				p.notifyObserversOfItem(currentNode);
								
			} else {
				//Connect AST children until we reach a PreIfStatement or there is only 1 item left on the stack
				closeASTBlock();
			}
			
		} else if (preASTItemStack.size() == 1)  {
			//Remove orphaned #endif statements
			PreBlockstarter lastNode = (PreBlockstarter) preASTItemStack.pop();
			System.err.println("Removed orphan: "+lastNode.getEscapedCodeStr()+ " in: "+lastNode.getLocation());
			//Notify OutModASTNodeVisitor, to call AST to database converter (PreStatementExporter class)
			p.notifyObserversOfItem(lastNode);
		}
	}


// --------------------------------------Preprocessor end-------------------------------------------------------------------------

	@Override
	public void enterFunction_def(ModuleParser.Function_defContext ctx) {
		logger.debug("Enter functionDef");

		FunctionDefBuilder builder = new FunctionDefBuilder();
		builder.createNew(ctx);
		p.builderStack.push(builder);

		CompoundStatement functionContent = ModuleFunctionParserInterface.parseFunctionContents(ctx);
		builder.setContent(functionContent);
	}

	@Override
	public void exitFunction_def(ModuleParser.Function_defContext ctx) {
		FunctionDefBuilder builder = (FunctionDefBuilder) p.builderStack.pop();
		p.notifyObserversOfItem(builder.getItem());
		
		// Connect to parent blockstarters if they exist
		checkVariability(builder.getItem());
		

	}

	@Override
	public void enterReturn_type(ModuleParser.Return_typeContext ctx) {
		FunctionDefBuilder builder = (FunctionDefBuilder) p.builderStack.peek();
		builder.setReturnType(ctx, p.builderStack);
	}

	@Override
	public void enterFunction_name(ModuleParser.Function_nameContext ctx) {
		FunctionDefBuilder builder = (FunctionDefBuilder) p.builderStack.peek();
		builder.setName(ctx, p.builderStack);
	}

	@Override
	public void enterFunction_param_list(ModuleParser.Function_param_listContext ctx) {
		FunctionDefBuilder builder = (FunctionDefBuilder) p.builderStack.peek();
		builder.setParameterList(ctx, p.builderStack);
	}

	@Override
	public void enterParameter_decl(ModuleParser.Parameter_declContext ctx) {
		FunctionDefBuilder builder = (FunctionDefBuilder) p.builderStack.peek();
		builder.addParameter(ctx, p.builderStack);
	}

	// DeclByType

	@Override
	public void enterDeclByType(ModuleParser.DeclByTypeContext ctx) {
		logger.debug("Enter enterDeclByType");
		Init_declarator_listContext decl_list = ctx.init_declarator_list();
		Type_nameContext typeName = ctx.type_name();
		emitDeclarations(decl_list, typeName, ctx);
	}

	private void emitDeclarations(ParserRuleContext decl_list, ParserRuleContext typeName, ParserRuleContext ctx) {
		IdentifierDeclBuilder builder = new IdentifierDeclBuilder();
		List<IdentifierDecl> declarations = builder.getDeclarations(decl_list, typeName);

		IdentifierDeclStatement stmt = new IdentifierDeclStatement();
		// stmt.initializeFromContext(ctx);

		Iterator<IdentifierDecl> it = declarations.iterator();
		while (it.hasNext()) {
			IdentifierDecl decl = it.next();
			stmt.addChild(decl);
			//stmt is not a node, instead check decl?
			checkVariability(decl);
		}

		p.notifyObserversOfItem(stmt);
		
	}

	// DeclByClass

	@Override
	public void enterDeclByClass(ModuleParser.DeclByClassContext ctx) {
		logger.debug("Enter enterDeclByClass");
		ClassDefBuilder builder = new ClassDefBuilder();
		builder.createNew(ctx);
		p.builderStack.push(builder);
	}

	@Override
	public void exitDeclByClass(ModuleParser.DeclByClassContext ctx) {
		ClassDefBuilder builder = (ClassDefBuilder) p.builderStack.pop();

		CompoundStatement content = parseClassContent(ctx);
		builder.setContent(content);
		
		p.notifyObserversOfItem(builder.getItem());
		emitDeclarationsForClass(ctx);
				
		//Connect to parent blockstarters if they exist
		checkVariability(builder.getItem());
	}

	@Override
	public void enterClass_name(ModuleParser.Class_nameContext ctx) {
		ClassDefBuilder builder = (ClassDefBuilder) p.builderStack.peek();
		builder.setName(ctx);
	}

	private void emitDeclarationsForClass(DeclByClassContext ctx) {

		Init_declarator_listContext decl_list = ctx.init_declarator_list();
		if (decl_list == null)
			return;

		ParserRuleContext typeName = ctx.class_def().class_name();
		emitDeclarations(decl_list, typeName, ctx);
	}

	private CompoundStatement parseClassContent(ModuleParser.DeclByClassContext ctx) {
		ANTLRCModuleParserDriver shallowParser = createNewShallowParser();
		CompoundItemAssembler generator = new CompoundItemAssembler();
		shallowParser.addObserver(generator);

		restrictStreamToClassContent(ctx);
		shallowParser.parseAndWalkTokenStream(p.stream);
		p.stream.resetRestriction();

		return generator.getCompoundItem();
	}

	private void restrictStreamToClassContent(ModuleParser.DeclByClassContext ctx) {
		Class_defContext class_def = ctx.class_def();
		int startIndex = class_def.OPENING_CURLY().getSymbol().getTokenIndex();
		int stopIndex = class_def.stop.getTokenIndex();

		p.stream.restrict(startIndex + 1, stopIndex);
	}

	private ANTLRCModuleParserDriver createNewShallowParser() {
		ANTLRCModuleParserDriver shallowParser = new ANTLRCModuleParserDriver();
		shallowParser.setStack(p.builderStack);
		return shallowParser;
	}

}
