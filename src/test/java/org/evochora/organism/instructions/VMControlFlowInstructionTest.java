package org.evochora.organism.instructions;

import org.evochora.runtime.Config;
import org.evochora.runtime.Simulation;
import org.evochora.runtime.isa.Instruction;
import org.evochora.runtime.model.Environment;
import org.evochora.runtime.model.Molecule;
import org.evochora.runtime.model.Organism;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

public class VMControlFlowInstructionTest {

    private Environment environment;
    private Organism org;
    private Simulation sim;
    private final int[] startPos = new int[]{5};

    @BeforeAll
    static void init() {
        Instruction.init();
    }

    @BeforeEach
    void setUp() {
        environment = new Environment(new int[]{100}, true);
        sim = new Simulation(environment);
        org = Organism.create(sim, startPos, 1000, sim.getLogger());
        sim.addOrganism(org);
    }

    private void placeInstructionWithVector(String name, int[] vector) {
        int opcode = Instruction.getInstructionIdByName(name);
        environment.setMolecule(new Molecule(Config.TYPE_CODE, opcode), org.getIp());
        int[] currentPos = org.getIp();
        for (int val : vector) {
            currentPos = org.getNextInstructionPosition(currentPos, environment, org.getDv());
            environment.setMolecule(new Molecule(Config.TYPE_DATA, val), currentPos);
        }
    }

    private void placeInstruction(String name, Integer... args) {
        int opcode = Instruction.getInstructionIdByName(name);
        environment.setMolecule(new Molecule(Config.TYPE_CODE, opcode), org.getIp());
        int[] currentPos = org.getIp();
        for (int arg : args) {
            currentPos = org.getNextInstructionPosition(currentPos, environment, org.getDv());
            environment.setMolecule(new Molecule(Config.TYPE_DATA, arg), currentPos);
        }
    }

    @Test
    void testJmpi() {
        int[] jumpDelta = new int[]{10};
        int[] expectedIp = org.getTargetCoordinate(org.getIp(), jumpDelta, environment);
        placeInstructionWithVector("JMPI", jumpDelta);

        sim.tick();

        assertThat(org.getIp()).isEqualTo(expectedIp);
    }

    @Test
    void testCall() {
        int[] jumpDelta = new int[]{7};
        int[] expectedIp = org.getTargetCoordinate(org.getIp(), jumpDelta, environment);
        placeInstructionWithVector("CALL", jumpDelta);

        sim.tick();

        assertThat(org.getIp()).isEqualTo(expectedIp);
        assertThat(org.getCallStack().peek()).isInstanceOf(Organism.ProcFrame.class);
    }

    @Test
    void testJmpr() {
        // JMPR interpretiert den Vektor im Register als relative Koordinate zur aktuellen IP.
        int[] jumpVector = new int[]{12};
        int[] currentIp = org.getIp(); // IP vor der Ausführung (z.B. [5])
        // Das erwartete Ziel ist die aktuelle Position PLUS der Sprungvektor.
        int[] expectedIp = org.getTargetCoordinate(currentIp, jumpVector, environment); // Erwartet: [17]

        org.setDr(0, jumpVector);
        placeInstruction("JMPR", 0);

        sim.tick();

        assertThat(org.getIp()).isEqualTo(expectedIp);
    }

    @Test
    void testJmps() {
        int[] jumpDelta = new int[]{8};
        int[] expectedIp = org.getTargetCoordinate(org.getIp(), jumpDelta, environment);
        // Vektor oben auf den Stack legen
        org.getDataStack().push(jumpDelta);
        placeInstruction("JMPS");

        sim.tick();

        assertThat(org.getIp()).isEqualTo(expectedIp);
    }

    @Test
    void testRet() {
        // Ein Return-Frame vorbereiten. Die absolute Rücksprungadresse ist die
        // Position nach der RET-Instruktion (Länge 1).
        // Startposition ist [5], also ist die Rücksprungadresse [6].
        int[] expectedIp = new int[]{6};
        Object[] prsSnapshot = org.getPrs().toArray(new Object[0]);
        Object[] fprsSnapshot = org.getFprs().toArray(new Object[0]);

        // KORREKTUR: Verwende den neuen Konstruktor und eine absolute IP.
        org.getCallStack().push(new Organism.ProcFrame("TEST_PROC", expectedIp, prsSnapshot, fprsSnapshot, java.util.Collections.emptyMap()));

        placeInstruction("RET");

        sim.tick();

        assertThat(org.getIp()).isEqualTo(expectedIp);
    }

    @org.junit.jupiter.api.AfterEach
    void assertNoInstructionFailure() {
        assertThat(org.isInstructionFailed()).as("Instruction failed: " + org.getFailureReason()).isFalse();
    }
}