package nl.utwente.fmt.rers;

import de.learnlib.api.exception.ModelCheckingException;
import de.learnlib.api.logging.LearnLogger;
import de.learnlib.api.modelchecking.counterexample.Lasso.MealyLasso;
import de.learnlib.api.modelchecking.modelchecker.ModelChecker.MealyModelCheckerLasso;
import de.learnlib.api.oracle.BlackBoxOracle.MealyBlackBoxProperty;
import de.learnlib.api.oracle.EmptinessOracle.MealyEmptinessOracle;
import de.learnlib.api.query.DefaultQuery;
import de.learnlib.filter.statistic.sul.ResetCounterSUL;
import de.learnlib.filter.statistic.sul.SymbolCounterSUL;
import net.automatalib.automata.transout.MealyMachine;
import net.automatalib.words.Word;

import javax.annotation.Nullable;
import javax.annotation.ParametersAreNonnullByDefault;
import java.util.Collection;

/**
 * Wrapper around a MealyBlackBoxProperty for several features:
 *
 *  - write a CSV line when a property is falsified,
 *  - also try to falsify a property with a fixed number of loop unrolls,
 *  - also try to falsify a property without a LassoEmptinessOracle.
 */
@ParametersAreNonnullByDefault
public class RERSProperty implements MealyBlackBoxProperty<String, String, String> {

    public static final LearnLogger LOGGER = LearnLogger.getLogger(RERSProperty.class);

    private final MealyBlackBoxProperty<String, ?, ?> property;

    private final MealyEmptinessOracle eo;

    private final MealyModelCheckerLasso<?, ?, String> mealyModelCheckerLasso;

    private int fixedFalseNegatives = 0;

    private int relativeFalseNegatives = 0;

    private final int propertyNumber;

    private final int problem;

    private final String learner;

    private final SymbolCounterSUL learnSymbolCounterSUL;
    private final SymbolCounterSUL eqSymbolCounterSUL;
    private final SymbolCounterSUL emSymbolCounterSUL;
    private final SymbolCounterSUL iSymbolCounterSUL;

    private final ResetCounterSUL learnResetCounterSUL;
    private final ResetCounterSUL eqResetCounterSUL;
    private final ResetCounterSUL emResetCounterSUL;
    private final ResetCounterSUL iResetCounterSUL;

    public RERSProperty(int problem,
                        String learner,
                        MealyBlackBoxProperty p,
                        MealyEmptinessOracle eo,
                        int propertyNumber,
                        MealyModelCheckerLasso mealyModelCheckerLasso,
                        SymbolCounterSUL learnSymbolCounterSUL,
                        SymbolCounterSUL eqSymbolCounterSUL,
                        SymbolCounterSUL emSymbolCounterSUL,
                        SymbolCounterSUL iSymbolCounterSUL,
                        ResetCounterSUL learnResetCounterSUL,
                        ResetCounterSUL eqResetCounterSUL,
                        ResetCounterSUL emResetCounterSUL,
                        ResetCounterSUL iResetCounterSUL) {
        this.problem = problem;
        this.learner = learner;
        this.property = p;
        this.eo = eo;
        this.propertyNumber = propertyNumber;
        this.mealyModelCheckerLasso = mealyModelCheckerLasso;

        this.learnSymbolCounterSUL = learnSymbolCounterSUL;
        this.eqSymbolCounterSUL = eqSymbolCounterSUL;
        this.emSymbolCounterSUL = emSymbolCounterSUL;
        this.iSymbolCounterSUL = iSymbolCounterSUL;

        this.learnResetCounterSUL = learnResetCounterSUL;
        this.eqResetCounterSUL = eqResetCounterSUL;
        this.emResetCounterSUL = emResetCounterSUL;
        this.iResetCounterSUL = iResetCounterSUL;

    }

    @Override
    public boolean isDisproved() {
        return property.isDisproved();
    }

    @Override
    public void setProperty(String s) {
        property.setProperty(s);
    }

    @Override
    public String getProperty() {
        return property.getProperty();
    }

    @Nullable
    @Override
    public DefaultQuery getCounterExample() {
        return property.getCounterExample();
    }

    /**
     * Disproves this property. Also try to disprove this property by unrolling the lasso a fixed number of times,
     * and without an LassoEmptinessOracle.
     *
     * @param hypothesis the current hypothesis.
     * @param inputs the alphabet
     * @return the query that disproves this property.
     *
     * @throws ModelCheckingException
     */
    @Nullable
    @Override
    public DefaultQuery disprove(MealyMachine hypothesis, Collection inputs) throws ModelCheckingException {

        final DefaultQuery<String, Word<String>> result = property.disprove(hypothesis, inputs);

        {
            mealyModelCheckerLasso.setMinimumUnfolds(3);
            mealyModelCheckerLasso.setMultiplier(0.0);
            final MealyLasso testLasso =
                    mealyModelCheckerLasso.findCounterExample(hypothesis, inputs, property.getProperty());
            final DefaultQuery<String, Word<String>> test;
            if (testLasso != null) test = eo.findCounterExample(testLasso, inputs);
            else test = null;

            if (test != null && result == null) {
                fixedFalseNegatives++;
                LOGGER.info(
                        String.format("possibly false: #%d, %s (%d times, fixed)", propertyNumber, property.getProperty(), fixedFalseNegatives));
                LOGGER.logQuery("query: " + test);
            }
        }

        {
            mealyModelCheckerLasso.setMinimumUnfolds(3);
            mealyModelCheckerLasso.setMultiplier(1.0);
            final MealyLasso testLasso =
                    mealyModelCheckerLasso.findCounterExample(hypothesis, inputs, property.getProperty());
            final DefaultQuery<String, Word<String>> test;
            if (testLasso != null) test = eo.findCounterExample(testLasso, inputs);
            else test = null;

            if (test != null && result == null) {
                relativeFalseNegatives++;
                LOGGER.info(
                        String.format("possibly false: #%d, %s (%d times, relative)", propertyNumber, property.getProperty(), fixedFalseNegatives));
                LOGGER.logQuery("query: " + test);
            }
        }

        // write the CSV line.
        if (result != null) {
            System.out.printf(
                    "%d,%s,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d,%d%n",
                    problem,
                    learner,
                    propertyNumber,
                    fixedFalseNegatives,
                    relativeFalseNegatives,
                    hypothesis.getStates().size(),
                    learnSymbolCounterSUL.getStatisticalData().getCount(),
                    eqSymbolCounterSUL.getStatisticalData().getCount(),
                    emSymbolCounterSUL.getStatisticalData().getCount(),
                    iSymbolCounterSUL.getStatisticalData().getCount(),
                    learnResetCounterSUL.getStatisticalData().getCount(),
                    eqResetCounterSUL.getStatisticalData().getCount(),
                    emResetCounterSUL.getStatisticalData().getCount(),
                    iResetCounterSUL.getStatisticalData().getCount());
        }

        return result;

    }

    @Nullable
    @Override
    public DefaultQuery findCounterExample(MealyMachine hypothesis, Collection inputs)
            throws ModelCheckingException {

        return property.findCounterExample(hypothesis, inputs);
    }

    @Override
    public void clearCache() {
        property.clearCache();
    }

    @Override
    public void useCache() {
        property.useCache();
    }
}
