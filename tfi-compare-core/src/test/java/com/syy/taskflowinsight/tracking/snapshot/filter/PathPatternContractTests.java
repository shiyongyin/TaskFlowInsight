package com.syy.taskflowinsight.tracking.snapshot.filter;

import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.IndexSegment;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.util.Locale;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 构造期typed path grammar合同，禁止重新引入regex或display path解析。
 */
class PathPatternContractTests {

    @Test
    void kernelPatternMatchesTypedSegmentsCaseSensitively() {
        PathPattern pattern = PathPatternCompiler.compileCaseSensitive(
                "PROPERTY:order/PROPERTY:item*/INDEX:*/PROPERTY:id",
                100,
                128,
                16_384);
        ComparePath matching = ComparePath.root()
                .append(new PropertySegment("order"))
                .append(new PropertySegment("items"))
                .append(new IndexSegment(0))
                .append(new PropertySegment("id"));
        ComparePath wrongCase = ComparePath.root()
                .append(new PropertySegment("Order"))
                .append(new PropertySegment("items"))
                .append(new IndexSegment(0))
                .append(new PropertySegment("id"));

        assertThat(pattern.matches(matching)).isTrue();
        assertThat(pattern.matches(wrongCase)).isFalse();
        assertThat(pattern.matches(matching.append(new PropertySegment("extra")))).isFalse();
    }

    @ParameterizedTest
    @ValueSource(strings = {
            "PROPERTY:**",
            "PROPERTY:na?e",
            "PROPERTY:[name]",
            "PROPERTY:na\\me",
            "PROPERTY:pre*post",
            "MAP_KEY:raw-key",
            "UNKNOWN:*",
            "PROPERTY:order//PROPERTY:id"
    })
    void invalidGrammarFailsInsteadOfFallingBackToLiteral(String source) {
        assertThatThrownBy(() -> PathPatternCompiler.compileCaseSensitive(
                source, 100, 128, 16_384))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessage("invalid typed path pattern");
    }

    @Test
    void compilerEnforcesSegmentTokenAndTotalLimits() {
        String source = "PROPERTY:order/PROPERTY:id";

        assertThat(PathPatternCompiler.compileCaseSensitive(
                source, 2, 5, source.length())).isNotNull();
        assertThatThrownBy(() -> PathPatternCompiler.compileCaseSensitive(
                source, 1, 5, source.length())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PathPatternCompiler.compileCaseSensitive(
                source, 2, 4, source.length())).isInstanceOf(IllegalArgumentException.class);
        assertThatThrownBy(() -> PathPatternCompiler.compileCaseSensitive(
                source, 2, 5, source.length() - 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void insensitiveCompilerUsesLocaleRootInsteadOfJvmDefault() {
        Locale original = Locale.getDefault();
        try {
            Locale.setDefault(Locale.forLanguageTag("tr-TR"));
            PathPattern pattern = PathPatternCompiler.compileCaseInsensitive(
                    "PROPERTY:IDENTIFIER", 100, 128, 16_384);
            ComparePath path = ComparePath.root().append(new PropertySegment("identifier"));

            assertThat(pattern.matches(path)).isTrue();
        } finally {
            Locale.setDefault(original);
        }
    }
}
