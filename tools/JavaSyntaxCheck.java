import com.sun.source.util.JavacTask;

import javax.tools.Diagnostic;
import javax.tools.DiagnosticCollector;
import javax.tools.JavaCompiler;
import javax.tools.JavaFileObject;
import javax.tools.StandardJavaFileManager;
import javax.tools.ToolProvider;
import java.nio.charset.StandardCharsets;
import java.util.List;

public final class JavaSyntaxCheck {
    private JavaSyntaxCheck() {}

    public static void main(String[] args) throws Exception {
        JavaCompiler compiler = ToolProvider.getSystemJavaCompiler();
        if (compiler == null) throw new IllegalStateException("The Java compiler module is unavailable.");

        DiagnosticCollector<JavaFileObject> diagnostics = new DiagnosticCollector<>();
        try (StandardJavaFileManager files = compiler.getStandardFileManager(
                diagnostics,
                null,
                StandardCharsets.UTF_8
        )) {
            Iterable<? extends JavaFileObject> inputs = files.getJavaFileObjects(args);
            JavacTask task = (JavacTask) compiler.getTask(
                    null,
                    files,
                    diagnostics,
                    List.of("-proc:none"),
                    null,
                    inputs
            );
            task.parse();
        }

        long errors = diagnostics.getDiagnostics().stream()
                .filter(item -> item.getKind() == Diagnostic.Kind.ERROR)
                .peek(item -> System.err.println(item.getSource() + ":" + item.getLineNumber() + ": " + item.getMessage(null)))
                .count();
        if (errors > 0) throw new IllegalStateException(errors + " Java syntax error(s) found.");
        System.out.println("Java syntax is valid across " + args.length + " source files.");
    }
}
