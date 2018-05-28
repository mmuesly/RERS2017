package nl.utwente.fmt.rers;

import de.learnlib.api.ObservableSUL;

import java.io.FileNotFoundException;
import java.lang.reflect.InvocationTargetException;
import java.util.HashSet;
import java.util.Set;
import java.util.List;
import java.util.ArrayList;


import de.learnlib.api.logging.LearnLogger;
import lombok.Getter;
import nl.utwente.fmt.rers.problems.seq.Problem;

/**
 * A SUL implementation for RERS 2017 problems
 *
 * @author Jeroen Meijer
 */
public class ProblemSUL implements ObservableSUL<Problem, String, String> {

    public static final LearnLogger LOGGER = LearnLogger.getLogger(ProblemSUL.class);

    @Getter
    final private Problem problem;

    /**
     * The problem number
     */
    @Getter
    final int number;

    /**
     * Constructs a new ProblemSUL.
     *
     * @param number the problem number to instantiate
     *
     * @throws FileNotFoundException when the appropriate Java class can not be found.
     */
    public ProblemSUL(int number) throws FileNotFoundException {
        problem = newProblem(number);
        this.number = number;
    }

    /**
     * Returns a new Problem instance.
     *
     * @param number the problem number to instantiate
     *
     * @return the instantiated Problem.
     *
     * @throws FileNotFoundException when the appropriate Java class can not be found
     */
    public static Problem newProblem(int number) throws FileNotFoundException {
        try {
            final Class<Problem> clazz = (Class<Problem>) Class.forName("nl.utwente.fmt.rers.problems.seq.Problem" + number);
            final Problem problem = clazz.getConstructor().newInstance();
            return problem;
        } catch (ClassNotFoundException | InstantiationException | IllegalAccessException | IllegalArgumentException | InvocationTargetException | NoSuchMethodException | SecurityException ex) {
            throw new FileNotFoundException(ex.toString());
        }
    }

    /**
     * A list of inputs applied. That is cleared after {@link #post()} is called.
     */
    private List<String> inputs = new ArrayList();

    @Override
    public void pre() {
    }

    /**
     * Resets the Problem instance.
     */
    @Override
    public void post() {
        problem.reset();
        inputs.clear();
    }

    /**
     * A Set of errors triggered.
     */
    private final Set<String> errors = new HashSet();

    /**
     * Steps through a Problem, be calling {@link Problem#calculateOutput(String)}.
     *
     * Additionally, when an error is triggered the error is appended to {@link #errors}
     *
     * @param input the input to apply.
     *
     * @return the output
     */
    @Override
    public String step(String input) {
        inputs.add(input);
        try {
            problem.calculateOutput(input);
        } catch (IllegalArgumentException iae) {
        } catch (IllegalStateException ise) {
            final String error = ise.getMessage();
            if (errors.add(error)) {
                LOGGER.info("assertion triggered: " + error);
                LOGGER.info("trace: " + inputs);
            }
        }

        final String output = problem.getOutput();
        if (output == null) return "";
        else return output;
    }

    public String[] getInputs() {
        return problem.getInputs();
    }

    @Override
    public boolean canFork() {
        return true;
    }

    /**
     * Forks the the current ProblemSUL, by instantiating a new ProblemSUL with the same {@link #number}.
     *
     * @return the forked ProblemSUL.
     *
     * @throws UnsupportedOperationException when the appropriate Java class can not be found.
     */
    @Override
    public ObservableSUL<Problem, String, String> fork() throws UnsupportedOperationException {
        try {
            final ProblemSUL problemSUL = new ProblemSUL(number);
            return problemSUL;
        } catch (FileNotFoundException ex) {
            throw new UnsupportedOperationException(ex);
        }
    }

    @Override
    public boolean deepCopies() {
        return false;
    }

    /**
     * Returns the current Problem instance.
     *
     * @return the current Problem instance.
     */
    @Override
    public Problem getState() {
        return problem;
    }

    public boolean canRetrieveState() {
        return true;
    }

    @Override
    public String toString() {
        return problem.toString();
    }
}
