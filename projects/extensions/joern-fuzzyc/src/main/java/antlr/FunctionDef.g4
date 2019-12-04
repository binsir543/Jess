grammar FunctionDef;
import ModuleLex;

function_def : template_decl_start? NEWLINE* return_type? NEWLINE* function_name NEWLINE*
            function_param_list ctor_list? compound_statement;

return_type : (function_decl_specifiers* NEWLINE* type_name) ptr_operator*
				| macroCall
				;

function_param_list : '(' parameter_decl_clause? ')' '*'? CV_QUALIFIER*  exception_specification?;

parameter_decl_clause: (parameter_decl (',' NEWLINE* parameter_decl)* ) (',' NEWLINE* '...' )?;
                     
parameter_decl : VOID 
                | param_decl_specifiers parameter_id;

parameter_id: ptrs? ('(' parameter_id ')' | parameter_name) type_suffix?;

compound_statement: OPENING_CURLY { skipToEndOfObject(); } ;

ctor_list: ':'  ctor_initializer (',' ctor_initializer)*;

ctor_initializer:  initializer_id ctor_expr;

initializer_id : '::'? identifier;

ctor_expr:  '(' expr? ')';

function_name: '(' function_name ')' | identifier | OPERATOR operator;

exception_specification : THROW '(' type_id_list ')';

type_id_list: no_brackets* ('(' type_id_list ')' no_brackets*)*;
