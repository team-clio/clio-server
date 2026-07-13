package ax.clio.code.entity;

import ax.clio.project.entity.Project;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;

@Entity
@Table(name = "code_symbols")
public class CodeSymbol {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "project_id", nullable = false)
	private Project project;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "file_id", nullable = false)
	private CodeFile file;

	@Column(nullable = false, length = 255)
	private String name;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CodeSymbolType type;

	@Column(length = 80)
	private String role;

	@Column(length = 500)
	private String packageName;

	private Integer startLine;

	private Integer endLine;

	@Column(length = 10000)
	private String annotations;

	@Column(length = 10000)
	private String imports;

	protected CodeSymbol() {
	}

	public CodeSymbol(Project project, CodeFile file, String name, CodeSymbolType type, String role, String packageName,
			Integer startLine, Integer endLine, String annotations, String imports) {
		this.project = project;
		this.file = file;
		this.name = name;
		this.type = type;
		this.role = role;
		this.packageName = packageName;
		this.startLine = startLine;
		this.endLine = endLine;
		this.annotations = annotations;
		this.imports = imports;
	}

	public Long getId() {
		return id;
	}

	public CodeFile getFile() {
		return file;
	}

	public String getName() {
		return name;
	}

	public CodeSymbolType getType() {
		return type;
	}

	public String getRole() {
		return role;
	}

	public String getPackageName() {
		return packageName;
	}

	public Integer getStartLine() {
		return startLine;
	}

	public Integer getEndLine() {
		return endLine;
	}

	public String getAnnotations() {
		return annotations;
	}

	public String getImports() {
		return imports;
	}
}
