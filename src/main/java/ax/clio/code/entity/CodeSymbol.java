package ax.clio.code.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.Lob;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "code_symbols")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class CodeSymbol {

	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	@ManyToOne(fetch = FetchType.LAZY, optional = false)
	@JoinColumn(name = "file_id", nullable = false)
	private CodeFile file;

	@Column(nullable = false, length = 255)
	private String name;

	@Column(length = 1000)
	private String qualifiedName;

	@Column(length = 1000)
	private String signature;

	@Enumerated(EnumType.STRING)
	@Column(nullable = false, length = 30)
	private CodeSymbolType type;

	@Column(length = 80)
	private String role;

	@Column(length = 500)
	private String packageName;

	private Integer startLine;

	private Integer endLine;

	@Lob
	private String annotations;

	@Lob
	private String imports;
}
