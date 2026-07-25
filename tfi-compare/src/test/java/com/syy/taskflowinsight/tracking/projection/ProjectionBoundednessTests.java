package com.syy.taskflowinsight.tracking.projection;

import com.syy.taskflowinsight.exporter.change.CanonicalChangeJsonEncoder;
import com.syy.taskflowinsight.exporter.change.CanonicalChangeMapEncoder;
import com.syy.taskflowinsight.tracking.compare.ChangeKind;
import com.syy.taskflowinsight.tracking.compare.ChangeSide;
import com.syy.taskflowinsight.tracking.compare.CompareCompletion;
import com.syy.taskflowinsight.tracking.compare.CompareDiagnostics;
import com.syy.taskflowinsight.tracking.compare.CompareOutcome;
import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.compare.FieldChange;
import com.syy.taskflowinsight.tracking.compare.ValueSnapshot;
import com.syy.taskflowinsight.tracking.path.ComparePath;
import com.syy.taskflowinsight.tracking.path.PropertySegment;
import com.syy.taskflowinsight.tracking.projection.internal.ProjectionFrame;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class ProjectionBoundednessTests {

    private final CompareProjectionFactory factory = new CompareProjectionFactory();
    private final CanonicalChangeJsonEncoder jsonEncoder = new CanonicalChangeJsonEncoder();
    private final CanonicalChangeMapEncoder mapEncoder = new CanonicalChangeMapEncoder();

    @Test
    void should_reject_projection_tree_beyond_depth_or_text_budget() {
        ProjectionNode deepRoot = deepRoot(17);
        CompareProjection deepProjection = new CompareProjection(deepRoot);

        assertThatThrownBy(() -> ProjectionFrame.validate(deepRoot, 16, 1_000_000))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("depth");
        assertThatThrownBy(() -> mapEncoder.encode(deepProjection))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("depth");
        assertThatThrownBy(() -> jsonEncoder.encode(deepProjection))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("depth");
        assertThatThrownBy(() -> ProjectionFrame.validate(
                ProjectionNode.object(List.of(ProjectionNode.member("field", ProjectionNode.string("value")))),
                16,
                3))
                .isInstanceOf(IllegalArgumentException.class)
                .hasMessageContaining("text");
    }

    @Test
    void should_encode_long_typed_path_without_recursive_schema_depth() {
        ComparePath path = pathWithSegments(100);
        FieldChange change = FieldChange.canonical(
                ChangeKind.ADD,
                Optional.empty(),
                Optional.of(new ChangeSide(path, ValueSnapshot.ofString("value", 16))));
        CompareResult result = CompareResult.canonical(
                CompareOutcome.DIFFERENT,
                CompareCompletion.COMPLETE,
                List.of(change),
                List.of(),
                List.of(),
                CompareDiagnostics.empty(),
                Optional.empty());
        CompareProjection projection = factory.create(
                result,
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults());

        assertThat(mapEncoder.encode(projection)).isNotEmpty();
        assertThat(jsonEncoder.encode(projection)).contains("property99");
    }

    @Test
    void should_flush_but_never_close_caller_outputs() throws Exception {
        CompareProjection projection = factory.create(
                CompareResult.identical(),
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults());
        CloseAwareWriter writer = new CloseAwareWriter();
        CloseAwareOutputStream output = new CloseAwareOutputStream();

        jsonEncoder.write(projection, writer);
        jsonEncoder.write(projection, output);

        assertThat(writer.content.toString()).isEqualTo(jsonEncoder.encode(projection));
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo(jsonEncoder.encode(projection));
        assertThat(writer.flushed).isTrue();
        assertThat(writer.closed).isFalse();
        assertThat(output.flushed).isTrue();
        assertThat(output.closed).isFalse();
    }

    @Test
    void should_propagate_same_writer_failure_instance() {
        CompareProjection projection = factory.create(
                CompareResult.identical(),
                ProjectionMetadata.empty(),
                MaskingPolicy.safeDefaults(),
                ProjectionOptions.defaults());
        IOException failure = new IOException("expected-write-failure");
        Writer writer = new FailingWriter(failure);

        assertThatThrownBy(() -> jsonEncoder.write(projection, writer))
                .isSameAs(failure);
    }

    private static ProjectionNode deepRoot(int nestedArrays) {
        ProjectionNode node = ProjectionNode.string("leaf");
        for (int index = 0; index < nestedArrays; index++) {
            node = ProjectionNode.array(List.of(node));
        }
        return ProjectionNode.object(List.of(ProjectionNode.member("deep", node)));
    }

    private static ComparePath pathWithSegments(int count) {
        ComparePath path = ComparePath.root();
        for (int index = 0; index < count; index++) {
            path = path.append(new PropertySegment("property" + index));
        }
        return path;
    }

    private static final class CloseAwareWriter extends Writer {

        /** 测试内收集编码字符，不与生产encoder共享状态。 */
        private final StringBuilder content = new StringBuilder();

        /** 是否收到flush调用。 */
        private boolean flushed;

        /** 是否错误收到close调用。 */
        private boolean closed;

        @Override
        public void write(char[] characters, int offset, int length) {
            content.append(characters, offset, length);
        }

        @Override
        public void flush() {
            flushed = true;
        }

        @Override
        public void close() {
            closed = true;
        }
    }

    private static final class CloseAwareOutputStream extends ByteArrayOutputStream {

        /** 是否收到flush调用。 */
        private boolean flushed;

        /** 是否错误收到close调用。 */
        private boolean closed;

        @Override
        public void flush() throws IOException {
            flushed = true;
            super.flush();
        }

        @Override
        public void close() throws IOException {
            closed = true;
            super.close();
        }
    }

    private static final class FailingWriter extends Writer {

        /** 必须原样传播的预置I/O异常。 */
        private final IOException failure;

        private FailingWriter(IOException failure) {
            this.failure = failure;
        }

        @Override
        public void write(char[] characters, int offset, int length) throws IOException {
            throw failure;
        }

        @Override
        public void flush() {
        }

        @Override
        public void close() {
        }
    }
}
