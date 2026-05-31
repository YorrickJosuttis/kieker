/***************************************************************************
 * Copyright 2022 Kieker Project (http://kieker-monitoring.net)
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 ***************************************************************************/
package kieker.tools.trace.analysis;

import java.io.File;
import java.io.IOException;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.nio.file.Path;

import com.beust.jcommander.JCommander;

import kieker.common.configuration.Configuration;
import kieker.common.exception.ConfigurationException;
import kieker.common.util.filesystem.FSUtil;
import kieker.model.repository.SystemModelRepository;
import kieker.tools.common.AbstractService;
import kieker.tools.common.GraphicsEngineType;
import kieker.tools.common.ParameterEvaluationUtils;
import kieker.tools.common.TraceAnalysisParameters;

import py4j.GatewayServer;

/**
 * This is the main class to start the Kieker TraceAnalysisTool - the model
 * synthesis and analysis
 * tool to process the monitoring data that comes from the instrumented system,
 * or from a file that
 * contains Kieker monitoring data. The Kieker TraceAnalysisTool can produce
 * output such as
 * sequence diagrams, dependency graphs on demand. Alternatively it can be used
 * continuously for
 * online performance analysis, anomaly detection or live visualization of
 * system behavior.
 *
 * This is the trace analysis main class built upon TeeTime.
 *
 * @author Reiner Jung
 * @author Yorrick Josuttis
 * @since 1.15
 */
public class TraceAnalysisToolMain
		extends AbstractService<AbstractTraceAnalysisConfiguration, TraceAnalysisParameters> {

	private final SystemModelRepository systemRepository = new SystemModelRepository();

	private AbstractTraceAnalysisConfiguration teetimeConfiguration;

	public TraceAnalysisToolMain() {
		// Nothing to do here
	}

	/**
	 * Configure and execute the TCP Kieker data collector.
	 *
	 * @param args
	 *             arguments are ignored
	 */
	public static void main(final String[] args) {
		boolean serverMode = false;
		int port = 25333;
		String host = "0.0.0.0";

		for (int i = 0; i < args.length; i++) {
			if ("--py4j".equals(args[i])) {
				serverMode = true;
			} else if ("--port".equals(args[i])) {
				if (i + 1 < args.length) {
					try {
						port = Integer.parseInt(args[i + 1]);
						i++; // skip next arg since we consumed it
					} catch (final NumberFormatException e) {
						System.err.println("Invalid port number: " + args[i + 1]);
						System.exit(1);
					}
				} else {
					System.err.println("--port requires a port number argument");
					System.exit(1);
				}
			} else if ("--host".equals(args[i])) {
				if (i + 1 < args.length) {
					host = args[i + 1];
					i++; // skip next arg since we consumed it
				} else {
					System.err.println("--host requires a hostname/IP address argument");
					System.exit(1);
				}
			}
		}

		final TraceAnalysisToolAPI api = new TraceAnalysisToolAPI();

		if (serverMode) {
			final GatewayServer server = TraceAnalysisToolMain.createGatewayServer(api, host, port);
			server.start();
			System.out.println("TraceAnalysisTool API is ready and listening on " + host + ":" + port);
		} else {
			api.run(args);
		}

	}

	private static GatewayServer createGatewayServer(final TraceAnalysisToolAPI api, final String host,
			final int port) {
		try {
			return new GatewayServer(api, port, GatewayServer.DEFAULT_PYTHON_PORT,
					InetAddress.getByName(host), GatewayServer.defaultAddress(), 0, 0, null);
		} catch (final UnknownHostException exception) {
			throw new IllegalStateException("Cannot bind trace analysis gateway server", exception);
		}
	}

	protected AbstractTraceAnalysisConfiguration getTraceAnalysisConfiguration() {
		return this.teetimeConfiguration;
	}

	protected org.slf4j.Logger getLogger() {
		return this.logger;
	}

	protected TraceAnalysisParameters getSettings() {
		return this.settings;
	}

	protected SystemModelRepository getSystemRepository() {
		return this.systemRepository;
	}

	@Override
	protected AbstractTraceAnalysisConfiguration createTeetimeConfiguration() throws ConfigurationException {
		switch (this.settings.getGraphicsEngineType()) {
			case PLANTUML:
				this.teetimeConfiguration = TraceAnalysisConfigurationFactory
						.create(GraphicsEngineType.PLANTUML, this.settings, this.systemRepository);
				break;
			case DOTPIC:
				this.teetimeConfiguration = TraceAnalysisConfigurationFactory
						.create(GraphicsEngineType.DOTPIC, this.settings, this.systemRepository);
				break;
			default:
				throw new ConfigurationException(
						"Unknown configuration type: " + this.settings.getGraphicsEngineType());
		}
		return this.teetimeConfiguration;
	}

	@Override
	protected Path getConfigurationPath() {
		return null;
	}

	@Override
	protected boolean checkConfiguration(final Configuration configuration, final JCommander commander) {
		return true;
	}

	@Override
	protected boolean checkParameters(final JCommander commander) throws ConfigurationException {
		return this.checkInputDirs(commander)
				&& ParameterEvaluationUtils.checkDirectory(this.settings.getOutputDir(), "Output", commander);
	}

	/**
	 * Returns if the specified input directories {@link #inputDirs} exist and that
	 * each one is a monitoring log. If
	 * this is not the case for one of the directories, an error message is printed
	 * to stderr.
	 *
	 * @return true if {@link #inputDirs} exist and are Kieker directories; false
	 *         otherwise
	 */
	private boolean checkInputDirs(final JCommander commander) {
		if (this.settings.getInputDirs() == null) {
			this.logger.error("No input directories specified.");
			commander.usage();
			return false;
		}
		for (final File inputDir : this.settings.getInputDirs()) {
			try {
				if (!inputDir.exists()) {
					this.logger.error("The specified input directory '{}' does not exist", inputDir.getCanonicalPath());
					return false;
				}
				if (!inputDir.isDirectory() && !inputDir.getAbsolutePath().endsWith(FSUtil.ZIP_FILE_EXTENSION)) {
					this.logger.error("The specified input directory '{}' is neither a directory nor a zip file",
							inputDir.getCanonicalPath());
					return false;
				}
				// check whether inputDirFile contains a (kieker|tpmon).map file; the latter for
				// legacy reasons
				if (inputDir.isDirectory()) { // only check for dirs
					final File[] mapFiles = {
							new File(inputDir.getAbsolutePath() + File.separatorChar + FSUtil.MAP_FILENAME),
							new File(inputDir.getAbsolutePath() + File.separatorChar + FSUtil.LEGACY_MAP_FILENAME), };
					boolean mapFileExists = false;
					for (final File potentialMapFile : mapFiles) {
						if (potentialMapFile.isFile()) {
							mapFileExists = true;
							break;
						}
					}
					if (!mapFileExists) {
						this.logger.error("The specified input directory '{}' is not a kieker log directory",
								inputDir.getCanonicalPath());
						return false;
					}
				}
			} catch (final IOException e) { // thrown by File.getCanonicalPath()
				this.logger.error("Error resolving name of input directory: '{}'", inputDir);
			}
		}

		return true;
	}

	@Override
	protected void shutdownService() {
		// nothing special to do here
	}
}
