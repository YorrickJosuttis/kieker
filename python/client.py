import logging
import os

from py4j.java_gateway import JavaGateway, GatewayParameters
from py4j.protocol import Py4JJavaError


class TraceAnalysisToolClient:
    global input_dir, output_dir

    def __init__(self, host="localhost", port=25333):
        self.gateway = JavaGateway(
            gateway_parameters=GatewayParameters(address=host, port=port))
        self.api = self.gateway.entry_point  # TraceAnalysisToolAPI instance from Java

    def set_input_dir(self, input_dir):
        self.input_dir = input_dir

    def set_output_dir(self, output_dir):
        self.output_dir = output_dir

    def create_output_dir(self, output_dir):
        if not os.path.exists(output_dir):
            os.makedirs(output_dir)
            print(f"Created output directory: {output_dir}")
        else:
            print(f"Output directory already exists: {output_dir}")

    def shutdown_gateway(self):
        try:
            logging.getLogger("py4j").setLevel(logging.CRITICAL)
            self.gateway.shutdown()
        except Exception as e:
            logging.error(f"Error shutting down gateway: {e}")
        finally:
            self.gateway.close()

    def run(self, args):
        java_args = self.gateway.new_array(
            self.gateway.jvm.java.lang.String, len(args))
        for idx, value in enumerate(args):
            java_args[idx] = value
        return self.api.run(java_args)

    def run_safely(self, args, plot_type):
        try:
            self.run(args)
            return True
        except Py4JJavaError as error:
            logging.error("Visualization '%s' failed: %s",
                          plot_type, error.java_exception)
            return False
        except Exception as error:
            logging.error(
                "Visualization '%s' failed on Python side: %s", plot_type, error)
            return False

    def run_visualization(self, plot_type, milliseconds=None):
        args = ["--inputdirs", self.input_dir,
                "--outputdir", self.output_dir,
                f"--plot-{plot_type}"]
        if milliseconds is not None:
            args.append(milliseconds)
        args += ["--graphics-engine", "PLANTUML", "--png", "--svg", "--pdf"]
        return self.run_safely(args, plot_type)

    def assembly_sequence_diagrams(self):
        self.run_visualization("Assembly-Sequence-Diagrams")

    def deployment_sequence_diagrams(self):
        self.run_visualization("Deployment-Sequence-Diagrams")

    def deployment_component_dependency_graph(self):
        self.run_visualization(
            "Deployment-Component-Dependency-Graph", "none")

    def assembly_component_dependency_graph(self):
        self.run_visualization(
            "Assembly-Component-Dependency-Graph", "none")

    def container_dependency_graph(self):
        self.run_visualization("Container-Dependency-Graph")

    def deployment_operation_dependency_graph(self):
        self.run_visualization(
            "Deployment-Operation-Dependency-Graph", "none")

    def assembly_operation_dependency_graph(self):
        self.run_visualization(
            "Assembly-Operation-Dependency-Graph", "none")

    def call_trees(self):
        self.run_visualization("Call-Trees")

    def aggregated_deployment_call_tree(self):
        self.run_visualization("Aggregated-Deployment-Call-Tree")

    def aggregated_assembly_call_tree(self):
        self.run_visualization("Aggregated-Assembly-Call-Tree")

    def help(self):
        self.run(["--help"])


if __name__ == "__main__":
    client = TraceAnalysisToolClient(port=25333)
    client.set_input_dir(
        r"./kieker-logs/kieker-20250426-154511-7665070184524-UTC--KIEKER")

    visualizations = [
        ("assembly-sequence-diagrams", client.assembly_sequence_diagrams),
        ("deployment-sequence-diagrams", client.deployment_sequence_diagrams),
        ("deployment-component-dependency-graph",
         client.deployment_component_dependency_graph),
        ("assembly-component-dependency-graph",
         client.assembly_component_dependency_graph),
        ("container-dependency-graph", client.container_dependency_graph),
        ("deployment-operation-dependency-graph",
         client.deployment_operation_dependency_graph),
        ("assembly-operation-dependency-graph",
         client.assembly_operation_dependency_graph),
        ("call-trees", client.call_trees),
        ("aggregated-deployment-call-tree",
         client.aggregated_deployment_call_tree),
        ("aggregated-assembly-call-tree",
         client.aggregated_assembly_call_tree)
    ]

    for plot_type, visualization_func in visualizations:
        subdir = plot_type
        client.set_output_dir(os.path.join(r"./python-output", subdir))
        client.create_output_dir(os.path.join(
            r"C:\Users\Anwender\Documents\Uni\SustainableKieker\Test\python-output", subdir))
        visualization_func()

    client.shutdown_gateway()
