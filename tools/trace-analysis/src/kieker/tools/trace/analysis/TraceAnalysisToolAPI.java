package kieker.tools.trace.analysis;

import java.io.IOException;
import java.nio.file.Paths;
import java.nio.file.Path;

import kieker.analysis.architecture.trace.InvalidEventRecordTraceCounter;
import kieker.analysis.architecture.trace.ValidEventRecordTraceCounter;
import kieker.tools.common.TraceAnalysisParameters;

public class TraceAnalysisToolAPI {

    private final TraceAnalysisToolMain tool;

    public TraceAnalysisToolAPI() {
        this.tool = new TraceAnalysisToolMain();
    }

    public int run(final String[] args) {
        int result = this.tool.run("Trace Analysis Tool", "trace-analysis", args, new TraceAnalysisParameters());

        if (this.tool.getSettings().isPrintSystemModel()) {
            this.printSystemModel();
        }

        return result;
    }

    private void printSystemModel() {
        final Path systemModelPath;
        try {
            systemModelPath = Paths.get(tool.getSettings().getOutputDir().getCanonicalPath(), "system-entities.html");

            try {
                tool.getSystemRepository().saveSystemToHTMLFile(systemModelPath);
                if (tool.getTraceAnalysisConfiguration().getTraceReconstructionStage() != null) {
                    tool.getTraceAnalysisConfiguration().getTraceReconstructionStage().printStatusMessage();
                }
                final ValidEventRecordTraceCounter validTraceCounter = tool.getTraceAnalysisConfiguration()
                        .getValidEventRecordTraceCounter();
                final InvalidEventRecordTraceCounter invalidTraceCounter = tool.getTraceAnalysisConfiguration()
                        .getInvalidEventRecordTraceCounter();
                if ((validTraceCounter != null) && tool.getLogger().isDebugEnabled()) {
                    tool.getLogger().debug("");
                    tool.getLogger().debug("#");
                    tool.getLogger().debug("# Plugin: {}", validTraceCounter.getClass().getName());

                    final int total = validTraceCounter.getTotalCount() + invalidTraceCounter.getTotalCount();
                    tool.getLogger().debug("Trace processing summary: {} total; {} succeeded; {} failed.",
                            total, validTraceCounter.getSuccessCount(), invalidTraceCounter.getErrorCount());
                }
                if (tool.getTraceAnalysisConfiguration().getTraceEventRecords2ExecutionAndMessageTraceStage() != null) {
                    tool.getTraceAnalysisConfiguration().getTraceEventRecords2ExecutionAndMessageTraceStage()
                            .printStatusMessage();
                }
            } catch (final IOException e) {
                if (tool.getLogger().isErrorEnabled()) {
                    tool.getLogger().error("Cannot save system model in {}: {}", systemModelPath.toString(),
                            e.getLocalizedMessage());
                }
            }
        } catch (final IOException e1) {
            if (tool.getLogger().isErrorEnabled()) {
                tool.getLogger().error("Cannot compose path: {}", e1.getLocalizedMessage());
            }
        }
    }

}