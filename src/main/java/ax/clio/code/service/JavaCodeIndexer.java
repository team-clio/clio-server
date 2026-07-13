package ax.clio.code.service;

import ax.clio.code.entity.CodeFile;
import ax.clio.code.entity.CodeSymbol;
import ax.clio.code.entity.CodeSymbolType;

import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

import ax.clio.project.entity.Project;
import com.github.javaparser.StaticJavaParser;
import com.github.javaparser.ast.CompilationUnit;
import com.github.javaparser.ast.ImportDeclaration;
import com.github.javaparser.ast.Node;
import com.github.javaparser.ast.body.AnnotationDeclaration;
import com.github.javaparser.ast.body.BodyDeclaration;
import com.github.javaparser.ast.body.ClassOrInterfaceDeclaration;
import com.github.javaparser.ast.body.ConstructorDeclaration;
import com.github.javaparser.ast.body.EnumDeclaration;
import com.github.javaparser.ast.body.FieldDeclaration;
import com.github.javaparser.ast.body.MethodDeclaration;
import com.github.javaparser.ast.body.RecordDeclaration;
import com.github.javaparser.ast.body.TypeDeclaration;
import com.github.javaparser.ast.expr.AnnotationExpr;
import org.springframework.stereotype.Component;

@Component
public class JavaCodeIndexer {

	public List<CodeSymbol> index(Project project, CodeFile codeFile, Path filePath) {
		try {
			CompilationUnit compilationUnit = StaticJavaParser.parse(filePath);
			String packageName = compilationUnit.getPackageDeclaration()
					.map(packageDeclaration -> packageDeclaration.getName().asString())
					.orElse("");
			String imports = compilationUnit.getImports().stream()
					.map(ImportDeclaration::toString)
					.map(String::trim)
					.collect(Collectors.joining("\n"));

			List<CodeSymbol> symbols = new ArrayList<>();
			for (TypeDeclaration<?> type : compilationUnit.getTypes()) {
				indexType(project, codeFile, type, packageName, imports, symbols);
			}
			return symbols;
		} catch (Exception exception) {
			return List.of();
		}
	}

	private void indexType(Project project, CodeFile codeFile, TypeDeclaration<?> type, String packageName, String imports,
			List<CodeSymbol> symbols) {
		symbols.add(new CodeSymbol(
				project,
				codeFile,
				type.getNameAsString(),
				resolveType(type),
				resolveRole(type),
				packageName,
				startLine(type),
				endLine(type),
				annotations(type),
				imports
		));

		for (BodyDeclaration<?> member : type.getMembers()) {
			if (member instanceof MethodDeclaration method) {
				symbols.add(new CodeSymbol(project, codeFile, method.getNameAsString(), CodeSymbolType.METHOD, null,
						packageName, startLine(method), endLine(method), annotations(method), imports));
			}
			if (member instanceof ConstructorDeclaration constructor) {
				symbols.add(new CodeSymbol(project, codeFile, constructor.getNameAsString(), CodeSymbolType.CONSTRUCTOR, null,
						packageName, startLine(constructor), endLine(constructor), annotations(constructor), imports));
			}
			if (member instanceof FieldDeclaration field) {
				for (var variable : field.getVariables()) {
					symbols.add(new CodeSymbol(project, codeFile, variable.getNameAsString(), CodeSymbolType.FIELD, null,
							packageName, startLine(field), endLine(field), annotations(field), imports));
				}
			}
			if (member instanceof TypeDeclaration<?> nestedType) {
				indexType(project, codeFile, nestedType, packageName, imports, symbols);
			}
		}
	}

	private CodeSymbolType resolveType(TypeDeclaration<?> type) {
		if (type instanceof ClassOrInterfaceDeclaration declaration) {
			return declaration.isInterface() ? CodeSymbolType.INTERFACE : CodeSymbolType.CLASS;
		}
		if (type instanceof EnumDeclaration) {
			return CodeSymbolType.ENUM;
		}
		if (type instanceof RecordDeclaration) {
			return CodeSymbolType.RECORD;
		}
		if (type instanceof AnnotationDeclaration) {
			return CodeSymbolType.ANNOTATION;
		}
		return CodeSymbolType.CLASS;
	}

	private String resolveRole(TypeDeclaration<?> type) {
		List<String> names = type.getAnnotations().stream()
				.map(AnnotationExpr::getNameAsString)
				.toList();
		if (containsAny(names, "RestController", "Controller")) {
			return "CONTROLLER";
		}
		if (containsAny(names, "Service")) {
			return "SERVICE";
		}
		if (containsAny(names, "Repository")) {
			return "REPOSITORY";
		}
		if (containsAny(names, "Entity")) {
			return "ENTITY";
		}
		return null;
	}

	private boolean containsAny(List<String> names, String... candidates) {
		for (String candidate : candidates) {
			if (names.contains(candidate)) {
				return true;
			}
		}
		return false;
	}

	private String annotations(Node node) {
		if (node instanceof BodyDeclaration<?> bodyDeclaration) {
			return bodyDeclaration.getAnnotations().stream()
					.map(annotation -> annotation.getName().asString())
					.collect(Collectors.joining(","));
		}
		return "";
	}

	private Integer startLine(Node node) {
		return node.getRange().map(range -> range.begin.line).orElse(null);
	}

	private Integer endLine(Node node) {
		Optional<Integer> line = node.getRange().map(range -> range.end.line);
		return line.orElse(null);
	}
}
