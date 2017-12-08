package nl.utwente.fmt.rers;

import de.learnlib.api.logging.LearnLogger;
import de.learnlib.api.oracle.BlackBoxOracle.BlackBoxProperty;
import de.learnlib.api.query.DefaultQuery;
import nl.utwente.fmt.rers.RERSExperiment.LEARNER;
import org.apache.commons.cli.*;

import java.io.FileNotFoundException;
import java.time.Duration;
import java.time.Instant;

public class Main {

    public static final LearnLogger LOGGER = LearnLogger.getLogger(Main.class);

    public static void main(String[] args) throws ParseException, FileNotFoundException {

        final CommandLineParser parser = new DefaultParser();
        final CommandLine line = parser.parse(getOptions(), args);

        final String[] lineArgs = line.getArgs();

        final int exit;
        if (lineArgs.length == 2) {
            if (line.hasOption('h')) printUsage();
            else {
                final int problem = Integer.parseInt(lineArgs[0]);
                final double multiplier = Double.parseDouble(line.getOptionValue('m', "1.0"));
                LOGGER.info("multiplier is: " + multiplier);

                final int minimumUnfolds = Integer.parseInt(line.getOptionValue('u', "3"));
                LOGGER.info("minimum unfolds is : " + minimumUnfolds);

                final boolean disproveFirst = line.hasOption('d');

                final boolean randomWords = !line.hasOption('r');
                final RERSExperiment rersExperiment = RERSExperiment.newExperiment(
                        problem, multiplier, minimumUnfolds, disproveFirst, LEARNER.valueOf(lineArgs[1]), randomWords);

                System.out.println(
                        "problem,learner,property,fixed,relative,size,learnsymbols,eqsymbols,emsymbols,isymbols,learnqueries,eqqueries,emqueries,iqueries");

                if (line.hasOption('l')) doOldStyleLearning(rersExperiment, Integer.parseInt(line.getOptionValue('l')));
                else {
                    rersExperiment.run();
                    LOGGER.info("final states: " + rersExperiment.getFinalHypothesis().getStates().size());
                }
            }

            exit = 0;
        } else {
            printUsage();
            exit = 1;
        }

        if (exit != 0) System.exit(exit);
    }

    static void doOldStyleLearning(RERSExperiment e, int seconds) {
        final Instant start = Instant.now();

        e.getLearningAlgorithm().startLearning();
        System.out.println("wtf: " + e.getLearningAlgorithm().getHypothesisModel().getStates().size());

        DefaultQuery ce;

        do {
            LOGGER.logPhase("Searching for counterexample");
            long eqSymbols = e.getEqSymbolCounterSUL().getStatisticalData().getCount();
            long eqResets = e.getEqResetCounterSUL().getStatisticalData().getCount();
            ce = e.getEquivalenceAlgorithm().findCounterExample(e.getLearningAlgorithm().getHypothesisModel(), e.getInputs());
            if (ce != null) LOGGER.logCounterexample(ce.toString());

            final Instant end = Instant.now();
            final Duration timeElapsed = Duration.between(start, end);
            if (timeElapsed.getSeconds() > seconds) {
                LOGGER.info("timeout reached, not using counter example");
                ce = null;
            }

            if (ce == null) {
                eqSymbols = e.getEqSymbolCounterSUL().getStatisticalData().getCount() - eqSymbols;
                eqResets = e.getEqResetCounterSUL().getStatisticalData().getCount() - eqResets;

                LOGGER.info("Useless equivalence symbols: " + eqSymbols);
                LOGGER.info("Useless equivalence queries: " + eqResets);
                e.getEqSymbolCounterSUL().getStatisticalData().increment(-eqSymbols);
                e.getEqResetCounterSUL().getStatisticalData().increment(-eqResets);
            }
        } while (ce != null && e.getLearningAlgorithm().refineHypothesis(ce));

        for (BlackBoxProperty p : e.getBlackBoxOracle().getProperties()) {
            p.disprove(e.getLearningAlgorithm().getHypothesisModel(), e.getInputs());
        }
    }

    static void printUsage() {
        final HelpFormatter formatter = new HelpFormatter();
        formatter.printHelp("java " + Main.class.getCanonicalName() + " [problem number] [learner]", getOptions());
    }

    static Options getOptions() {
        final Options options = new Options();

        options.addOption("l", "learn-first", true, "learn first within a timeout in seconds");
        options.addOption("m", "multiplier", true, "multiplier for unrolls");
        options.addOption("u", "minimum-unfolds", true, "minimum number of unfolds");
        options.addOption("d", "disprove-first", false, "use disprove first black-box oracle");
        options.addOption("r", "no-random-words", false, "do not use an additional random words equivalence oracle");
        options.addOption("h", "help", false, "prints help");

        return options;
    }
}
