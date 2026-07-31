package com.everyarrow.dittoe.pipelines.behaviors.transformation;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;

import java.util.List;

/** Serializes the authoritative engine contract for non-Java consumers. */
public final class DealLanguageArtifacts {
    private static final ObjectMapper MAPPER = new ObjectMapper()
            .enable(SerializationFeature.INDENT_OUTPUT);

    private DealLanguageArtifacts() {
    }

    public static CatalogArtifact catalog() {
        DealLanguageDescriptor language = DealLanguage.descriptor();
        List<FunctionArtifact> functions = language.functions().stream()
                .map(FunctionArtifact::from)
                .toList();
        return new CatalogArtifact(
                language.version(), language.literals(), language.operators(), functions,
                language.diagnostics().stream().map(DiagnosticArtifact::from).toList(),
                DealConformanceFixtures.cases());
    }

    public static String catalogJson() {
        try {
            return MAPPER.writeValueAsString(catalog()) + System.lineSeparator();
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize the DEAL language catalog", e);
        }
    }

    public record CatalogArtifact(String version,
                                  List<String> literals,
                                  List<String> operators,
                                  List<FunctionArtifact> functions,
                                  List<DiagnosticArtifact> diagnostics,
                                  List<DealConformanceCase> conformanceCases) {
    }

    public record DiagnosticArtifact(String code, String severity, String description) {
        private static DiagnosticArtifact from(DealDiagnosticDescriptor descriptor) {
            return new DiagnosticArtifact(
                    descriptor.code().name(),
                    descriptor.severity().name(),
                    descriptor.description());
        }
    }

    public record FunctionArtifact(String name,
                                   List<String> aliases,
                                   String category,
                                   String description,
                                   String syntax,
                                   List<SignatureArtifact> signatures,
                                   String evaluationStrategy) {
        private static FunctionArtifact from(FunctionDescriptor descriptor) {
            return new FunctionArtifact(
                    descriptor.name(),
                    descriptor.aliases().stream().sorted().toList(),
                    descriptor.category().name(),
                    descriptor.description(),
                    descriptor.syntax(),
                    descriptor.signatures().stream().map(SignatureArtifact::from).toList(),
                    descriptor.evaluationStrategy().name());
        }
    }

    public record SignatureArtifact(List<String> prefix,
                                    List<String> repeating,
                                    int minimumRepetitions,
                                    int maximumRepetitions,
                                    List<String> suffix,
                                    String resultType) {
        private static SignatureArtifact from(FunctionSignature signature) {
            return new SignatureArtifact(
                    names(signature.prefix()),
                    names(signature.repeating()),
                    signature.minimumRepetitions(),
                    signature.maximumRepetitions(),
                    names(signature.suffix()),
                    signature.resultType().name());
        }

        private static List<String> names(List<FunctionValueType> types) {
            return types.stream().map(Enum::name).toList();
        }
    }
}
