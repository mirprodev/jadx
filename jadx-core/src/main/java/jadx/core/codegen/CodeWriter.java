package jadx.core.codegen;

import jadx.api.CodePosition;
import jadx.core.dex.attributes.LineAttrNode;
import jadx.core.utils.Utils;

import java.io.File;
import java.io.PrintWriter;
import java.util.Collections;
import java.util.HashMap;
import java.util.Iterator;
import java.util.Map;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class CodeWriter {
	private static final Logger LOG = LoggerFactory.getLogger(CodeWriter.class);
	private static final int MAX_FILENAME_LENGTH = 128;

	public static final String NL = System.getProperty("line.separator");
	public static final String INDENT = "    ";

	private static final String[] INDENT_CACHE = {
			"",
			INDENT,
			INDENT + INDENT,
			INDENT + INDENT + INDENT,
			INDENT + INDENT + INDENT + INDENT,
			INDENT + INDENT + INDENT + INDENT + INDENT,
	};

	private final StringBuilder buf = new StringBuilder();
	private String indentStr;
	private int indent;

	private int line = 1;
	private int offset = 0;
	private Map<CodePosition, Object> annotations = Collections.emptyMap();

	public CodeWriter() {
		this.indent = 0;
		this.indentStr = "";
	}

	public CodeWriter(int indent) {
		this.indent = indent;
		updateIndent();
	}

	public CodeWriter startLine() {
		addLine();
		addIndent();
		return this;
	}

	public CodeWriter startLine(char c) {
		addLine();
		addIndent();
		add(c);
		return this;
	}

	public CodeWriter startLine(String str) {
		addLine();
		addIndent();
		add(str);
		return this;
	}

	public CodeWriter startLine(int ind, String str) {
		addLine();
		addIndent();
		for (int i = 0; i < ind; i++) {
			addIndent();
		}
		add(str);
		return this;
	}

	public CodeWriter add(Object obj) {
		add(obj.toString());
		return this;
	}

	public CodeWriter add(String str) {
		buf.append(str);
		offset += str.length();
		return this;
	}

	public CodeWriter add(char c) {
		buf.append(c);
		offset++;
		return this;
	}

	CodeWriter add(CodeWriter code) {
		line--;
		for (Map.Entry<CodePosition, Object> entry : code.annotations.entrySet()) {
			CodePosition pos = entry.getKey();
			attachAnnotation(entry.getValue(), new CodePosition(line + pos.getLine(), pos.getOffset()));
		}
		line += code.line;
		offset = code.offset;
		buf.append(code);
		return this;
	}

	public CodeWriter newLine() {
		addLine();
		return this;
	}

	private void addLine() {
		buf.append(NL);
		line++;
		offset = 0;
	}

	public CodeWriter addIndent() {
		buf.append(indentStr);
		offset += indentStr.length();
		return this;
	}

	private void updateIndent() {
		int curIndent = indent;
		if (curIndent < INDENT_CACHE.length) {
			this.indentStr = INDENT_CACHE[curIndent];
		} else {
			StringBuilder s = new StringBuilder(curIndent * INDENT.length());
			for (int i = 0; i < curIndent; i++) {
				s.append(INDENT);
			}
			this.indentStr = s.toString();
		}
	}

	public int getLine() {
		return line;
	}

	public int getIndent() {
		return indent;
	}

	public void incIndent() {
		incIndent(1);
	}

	public void decIndent() {
		decIndent(1);
	}

	public void incIndent(int c) {
		this.indent += c;
		updateIndent();
	}

	public void decIndent(int c) {
		this.indent -= c;
		if (this.indent < 0) {
			LOG.warn("Indent < 0");
			this.indent = 0;
		}
		updateIndent();
	}

	private static class DefinitionWrapper {
		private final LineAttrNode node;

		private DefinitionWrapper(LineAttrNode node) {
			this.node = node;
		}

		public LineAttrNode getNode() {
			return node;
		}
	}

	public Object attachDefinition(LineAttrNode obj) {
		return attachAnnotation(new DefinitionWrapper(obj), new CodePosition(line, offset));
	}

	public Object attachAnnotation(Object obj) {
		return attachAnnotation(obj, new CodePosition(line, offset + 1));
	}

	private Object attachAnnotation(Object obj, CodePosition pos) {
		if (annotations.isEmpty()) {
			annotations = new HashMap<CodePosition, Object>();
		}
		return annotations.put(pos, obj);
	}

	public Map<CodePosition, Object> getAnnotations() {
		return annotations;
	}

	public void finish() {
		buf.trimToSize();
		Iterator<Map.Entry<CodePosition, Object>> it = annotations.entrySet().iterator();
		while (it.hasNext()) {
			Map.Entry<CodePosition, Object> entry = it.next();
			Object v = entry.getValue();
			if (v instanceof DefinitionWrapper) {
				LineAttrNode l = ((DefinitionWrapper) v).getNode();
				l.setDecompiledLine(entry.getKey().getLine());
				it.remove();
			}
		}
	}

	private static String removeFirstEmptyLine(String str) {
		if (str.startsWith(NL)) {
			return str.substring(NL.length());
		} else {
			return str;
		}
	}

	public int length() {
		return buf.length();
	}

	public boolean isEmpty() {
		return buf.length() == 0;
	}

	public boolean notEmpty() {
		return buf.length() != 0;
	}

	@Override
	public String toString() {
		return buf.toString();
	}

	public void save(File dir, String subDir, String fileName) {
		save(dir, new File(subDir, fileName).getPath());
	}

	public void save(File dir, String fileName) {
		save(new File(dir, fileName));
	}

	public void save(File file) {
		String name = file.getName();
		if (name.length() > MAX_FILENAME_LENGTH) {
			int dotIndex = name.indexOf('.');
			int cutAt = MAX_FILENAME_LENGTH - name.length() + dotIndex - 1;
			if (cutAt <= 0) {
				name = name.substring(0, MAX_FILENAME_LENGTH - 1);
			} else {
				name = name.substring(0, cutAt) + name.substring(dotIndex);
			}
			file = new File(file.getParentFile(), name);
		}

		PrintWriter out = null;
		try {
			Utils.makeDirsForFile(file);
			out = new PrintWriter(file, "UTF-8");
			String code = buf.toString();
			code = removeFirstEmptyLine(code);
			out.print(code);
		} catch (Exception e) {
			LOG.error("Save file error", e);
		} finally {
			if (out != null) {
				out.close();
			}
		}
	}

	@Override
	public int hashCode() {
		return buf.toString().hashCode();
	}

	@Override
	public boolean equals(Object o) {
		if (this == o) {
			return true;
		}
		if (!(o instanceof CodeWriter)) {
			return false;
		}
		CodeWriter that = (CodeWriter) o;
		return buf.toString().equals(that.buf.toString());
	}
}
