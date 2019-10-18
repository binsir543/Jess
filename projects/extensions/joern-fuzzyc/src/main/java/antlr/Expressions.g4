grammar Expressions;

expr: assign_expr NEWLINE* (',' NEWLINE* expr)?;

assign_expr: conditional_expression NEWLINE* (assignment_operator NEWLINE* assign_expr)?;

conditional_expression: or_expression #normOr
		      | or_expression NEWLINE* ('?' expr ':' conditional_expression) #cndExpr;


or_expression : and_expression NEWLINE* ('||' NEWLINE* or_expression)?;

and_expression : inclusive_or_expression NEWLINE* ('&&' NEWLINE* and_expression)?;

inclusive_or_expression: exclusive_or_expression NEWLINE* ('|' NEWLINE* inclusive_or_expression)?;

exclusive_or_expression: bit_and_expression NEWLINE* ('^' NEWLINE* exclusive_or_expression)?;

bit_and_expression: equality_expression NEWLINE* ('&' NEWLINE* bit_and_expression)?;

equality_expression: relational_expression NEWLINE* (equality_operator NEWLINE* equality_expression)?;

relational_expression: shift_expression NEWLINE* (relational_operator NEWLINE* relational_expression)?;

shift_expression: additive_expression NEWLINE* ( ('<<'|'>>') NEWLINE* shift_expression)?;

additive_expression: multiplicative_expression NEWLINE* (('+'| '-') NEWLINE* additive_expression)?;

multiplicative_expression: cast_expression NEWLINE* ( ('*'| '/'| '%') NEWLINE* multiplicative_expression)?;

cast_expression: ('(' cast_target ')' NEWLINE* cast_expression)
               | unary_expression
                ;

cast_target: type_name NEWLINE* ptr_operator*;

// currently does not implement delete

unary_expression: unary_op_and_cast_expr
                | sizeof_expression 
                | new_expression
                | postfix_expression
                | defined_expression
                ;
                
/*inc_dec cast_expression  
*        unary_expression
*                | postfix_expression
*                       primary_expression
*/   
 
 
new_expression: '::'? NEW NEWLINE* type_name NEWLINE* '[' conditional_expression? ']' 
              | '::'? NEW NEWLINE* type_name NEWLINE* '(' expr? ')'
              ;

unary_op_and_cast_expr: unary_operator NEWLINE* cast_expression;

sizeof_expression: sizeof NEWLINE* '(' sizeof_operand ')'
                 | sizeof NEWLINE* sizeof_operand2;

sizeof: 'sizeof';

defined_expression: 'defined' NEWLINE* '(' expr ')'
					|  'defined' NEWLINE* expr ;

sizeof_operand: type_name NEWLINE* ptr_operator *;
sizeof_operand2: unary_expression;

inc_dec: ('--' | '++');

// this is a bit misleading. We're just allowing access_specifiers
// here because C programs can use 'public', 'protected' or 'private'
// as variable names.

postfix_expression: postfix_expression NEWLINE* '[' expr ']' #arrayIndexing
                  | postfix_expression NEWLINE* '(' function_argument_list ')' #funcCall
                  | postfix_expression '.' NEWLINE* TEMPLATE? (identifier) #memberAccess
                  | postfix_expression '->' NEWLINE* TEMPLATE? (identifier) #ptrMemberAccess
                  | postfix_expression NEWLINE* inc_dec #incDecOp
                  | primary_expression # primaryOnly
                  | inc_dec NEWLINE* primary_expression #incDecOp
                  ;

function_argument_list: ( function_argument+ NEWLINE* (',' function_argument+)* )?;
function_argument: NEWLINE* assign_expr NEWLINE*;


primary_expression: identifier | constant | '(' expr ')';

