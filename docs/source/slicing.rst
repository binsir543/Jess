Semantic Unit Identification
==========================

Introduction
--------------

The use case of the Semantic Unit identification process is to automatically migrate useful changes between a product line and separated products. These changes can be assigned to Semantic Units.

A Semantic Unit consists of semantically related lines of code. Semantically related means, that there is a common purpose belonging to the related code. This purpose can for example be defined by a test case. Thus, all code that is needed to make a test case pass belongs to one Semantic Unit.

We need a user given entry point do identify a Semantic Unit, because the user decides, which useful changes should be migrated. The idea is, that the user selects only one line of code, for example a method declaration or an assert statement from a test case. Based on this selection, the proposed approach identifies all semantically related lines of code and, hence, the Semantic Unit.

Depending on the entry point, there are different possibilities of what should belong to the Semantic Unit. For example, when the user selects a line of code with an if-statement. Does a following else-statement also belong to the Semantic Unit? This decision is arguable, so the user should have the possibility to configure the desired behavior. The next sections describe the general idea, the input and output relations, the configurable decisions and the default configuration of the Semantic Unit Identification process.

General Idea
--------------

The Semantic Unit Identification process identifies semantically related lines of code based on one or more given entry points. The underlaying structure of the process is a graph database, consisting of AST (Abstract Syntax Tree) elements and relations between them. These relations are not only AST relations (is parent of), but also data flow (uses, defines) and control flow (controls) relations.

-**Input:** AST Element: Entry Point

-**Output:** AST Element: Semantic Unit, Relations of Semantic Unit

-**Function:** Set(Entry Point) -> Set(Semantic Unit) | There is a relation between all elements


Input/Output Relations
--------------

The selected elements of the Semantic Unit (output) depend on the type of the given entry point (input). All type-based decisions and the resulting input and output relations are listed below and described in form of Input Type -> Resulting Output. Some of these decisions are configurable and described again in detail in the next section **Configuration Options**.

• **Working:**
	• Directory -> All included files in this directory
	• File -> All included code elements in this file 
	• Function Ddefinition -> All enclosed code elements (configurable)
	• If, for, while statements -> All enclosed code elements (configurable)
	• Else statement -> Corresponding if statement
	• Callee, call expression -> Called function or macro definition, #include statement if target is in another file
	• Function -> Function definition
	• Configuration option -> All #if/#ifdef/#elif nodes and their enclosed content

• **Todo:**
	• Identifier Declaration
	• Symbol
	• Identifier -> Declaration of Identifier (atm not working)
	• Assignment Expression
	• Argument, Argument List, Condition, Unary Expression -> All variable and function definitions?
	• Function, macro definition -> Callees

• **Do nothing for:**
	• 'AdditiveExpression' a + b
	• 'PrimaryExpression' 1
	• 'IncDec' ++
	• 'UnaryOperator' !
	• 'UnaryOperationExpression' - 1
	• 'ArrayIndexing' array[1]
	• 'ReturnType' void
	• 'CFGEntryNode' ENRTY
	• 'CFGExitNode' EXIT
	• 'InitializerList' 7 (size of list)
	• 'ForInit' i = 0
	• 'IdentifierDeclType' int (contained in IdentifierDeclStatement)
	• 'IdentifierDecl' i (contained in IdentifierDeclStatement)
	• 'Parameter' i (contained in ParameterList)
	• 'ParameterType' int (contained in ParameterList)
	• 'ParameterList' int i (contained in FunctionDef)
	• 'RelationalExpression' i > 5 (contained in condition)
	• 'Sizeof', 'SizeofOperand'  (contained in call expression)
	• 'CompoundStatement' (container element)


Variability Handling
--------------

In our use-case, variability is realized with conditional compilation through #ifdef preprocessor annotations. The preprocessor-code is parsed into the graph database, like the normal C-code. The AST structure of the preprocessor-code is separated from the C-code structure. Preprocessor statements are either AST children of their parent file, class or function. The only exception are #elif/#else and #endif statements, as they are AST children of their respective blockstarter-statement, e.g. #endif is AST child of #else, which is an AST child of #if. Furthermore, a C-statement is never an AST child of a preprocessor-statement. Instead, whenever a line of code is annotated with an #if/#ifdef statement, it is connected with a variability edge. This allows us to specifically analyze variability relationships.


Configuration Options
--------------

• Include enclosed code
	• Explanation: Whenever a syntax structure is selected that encloses code, this code is included in the Semantic Unit. 
	• Example entry point: A method declaration 
	• Effect on Semantic Unit: All code inside the method belongs to the Semantic Unit (and thus probably makes the result bigger, decreases precision and increases recall)
	• Hint: You should not turn this off when you plan to use structure-based entry points (like class/method declaration), as the result will be empty. You can turn this off when you use behavior-based entry points like assert statements from test cases. Deactivation makes the result strongly rely on the quality of your test case.

• Select multiple entry points
	• Explanation: The user can select more than one line of code as an entry point.
	• Example entry point: Two declarations of different test methods 
	• Effect on Semantic Unit: The result is a conjunction of the Semantic Units for each entry point
	• Hint: The identification process will take longer, the more lines of code you select as entry points.

• Connect if with else
	• Explanation: Always connect an existing else-statement, whenever an if-statement is selected
	• Example entry point: An if-statement that has one else statement
	• Effect on Semantic Unit: The else statement is added to the Semantic Unit
	• Hint: Deactivate only if you want to focus on special cases and not on the whole case distinction. Deactivation has no effect, if the include enclosed code option is activated.

• Search directories recursively
	• Explanation: When a directory node is analyzed, all contained directories are added to the Semantic Unit and then recursively analyzed
	• Example entry point: A directory which contains one or more directories
	• Effect on Semantic Unit: All contained directories (on all levels underneath) are added to the Semantic Unit
	• Hint: Activate if you want to recursively add all directories under a given root node. This can result in very big Semantic Units. Deactivate if you want to stay on the current directory level.

• Include other features
	• Explanation: When we search for the semantically related lines for a specific feature, we only expand for the occurrence of this feature name. When we reach an implementation that is connected to another feature (via incoming variability edges), we do not search for all other implementations that are annotated with this other feature. We do include the implementations that were reached through all (except variability) edges. 
	• Example entry point: A feature identifier
	• Effect on Semantic Unit: All blocks that are annotated with an #ifdef that contains the identifier of the entry-point-feature are added to the Semantic Unit. All other variability links (connected to different feature identifiers) will not be followed/analyzed.
	• Hint: Activate if you want to follow all appearing variability links and include all implementations of a feature whenever you reach one of its implementations. This can result in very big Semantic Units. Deactivate if you want to focus on the current feature(s).


Default Configuration
--------------

• Include enclosed code: TRUE
• Select multiple entry points: FALSE
• Connect if with else: TRUE
• Search directories recursively: TRUE
• Include other features: FALSE


How to use
--------------

First, you have to start the jess-server (separate terminal) and import a project. Then open a new terminal and navigate to the customScripts folder. Edit the name of the project into the SUI.py script (variable projectName, Line 23), enter an entry point (the id of a node in variable entryPointId in Line 43 or the name of a configuration option in variable entryFeatureNames in Line 45) and change the configuration (Lines 11-15) to your needs. Then, you can run the SUI script. 

.. code-block:: none

	cd $JESS/customScripts
	nano SUI.py
	python3 SUI.py

The script will now iteratively gather all semantically related lines to your given entry point. It will output the result as a Graphviz .dot file and a .png file in the folder $JESS/customScripts/SemanticUnit. 