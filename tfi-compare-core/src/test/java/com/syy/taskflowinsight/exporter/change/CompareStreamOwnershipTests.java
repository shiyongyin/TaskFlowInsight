package com.syy.taskflowinsight.exporter.change;

import com.syy.taskflowinsight.tracking.compare.CompareResult;
import com.syy.taskflowinsight.tracking.projection.CompareProjection;
import com.syy.taskflowinsight.tracking.projection.CompareProjectionFactory;
import com.syy.taskflowinsight.tracking.projection.MaskingPolicy;
import com.syy.taskflowinsight.tracking.projection.ProjectionMetadata;
import com.syy.taskflowinsight.tracking.projection.ProjectionOptions;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.Writer;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * JSON三个入口共享内容与调用方资源所有权的合同。
 */
class CompareStreamOwnershipTests {

    private final ChangeJsonExporter exporter = new ChangeJsonExporter();
    private final CompareProjection projection = new CompareProjectionFactory().create(
            CompareResult.identical(),
            ProjectionMetadata.empty(),
            MaskingPolicy.safeDefaults(),
            ProjectionOptions.defaults());

    @Test
    void stringWriterAndOutputStreamProduceSameCompactJson() throws Exception {
        CloseAwareWriter writer = new CloseAwareWriter();
        CloseAwareOutputStream output = new CloseAwareOutputStream();

        exporter.write(projection, writer);
        exporter.write(projection, output);

        String expected = exporter.format(projection);
        assertThat(writer.content.toString()).isEqualTo(expected);
        assertThat(output.toString(StandardCharsets.UTF_8)).isEqualTo(expected);
        assertThat(expected).doesNotEndWith("\n");
        assertThat(writer.flushed).isTrue();
        assertThat(writer.closed).isFalse();
        assertThat(output.flushed).isTrue();
        assertThat(output.closed).isFalse();
    }

    @Test
    void writerFailurePropagatesWithoutWrapping() {
        IOException failure = new IOException("expected-write-failure");

        assertThatThrownBy(() -> exporter.write(projection, new FailingWriter(failure)))
                .isSameAs(failure);
    }

    private static final class CloseAwareWriter extends Writer {

        /** 测试收集字符，不与生产encoder共享可变状态。 */
        private final StringBuilder content = new StringBuilder();

        /** 成功路径必须显式flush。 */
        private boolean flushed;

        /** exporter不得取得调用方资源的close所有权。 */
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

        /** 成功路径必须显式flush。 */
        private boolean flushed;

        /** exporter不得取得调用方资源的close所有权。 */
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
