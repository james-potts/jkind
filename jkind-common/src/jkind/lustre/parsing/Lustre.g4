grammar Lustre;

program: (typedef | constant | node | recursive)* EOF;

typedef: 'type' ID '=' topLevelType ';';

constant: 'const' ID (':' type)? '=' expr ';';

node:
  'node' ID '(' input=varDeclList? ')'
  'returns' '(' output=varDeclList? ')' ';'
  ('var' local=varDeclList ';')?
  'let'
    (equation | property | assertion | main | realizabilityInputs | support)*
  'tel' ';'?
;

recursive:
  'recursive' ID '(' input=varDeclList? ')'
  'returns' '(' output=varDeclList? ')' ';'
  ('var' local=varDeclList ';')?
  'let'
    (equation)+
  'tel' ';'?
;

varDeclList: varDeclGroup (';' varDeclGroup)*;

varDeclGroup: eID (',' eID)* ':' type;

topLevelType: type                                       # plainType
    | 'struct' '{' (ID ':' type) (';' ID ':' type)* '}'  # recordType
    | 'enum' '{' ID (',' ID)* '}'                        # enumType
    | 'induct' '{' inductTerm ('|' inductTerm)* '}' #inductType 
    ;

inductTerm: 
  ID ('(' ID type ')')*
    ;

type: 'int'                                              # intType
    | 'subrange' '[' bound ',' bound ']' 'of' 'int'      # subrangeType
    | 'bool'                                             # boolType
    | 'real'                                             # realType
    | type '[' INT ']'                                   # arrayType
    | ID                                                 # userType
    ;

bound: '-'? INT;

property: '--%PROPERTY' eID ';';

contract: contract_id (require | ensure)*;

contract_id: '--@contract' ':' ID ';';
ensure: '--@ensure' expr ';';
require: '--@require' expr ';'; 

realizabilityInputs: '--%REALIZABLE' (ID (',' ID)*)? ';';

support: '--%SUPPORT' (ID (',' ID)*)? ';';

main: '--%MAIN' ';'?;

assertion: 'assert' expr ';';

equation: (lhs | '(' lhs? ')') '=' expr ';';

lhs: eID (',' eID)*;

expr: ID                                                       # idExpr
    | INT                                                      # intExpr
    | REAL                                                     # realExpr
    | BOOL                                                     # boolExpr
    | op=('real' | 'floor') '(' expr ')'                       # castExpr
    | ID '(' (expr (',' expr)*)? ')'                           # nodeCallExpr
    | ID (expr)*                                               # inductDataExpr    
    | 'condact' '(' expr (',' expr)+ ')'                       # condactExpr
    | expr '.' ID                                              # recordAccessExpr
    | expr '{' ID ':=' expr '}'                                # recordUpdateExpr
    | expr '[' expr ']'                                        # arrayAccessExpr
    | expr '[' expr ':=' expr ']'                              # arrayUpdateExpr
    | 'pre' expr                                               # preExpr
    | 'not' expr                                               # notExpr
    | '-' expr                                                 # negateExpr
    | expr op=('*' | '/' | 'div' | 'mod') expr                 # binaryExpr
    | expr op=('+' | '-') expr                                 # binaryExpr
    | expr op=('<' | '<=' | '>' | '>=' | '=' | '<>') expr      # binaryExpr
    | expr op='and' expr                                       # binaryExpr
    | expr op=('or' | 'xor') expr                              # binaryExpr
    | <assoc=right> expr op='=>' expr                          # binaryExpr
    | <assoc=right> expr op='->' expr                          # binaryExpr
    | 'if' expr 'then' expr 'else' expr                        # ifThenElseExpr
    | ('forall' | 'exists') '(' vars=varDeclList ')' '.' expr  # quantExpr
    | ID '{' ID '=' expr (';' ID '=' expr)* '}'                # recordExpr
    | '[' expr (',' expr)* ']'                                 # arrayExpr
    | '(' expr (',' expr)* ')'                                 # tupleExpr
    ;

// eID used internally. Users should only use ID.
eID: ID                                                        # baseEID
   | eID '[' INT ']'                                           # arrayEID
   | eID '.' ID                                                # recordEID
   ;

REAL: INT '.' INT;

BOOL: 'true' | 'false';
INT: [0-9]+;

// ~ is used internally. Users should not use it.
ID: [a-zA-Z_~][a-zA-Z_0-9~]*;

WS: [ \t\n\r\f]+ -> skip;

SL_COMMENT: '--' (~[%\n\r] ~[\n\r]* | /* empty */) ('\r'? '\n')? -> skip;
ML_COMMENT: '/*' .*? '*/' -> skip;

ERROR: .;
