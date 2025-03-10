import backend.SymbolicWalker;
import midend.IstioWorker.IstioGenerator;

import java.io.IOException;


public class Main {
    public static void run(String input_path, String output_path) throws IOException {
        Controller.timer.start("CFG Model");
        IstioGenerator cfg = new IstioGenerator();
        cfg.fromYaml(input_path);
        cfg.generateCFG();
//        cfg.toDotFile(output_path + "/cfg.dot");
        Controller.timer.stop("CFG Model");

        Controller.timer.start("Symbolic Execution");
        SymbolicWalker walker = new SymbolicWalker(cfg);
        try {
            walker.generateCases(input_path, output_path);
        } catch (Exception e) {
            walker.outputNodesInPath();
            e.printStackTrace();
        }
        midend.Node.Node.resetCnt();
        Controller.timer.stop("Symbolic Execution");
    }

    public static void main(String[] args) throws IOException {
        // first argument: the path of yaml files
        String input_path = args[0];
        String output_path = args[1];
        run(input_path, output_path);
    }
}
