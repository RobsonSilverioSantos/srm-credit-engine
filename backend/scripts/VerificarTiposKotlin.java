import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.ArrayList;
import java.util.List;
import org.jetbrains.kotlin.cli.jvm.compiler.EnvironmentConfigFiles;
import org.jetbrains.kotlin.cli.jvm.compiler.KotlinCoreEnvironment;
import org.jetbrains.kotlin.com.intellij.openapi.Disposable;
import org.jetbrains.kotlin.com.intellij.openapi.util.Disposer;
import org.jetbrains.kotlin.com.intellij.psi.PsiErrorElement;
import org.jetbrains.kotlin.com.intellij.psi.util.PsiTreeUtil;
import org.jetbrains.kotlin.config.CompilerConfiguration;
import org.jetbrains.kotlin.psi.KtClassOrObject;
import org.jetbrains.kotlin.psi.KtDeclaration;
import org.jetbrains.kotlin.psi.KtFile;
import org.jetbrains.kotlin.psi.KtNamedDeclaration;
import org.jetbrains.kotlin.psi.KtPsiFactory;
import org.jetbrains.kotlin.psi.KtTypeAlias;

public final class VerificarTiposKotlin {
    private static List<String> verificar(KtPsiFactory factory, String nome, String codigo) {
        KtFile arquivo = factory.createFile(nome, codigo);
        List<String> erros = new ArrayList<>();
        if (PsiTreeUtil.findChildOfType(arquivo, PsiErrorElement.class) != null) {
            erros.add(nome + ": sintaxe Kotlin inválida");
            return erros;
        }
        List<String> tipos = new ArrayList<>();
        for (KtDeclaration declaracao : arquivo.getDeclarations()) {
            if (declaracao instanceof KtClassOrObject || declaracao instanceof KtTypeAlias) {
                String nomeTipo = ((KtNamedDeclaration) declaracao).getName();
                if (nomeTipo == null) {
                    erros.add(nome + ": declaração de tipo sem nome");
                } else {
                    tipos.add(nomeTipo);
                }
            }
        }
        if (tipos.size() > 1 || (tipos.size() == 1 && !nome.equals(tipos.get(0) + ".kt"))) {
            erros.add(nome + ": tipos top-level " + tipos);
        }
        return erros;
    }

    private static void testar(KtPsiFactory factory) {
        if (!verificar(factory, "A.kt", "class A { class B; companion object {} }\n").isEmpty()
                || !verificar(factory, "Funcoes.kt", "fun f() = \"class Falso\"\n").isEmpty()
                || !verificar(factory, "A.kt", "class A\n/* class Falso */\ntypealias B = String\n").contains(
                        "A.kt: tipos top-level [A, B]")
                || verificar(factory, "Outro.kt", "fun interface A { fun f() }\n").isEmpty()
                || verificar(factory, "A.kt", "enum class A { UM }\nobject B\n").isEmpty()) {
            throw new IllegalStateException("Autoteste da verificação Kotlin falhou");
        }
    }

    public static void main(String[] args) throws IOException {
        Disposable disposable = Disposer.newDisposable();
        try {
            KotlinCoreEnvironment ambiente = KotlinCoreEnvironment.createForProduction(
                    disposable, new CompilerConfiguration(), EnvironmentConfigFiles.JVM_CONFIG_FILES);
            KtPsiFactory factory = new KtPsiFactory(ambiente.getProject(), false);
            testar(factory);
            List<String> erros = new ArrayList<>();
            for (String argumento : args) {
                Path caminho = Path.of(argumento);
                if (!Files.isDirectory(caminho)) {
                    throw new IllegalArgumentException("Diretório ausente: " + caminho);
                }
                try (var arquivos = Files.walk(caminho)) {
                    for (Path arquivo : arquivos.filter(p -> Files.isRegularFile(p) && p.toString().endsWith(".kt")).toList()) {
                        for (String erro : verificar(factory, arquivo.getFileName().toString(), Files.readString(arquivo))) {
                            erros.add(arquivo + ": " + erro);
                        }
                    }
                }
            }
            if (!erros.isEmpty()) {
                throw new IllegalStateException(String.join("\n", erros));
            }
        } finally {
            Disposer.dispose(disposable);
        }
    }
}
