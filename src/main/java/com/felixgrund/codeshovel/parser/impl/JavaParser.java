package com.felixgrund.codeshovel.parser.impl;import com.felixgrund.codeshovel.changes.*;import com.felixgrund.codeshovel.entities.Ycommit;import com.felixgrund.codeshovel.entities.Yparameter;import com.felixgrund.codeshovel.exceptions.ParseException;import com.felixgrund.codeshovel.parser.AbstractParser;import com.felixgrund.codeshovel.parser.Yfunction;import com.felixgrund.codeshovel.parser.Yparser;import com.felixgrund.codeshovel.wrappers.StartEnvironment;import com.felixgrund.codeshovel.changes.*;import com.github.javaparser.ast.CompilationUnit;import com.github.javaparser.ast.Node;import com.github.javaparser.ast.body.MethodDeclaration;import com.github.javaparser.ast.body.TypeDeclaration;import com.github.javaparser.ast.expr.SimpleName;import com.github.javaparser.ast.nodeTypes.NodeWithName;import com.github.javaparser.ast.nodeTypes.NodeWithSimpleName;import com.github.javaparser.ast.visitor.VoidVisitorAdapter;import org.eclipse.jgit.revwalk.RevCommit;import org.slf4j.Logger;import org.slf4j.LoggerFactory;import java.util.*;public class JavaParser extends AbstractParser implements Yparser {	private Logger log = LoggerFactory.getLogger(JavaParser.class);	public static final String ACCEPTED_FILE_EXTENSION = ".java";	private CompilationUnit rootCompilationUnit;	public JavaParser(StartEnvironment startEnv, String filePath, String fileContent, RevCommit commit) throws ParseException {		super(startEnv, filePath, fileContent, commit);	}	@Override	public Yfunction findFunctionByNameAndLine(String name, int line) {		Yfunction ret = null;		MethodDeclaration method = findMethod(new MethodVisitor() {			@Override			public boolean methodMatches(MethodDeclaration method) {				String methodName = method.getNameAsString();				int methodLineNumber = getMethodStartLine(method); // TODO get() ?				return name.equals(methodName) && line == methodLineNumber;			}		});		if (method != null) {			ret = new JavaFunction(method, repository, this.commit, this.filePath, this.fileContent);		}		return ret;	}	@Override	public List<Yfunction> findFunctionsByLineRange(int beginLine, int endLine) {		List<Yfunction> functions = new ArrayList<>();		List<MethodDeclaration> matchedMethods = findAllMethods(new MethodVisitor() {			@Override			public boolean methodMatches(MethodDeclaration method) {				int lineNumber = getMethodStartLine(method);				return lineNumber >= beginLine && lineNumber <= endLine;			}		});		for (MethodDeclaration method : matchedMethods) {			functions.add(new JavaFunction(method, repository, this.commit, this.filePath, this.fileContent));		}		return transformMethods(matchedMethods);	}	@Override	public List<Yfunction> getAllFunctions() {		List<MethodDeclaration> matchedMethods = findNonAbstractMethods();		return transformMethods(matchedMethods);	}	@Override	public Map<String, Yfunction> getAllFunctionsAsMap() {		List<MethodDeclaration> matchedMethods = findNonAbstractMethods();		return transformMethodsToMap(matchedMethods);	}	private Map<String,Yfunction> transformMethodsToMap(List<MethodDeclaration> methods) {		Map<String, Yfunction> ret = new HashMap<>();		for (MethodDeclaration method : methods) {			Yfunction javaFunction = new JavaFunction(method, repository, this.commit, this.filePath, this.fileContent);			ret.put(javaFunction.getId(), javaFunction);		}		return ret;	}	private List<MethodDeclaration> findNonAbstractMethods() {		return findAllMethods(new MethodVisitor() {			@Override			public boolean methodMatches(MethodDeclaration method) {				return !method.isAbstract();			}		});	}	@Override	public Yfunction findFunctionByOtherFunction(Yfunction otherMethod) {		Yfunction function = null;		String methodNameOther = otherMethod.getName();		List<Yparameter> parametersOther = otherMethod.getParameters();		List<MethodDeclaration> matchedMethods = findAllMethods(new MethodVisitor() {			@Override			public boolean methodMatches(MethodDeclaration method) {				Yfunction yfunction = new JavaFunction(method, repository, commit, filePath, fileContent);				String methodNameThis = yfunction.getName();				List<Yparameter> parametersThis = yfunction.getParameters();				boolean methodNameMatches = methodNameOther.equals(methodNameThis);				boolean parametersMatch = parametersOther.equals(parametersThis);				return methodNameMatches && parametersMatch;			}		});		int numMatches = matchedMethods.size();		if (numMatches == 1) {			function = new JavaFunction(matchedMethods.get(0), this.repository, this.commit, this.filePath, this.fileContent);		} else if (numMatches > 1) {			log.trace("Found more than one matching function. Trying to find correct candidate.");			function = getCandidateWithSameParent(matchedMethods, otherMethod);		}		return function;	}	private Yfunction getCandidateWithSameParent(List<MethodDeclaration> candidates, Yfunction compareMethod) {		for (MethodDeclaration candidateMethod : candidates) {			if (parentNameEquals(candidateMethod, compareMethod)) {				String parentName = ((TypeDeclaration) candidateMethod.getParentNode().get()).getNameAsString();				log.trace("Found correct candidate. Parent name: {}", parentName);				return new JavaFunction(candidateMethod, this.repository, this.commit, this.filePath, this.fileContent);			}		}		return null;	}	private boolean parentNameEquals(MethodDeclaration method, Yfunction compareMethod) {		MethodDeclaration compareMethodRaw = (MethodDeclaration) compareMethod.getRawFunction();		Optional<Node> optionalNode1 = method.getParentNode();		Optional<Node> optionalNode2 = compareMethodRaw.getParentNode();		if (optionalNode1.isPresent() && optionalNode2.isPresent()) {			Node node1 = optionalNode1.get();			Node node2 = optionalNode2.get();			if (node1 instanceof NodeWithSimpleName && node2 instanceof NodeWithSimpleName) {				String nameNode1 = ((NodeWithSimpleName) node1).getName().asString();				String nameNode2 = ((NodeWithSimpleName) node2).getName().asString();				return nameNode1.equals(nameNode2);			}		}		return false;	}	@Override	protected Object parse() throws ParseException {		this.rootCompilationUnit = com.github.javaparser.JavaParser.parse(this.fileContent);		if (this.rootCompilationUnit == null) {			throw new ParseException("Could not parse root compilation unit", this.filePath, this.fileContent);		}		return this.rootCompilationUnit;	}	@Override	public boolean functionNamesConsideredEqual(String aName, String bName) {		return aName != null && aName.equals(bName);	}	@Override	public double getScopeSimilarity(Yfunction function, Yfunction compareFunction) {		MethodDeclaration methodRaw = (MethodDeclaration) function.getRawFunction();		return parentNameEquals(methodRaw, compareFunction) ? 1 : 0;	}	@Override	public String getAcceptedFileExtension() {		return ACCEPTED_FILE_EXTENSION;	}	@Override	public List<Ychange> getMinorChanges(Ycommit commit, Yfunction compareFunction) {		List<Ychange> changes = new ArrayList<>();		Yreturntypechange yreturntypechange = getReturnTypeChange(commit, compareFunction);		Ymodifierchange ymodifierchange = getModifiersChange(commit, compareFunction);		Yexceptionschange yexceptionschange = getExceptionsChange(commit, compareFunction);		Ybodychange ybodychange = getBodyChange(commit, compareFunction);		if (yreturntypechange != null) {			changes.add(yreturntypechange);		}		if (ymodifierchange != null) {			changes.add(ymodifierchange);		}		if (yexceptionschange != null) {			changes.add(yexceptionschange);		}		if (ybodychange != null) {			changes.add(ybodychange);		}		return changes;	}	private List<Yfunction> transformMethods(List<MethodDeclaration> methods) {		List<Yfunction> functions = new ArrayList<>();		for (MethodDeclaration method : methods) {			functions.add(new JavaFunction(method, repository, this.commit, this.filePath, this.fileContent));		}		return functions;	}	private MethodDeclaration findMethod(MethodVisitor visitor) {		MethodDeclaration ret = null;		List<MethodDeclaration> matchedNodes = findAllMethods(visitor);		if (matchedNodes.size() > 0) {			ret = matchedNodes.get(0);		}		return ret;	}	private List<MethodDeclaration> findAllMethods(MethodVisitor visitor) {		this.rootCompilationUnit.accept(visitor, null);		return visitor.getMatchedNodes();	}	public static int getMethodStartLine(MethodDeclaration method) {		return method.getName().getBegin().get().line;	}	public static abstract class MethodVisitor extends VoidVisitorAdapter<Void> {		private List<MethodDeclaration> matchedNodes = new ArrayList<>();		public abstract boolean methodMatches(MethodDeclaration method);		@Override		public void visit(MethodDeclaration method, Void arg) {			super.visit(method, arg);			boolean hasBody = method.getBody().isPresent(); // ignore abstract and interface methods			if (hasBody && methodMatches(method)) {				matchedNodes.add(method);			}		}		public List<MethodDeclaration> getMatchedNodes() {			return matchedNodes;		}	}}