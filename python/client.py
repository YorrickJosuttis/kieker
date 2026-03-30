from py4j.java_gateway import JavaGateway, GatewayParameters


class TraceAnalysisToolClient:
    def __init__(self, port=25333):
        self.gateway = JavaGateway(
            gateway_parameters=GatewayParameters(port=port))
        self.api = self.gateway.entry_point

    def run(self, args):
        java_args = self.gateway.new_array(
            self.gateway.jvm.java.lang.String, len(args))
        for idx, value in enumerate(args):
            java_args[idx] = value
        return self.api.run(java_args)


if __name__ == "__main__":
    client = TraceAnalysisToolClient()
    result = client.run(["--help"])
    """ result = client.run(["--inputdirs", "C:/w/kieker/python/kieker-logs/kieker-20250426-154511-7665070184524-UTC--KIEKER",
                        "--outputdir", "C:/w/kieker/python/output",
                         "--plot-Call-Trees",
                         "--graphics-engine", "PLANTUML",
                         "--png", "--svg", "--pdf"]) """
    print(f"Result: {result}")
