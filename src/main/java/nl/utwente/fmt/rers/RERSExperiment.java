package nl.utwente.fmt.rers;

import com.google.common.base.Suppliers;
import de.learnlib.algorithms.adt.learner.ADTLearnerBuilder;
import de.learnlib.algorithms.dhc.mealy.MealyDHC;
import de.learnlib.algorithms.discriminationtree.mealy.DTLearnerMealyBuilder;
import de.learnlib.algorithms.kv.mealy.KearnsVaziraniMealyBuilder;
import de.learnlib.algorithms.lstargeneric.mealy.ExtensibleLStarMealyBuilder;
import de.learnlib.algorithms.malerpnueli.MalerPnueliMealyBuilder;
import de.learnlib.algorithms.rivestschapire.RivestSchapireMealyBuilder;
import de.learnlib.algorithms.ttt.mealy.TTTLearnerMealyBuilder;
import de.learnlib.api.SUL;
import de.learnlib.api.algorithm.LearningAlgorithm.MealyLearner;
import de.learnlib.api.logging.LearnLogger;
import de.learnlib.api.logging.LoggingBlackBoxProperty.MealyLoggingBlackBoxProperty;
import de.learnlib.api.modelchecking.modelchecker.ModelChecker.MealyModelCheckerLasso;
import de.learnlib.api.oracle.BlackBoxOracle.MealyBlackBoxOracle;
import de.learnlib.api.oracle.BlackBoxOracle.MealyBlackBoxProperty;
import de.learnlib.api.oracle.EmptinessOracle.MealyEmptinessOracle;
import de.learnlib.api.oracle.EmptinessOracle.MealyLassoEmptinessOracle;
import de.learnlib.api.oracle.EquivalenceOracle.MealyEquivalenceOracle;
import de.learnlib.api.oracle.InclusionOracle.MealyInclusionOracle;
import de.learnlib.api.oracle.MembershipOracle.MealyMembershipOracle;
import de.learnlib.api.oracle.SymbolQueryOracle;
import de.learnlib.filter.statistic.sul.ResetCounterSUL;
import de.learnlib.filter.statistic.sul.SymbolCounterSUL;
import de.learnlib.modelchecking.modelchecker.LTSminLTLAlternatingBuilder;
import de.learnlib.oracle.blackbox.CExFirstBBOracle.CExFirstMealyBBOracle;
import de.learnlib.oracle.blackbox.DisproveFirstBBOracle.DisproveFirstMealyBBOracle;
import de.learnlib.oracle.blackbox.ModelCheckingBBProperty.MealyBBPropertyMealyLasso;
import de.learnlib.oracle.emptiness.BreadthFirstEmptinessOracle.MealyBreadthFirstEmptinessOracle;
import de.learnlib.oracle.emptiness.LassoAutomatonEmptinessOracle.MealyLassoMealyEmptinessOracle;
import de.learnlib.oracle.equivalence.EQOracleChain.MealyEQOracleChain;
import de.learnlib.oracle.equivalence.RandomWordsEQOracle.MealyRandomWordsEQOracle;
import de.learnlib.oracle.equivalence.WpMethodEQOracle.MealyWpMethodEQOracle;
import de.learnlib.oracle.inclusion.BreadthFirstInclusionOracle.MealyBreadthFirstInclusionOracle;
import de.learnlib.oracle.membership.SULOmegaOracle;
import de.learnlib.oracle.membership.SULOracle;
import de.learnlib.oracle.membership.SULSymbolQueryOracle;
import de.learnlib.oracle.parallelism.ParallelOracle.PoolPolicy;
import de.learnlib.oracle.parallelism.StaticParallelOracleBuilder;
import de.learnlib.util.BBCExperiment.MealyBBCExperiment;
import lombok.Getter;
import net.automatalib.words.Alphabet;
import net.automatalib.words.impl.Alphabets;
import nl.utwente.fmt.rers.problems.seq.Problem;

import java.io.FileNotFoundException;
import java.io.InputStream;
import java.util.*;
import java.util.function.Function;

/**
 * A specialization of MealyBBCExperiment. That parses LTL formulae, and instantiates the proper classes
 * (such as ModelChecker).
 */
public class RERSExperiment extends MealyBBCExperiment<String, String> {

    enum LEARNER {
        ADT,
        DHC,
        DiscriminationTree,
        KearnsVazirani,
        ExtensibleLStar,
        MalerPnueli,
        RivestSchapire,
        TTT
    }

    public static final LearnLogger LOGGER = LearnLogger.getLogger(RERSExperiment.class);

    @Getter
    private final SymbolCounterSUL eqSymbolCounterSUL;

    @Getter
    private final ResetCounterSUL eqResetCounterSUL;

    private RERSExperiment(MealyLearner learningAlgorithm,
                           MealyEquivalenceOracle equivalenceAlgorithm,
                           Alphabet inputs,
                           MealyBlackBoxOracle<String, String> blackBoxOracle,
                           SymbolCounterSUL eqSymbolCounterSUL,
                           ResetCounterSUL eqResetCounterSUL) {
        super(learningAlgorithm, equivalenceAlgorithm, inputs, blackBoxOracle, true);
        this.eqSymbolCounterSUL = eqSymbolCounterSUL;
        this.eqResetCounterSUL = eqResetCounterSUL;
    }

    /**
     * Returns a new RERSExperiment.
     *
     * @param number the {@link Problem} number to instantiate.
     * @param multiplier the multiplier used when computing the number of unrolls for a lasso.
     * @param minimumUnfolds the minimum number of times a lasso needs to be unrolled.
     * @param disproveFirst whether to use a {@link DisproveFirstMealyBBOracle},
     *                      instead of a {@link CExFirstMealyBBOracle}.
     * @param learner the learner to instantiate.
     * @param randomWords whether to use an additional {@link de.learnlib.oracle.equivalence.RandomWordsEQOracle}.
     *
     * @return the RERSExperiment
     *
     * @throws FileNotFoundException when the appropriate Java class can not be found.
     */
    public static RERSExperiment newExperiment(int number,
                                               double multiplier,
                                               int minimumUnfolds,
                                               boolean disproveFirst,
                                               LEARNER learner,
                                               boolean randomWords) throws FileNotFoundException {
        final ProblemSUL problemSUL = new ProblemSUL(number);

        final SymbolCounterSUL learnSymbolCounterSUL = new SymbolCounterSUL("learner", problemSUL);
        final ResetCounterSUL learnResetCounterSUL = new ResetCounterSUL("learner", learnSymbolCounterSUL);
        final SUL learnSUL = learnResetCounterSUL;
        final SULOracle learnOracle = new SULOracle(learnSUL);
        final SymbolQueryOracle learnSymbolQueryOracle = new SULSymbolQueryOracle(learnSUL);

        final SymbolCounterSUL eqSymbolCounterSUL = new SymbolCounterSUL("equivalence", problemSUL);
        final ResetCounterSUL eqResetCounterSUL = new ResetCounterSUL("equivalence", eqSymbolCounterSUL);
        final SUL eqSUL = eqResetCounterSUL;
        final SULOracle eqOracle = new SULOracle(eqSUL);

        final SymbolCounterSUL emSymbolCounterSUL = new SymbolCounterSUL("emptiness", problemSUL);
        final ResetCounterSUL emResetCounterSUL = new ResetCounterSUL("emptiness", emSymbolCounterSUL);
        final SUL emSUL = emResetCounterSUL;

        final SymbolCounterSUL iSymbolCounterSUL = new SymbolCounterSUL("inclusion", problemSUL);
        final ResetCounterSUL iResetCounterSUL = new ResetCounterSUL("inclusion", iSymbolCounterSUL);
        final SUL iSUL = iResetCounterSUL;
        final SULOracle iOracle = new SULOracle(iSUL);

        final Alphabet alphabet = Alphabets.fromArray(problemSUL.getInputs());

        final MealyMembershipOracle membershipOracle =
                new StaticParallelOracleBuilder(Suppliers.ofInstance(eqOracle)).
                                    withDefaultNumInstances().
                                    withMinBatchSize(50000).
                                    withPoolPolicy(PoolPolicy.FIXED).createMealy();

        MealyEquivalenceOracle equivalenceOracle = new MealyWpMethodEQOracle(3, membershipOracle);
        if (randomWords) {
            equivalenceOracle = new MealyEQOracleChain(
                    equivalenceOracle,
                    new MealyRandomWordsEQOracle(
                            membershipOracle,
                            number * 5,
                            number * 50, 1000 * 1000 * 100,
                            new Random(123456l)));
        }

        final MealyLearner mealyLearner;

        switch (learner) {
            case ADT:
                mealyLearner = new ADTLearnerBuilder().withAlphabet(alphabet).withOracle(learnSymbolQueryOracle).create();
                break;
            case DHC:
                mealyLearner = new MealyDHC(alphabet, learnOracle);
                break;
            case DiscriminationTree:
                mealyLearner = new DTLearnerMealyBuilder().withAlphabet(alphabet).withOracle(learnOracle).create();
                break;
            case KearnsVazirani:
                mealyLearner = new KearnsVaziraniMealyBuilder().withAlphabet(alphabet).withOracle(learnOracle).create();
                break;
            case ExtensibleLStar:
                mealyLearner = new ExtensibleLStarMealyBuilder().withAlphabet(alphabet).withOracle(learnOracle).create();
                break;
            case MalerPnueli:
                mealyLearner = new MalerPnueliMealyBuilder().withAlphabet(alphabet).withOracle(learnOracle).create();
                break;
            case RivestSchapire:
                mealyLearner = new RivestSchapireMealyBuilder().withAlphabet(alphabet).withOracle(learnOracle).create();
                break;
            case TTT:
                mealyLearner = new TTTLearnerMealyBuilder().withAlphabet(alphabet).withOracle(learnOracle).create();
                break;
            default:
                mealyLearner = null;
                break;
        }

        final Function<String, String> edgeParser = s -> s;

        final MealyModelCheckerLasso modelChecker = new LTSminLTLAlternatingBuilder().
                withString2Input(edgeParser).withString2Output(edgeParser).withSkipOutputs(Collections.singleton("")).
                withMinimumUnfolds(minimumUnfolds).withMultiplier(multiplier).
                //withInheritIO(true).withKeepFiles(true).
                create();

        final MealyEmptinessOracle emptinessOracle = new MealyBreadthFirstEmptinessOracle(1, new SULOracle(problemSUL));

        final MealyLassoEmptinessOracle lassoEmptinessOracle =
                new MealyLassoMealyEmptinessOracle(SULOmegaOracle.newOracle(emSUL));

        final MealyInclusionOracle inclusionOracle = new MealyBreadthFirstInclusionOracle(1, iOracle);

        final List<String> formulae = parseLTL(number);
        final Set<MealyBlackBoxProperty> properties = new HashSet();
        for (int i = 0; i < formulae.size(); i++) {
            final String formula = formulae.get(i);
            final MealyBlackBoxProperty p = new RERSProperty(
                    number,
                    learner.toString(),
                    new MealyLoggingBlackBoxProperty(
                        new MealyBBPropertyMealyLasso(modelChecker, lassoEmptinessOracle, inclusionOracle, formula)),
                    emptinessOracle,
                    i,
                    modelChecker,
                    learnSymbolCounterSUL,
                    eqSymbolCounterSUL,
                    emSymbolCounterSUL,
                    iSymbolCounterSUL,
                    learnResetCounterSUL,
                    eqResetCounterSUL,
                    emResetCounterSUL,
                    iResetCounterSUL);
            properties.add(p);
        }

        final MealyBlackBoxOracle blackBoxOracle;
        if (disproveFirst) blackBoxOracle = new DisproveFirstMealyBBOracle(properties);
        else blackBoxOracle = new CExFirstMealyBBOracle(properties);

        return new RERSExperiment(
                mealyLearner, equivalenceOracle, alphabet, blackBoxOracle, eqSymbolCounterSUL, eqResetCounterSUL);
    }

    /**
     * Constructs a List of LTL formulae in LTSmin format, for a given {@link Problem} number.
     *
     * @param number the Problem number.
     *
     * @return the List of LTL formulae.
     *
     * @throws FileNotFoundException when the appropriate file containing LTL formulae can not be found.
     */
    static List<String> parseLTL(int number) throws FileNotFoundException {

        final List<String> result = new ArrayList();
        final InputStream is = RERSExperiment.class.getClass().getResourceAsStream(
                String.format("/constraints-Problem%d.txt", number));

        final Scanner fileScanner = new Scanner(is);

        while(fileScanner.hasNextLine()) {

            String line = fileScanner.nextLine();
            line = line.replace("(", "( ");
            line = line.replace(")", " )");

            if (!line.isEmpty()) {

                if (line.charAt(0) != '#') {
                    final Scanner lineScanner = new Scanner(line);
                    lineScanner.useDelimiter(" ");

                    final StringBuilder sb = new StringBuilder();
                    while (lineScanner.hasNext()) {
                        final String token = lineScanner.next();


                        if (token.equals("true")) sb.append("true");
                        else if (token.equals("false")) sb.append("false");
                        else if (token.equals("(")) sb.append("(");
                        else if (token.equals(")")) sb.append(")");
                        else if (token.equals("!")) sb.append("!");
                        else if (token.equals("R")) sb.append(" R ");
                        else if (token.equals("U")) sb.append(" U ");
                        else if (token.equals("X")) sb.append("X ");
                        else if (token.equals("WU")) sb.append(" W ");
                        else if (token.equals("&")) sb.append(" && ");
                        else if (token.equals("|")) sb.append(" || ");
                        else if (token.matches("i[A-Z]")) { sb.append("("); sb.append("letter"); sb.append(" == \""); sb.append(token.charAt(1)); sb.append("\")"); }
                        else if (token.matches("o[A-Z]")) { sb.append("("); sb.append("letter"); sb.append(" == \""); sb.append(token.charAt(1)); sb.append("\")"); }
                        else throw new RuntimeException("I do not know what to do with token: " + token);
                    }

                    final String formula = sb.toString();
                    result.add(formula);

                    LOGGER.info(String.format("Parsed formula #%d: %s", result.size(), formula));
                }
            }
        }

        fileScanner.close();

        return result;
    }
}

