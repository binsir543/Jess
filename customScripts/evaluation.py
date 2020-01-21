#!/usr/bin/env python3
from octopus.server.DBInterface import DBInterface
import subprocess
import os
import shutil


#Name and path of the project
projectNameAndPath = {'Collection':'/Joern_Advanced/testProjects/Collection/src/', 
'EvoDiss.tar.gz':'/home/lea/Downloads/EvoDiss/src/', 
'ICSE':'/home/lea/Downloads/', 
'JoernTest.tar.gz':'/home/lea/Downloads/Joern_Advanced/testProjects/Tests/', 
'Linux3':'/home/lea/Downloads/linux-3.16.72/', 
'Marlin':'/Marlin-1.1.9/', 
'Revamp':'/home/lea/Downloads/Joern_Advanced/testProjects/', 
'SPLC':'/Joern_Advanced/testProjects/SPLC/src/', 
'TestCommit':'/src/',
'expat':'/libexpat/',
'Origin.tar.gz':'/C/src/',
'PL_Current.tar.gz':'/C/src/',
'PV_Current.tar.gz':'/C/src/',
'sample':'/Example/'} 

#Assemble path 
projectName = "PL_Current.tar.gz"
projectPath = projectNameAndPath[projectName]

pathToPatch = 'Patch/'
basePath = os.getcwd().replace("/customScripts", "/projects/octopus/data/projects/"+projectName+"/src")
pathToOriginalProject = basePath+projectPath

db = DBInterface()
db.connectToDatabase(projectName)

visibleStatementTypes = ['CustomNode', 'ClassDef', 'DeclByClass', 'DeclByType', 'FunctionDef', 'CompoundStatement', 'DeclStmt', 'StructUnionEnum', 'TryStatement', 'CatchStatement', 'IfStatement', 'ElseStatement', 'SwitchStatement', 'ForStatement', 'DoStatement', 'WhileStatement', 'BreakStatement', 'ContinueStatement', 'GotoStatement', 'Label', 'ReturnStatement', 'ThrowStatement', 'ExpressionStatement', 'IdentifierDeclStatement', 'PreIfStatement', 'PreElIfStatement', 'PreElseStatement', 'PreEndIfStatement', 'PreDefine', 'PreUndef', 'PreDiagnostic', 'PreOther', 'PreInclude', 'PreIncludeNext', 'PreLine', 'PrePragma', 'UsingDirective', 'BlockCloser', 'Comment']



# Returns all AST vertices of the SemanticUnit that directly appear in the code (CFG nodes or direct children of file nodes)    
def getVisibleNodes():
    # Remove unneeded nodes (we need to exclude IdentifierDeclStatement that have a ForInit or StructUnionEnum as parent)
    query = """g.V().has('type', within(%s))
                .not(has('type', 'IdentifierDeclStatement').in(AST_EDGE).has('type', within('ForInit','StructUnionEnum')))
                .id()""" % (visibleStatementTypes) 
    result = db.runGremlinQuery(query)
    return result  

def fileOutput (result):    
    with open('result.txt', 'w') as file_handler:
        file_handler.write(projectName+"\n")
        for item in result:
            file_handler.write("{}\n".format(item))  
            
               
print("Export visible node ids to file...")
fileOutput(getVisibleNodes())      
      
print("Convert project back to source code...")
from patchCreator import createPatch

print("Compare with original source code...")
#Make new empty temp dir
foldername = "Temp"
if os.path.exists(foldername):
    shutil.rmtree(foldername)

os.makedirs(foldername)
#Finds all files in the original directory that end with .c or .h and copies them in the temporary folder 
#(necessary because git diff --no-index does not allow for filtering of filetypes
os.system("find "+pathToOriginalProject+" -iname '*.[c|h]' -exec cp '{}' "+foldername+"/ \;")
os.system("git diff -w -b --no-index "+foldername+" "+pathToPatch+"   > EvaluationResult.txt")   
 
    
if (os.stat("EvaluationResult.txt").st_size == 0):
    print("* * * Evaluation was successful, no differences found * * *")
else:
    print("Found some differences, please look at EvaluationResult.txt for more details")



