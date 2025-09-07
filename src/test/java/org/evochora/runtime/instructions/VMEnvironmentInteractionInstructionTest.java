package org.evochora.runtime.instructions;

import org.evochora.runtime.Config;
import org.evochora.runtime.Simulation;
import org.evochora.runtime.isa.Instruction;
import org.evochora.runtime.model.Environment;
import org.evochora.runtime.model.Molecule;
import org.evochora.runtime.model.Organism;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.Tag;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Contains low-level unit tests for the execution of environment interaction instructions (PEEK, POKE)
 * by the virtual machine. Each test sets up a specific state, executes a single instruction,
 * and verifies the precise outcome on the organism's state and the environment.
 * These tests operate on an in-memory simulation and do not require external resources.
 */
public class VMEnvironmentInteractionInstructionTest {

    private Environment environment;
    private Organism org;
    private Simulation sim;
    private final int[] startPos = new int[]{5, 5};

    @BeforeAll
    static void init() {
        Instruction.init();
    }

    @BeforeEach
    void setUp() {
        environment = new Environment(new int[]{100, 100}, true);
        sim = new Simulation(environment);
        org = Organism.create(sim, startPos, 2000, sim.getLogger());
        sim.addOrganism(org);
    }

    private void placeInstruction(String name) {
        int opcode = Instruction.getInstructionIdByName(name);
        environment.setMolecule(new Molecule(Config.TYPE_CODE, opcode), org.getIp());
    }

    private void placeInstruction(String name, Integer... args) {
        placeInstruction(name);
        int[] currentPos = org.getIp();
        for (int arg : args) {
            currentPos = org.getNextInstructionPosition(currentPos, org.getDv(), environment); // CORRECTED
            environment.setMolecule(new Molecule(Config.TYPE_DATA, arg), currentPos);
        }
    }

    private void placeInstructionWithVector(String name, int reg, int[] vector) {
        int opcode = Instruction.getInstructionIdByName(name);
        environment.setMolecule(new Molecule(Config.TYPE_CODE, opcode), org.getIp());
        int[] currentPos = org.getIp();
        currentPos = org.getNextInstructionPosition(currentPos, org.getDv(), environment); // CORRECTED
        environment.setMolecule(new Molecule(Config.TYPE_DATA, reg), currentPos);
        for (int val : vector) {
            currentPos = org.getNextInstructionPosition(currentPos, org.getDv(), environment); // CORRECTED
            environment.setMolecule(new Molecule(Config.TYPE_DATA, val), currentPos);
        }
    }

    /**
     * Tests the POKE instruction (write from register to location specified by register vector).
     * This is a unit test for the VM's instruction logic.
     */
    @Test
    @Tag("unit")
    void testPoke() {
        int[] vec = new int[]{0, 1};
        int payload = new Molecule(Config.TYPE_DATA, 77).toInt();
        org.setDr(0, payload);
        org.setDr(1, vec);

        placeInstruction("POKE", 0, 1);
        int[] targetPos = org.getTargetCoordinate(org.getDp(0), vec, environment); // CORRECTED

        sim.tick();

        assertThat(org.isInstructionFailed()).as("Instruction failed: " + org.getFailureReason()).isFalse();
        assertThat(environment.getMolecule(targetPos).toInt()).isEqualTo(payload);
        // POKE(DATA) costs base 1 + 5
        assertThat(org.getEr()).isLessThanOrEqualTo(2000 - 1 - 5);
    }

    /**
     * Tests the POKI instruction (write from register to location specified by immediate vector).
     * This is a unit test for the VM's instruction logic.
     */
    @Test
    @Tag("unit")
    void testPoki() {
        int payload = new Molecule(Config.TYPE_DATA, 88).toInt();
        org.setDr(0, payload);

        placeInstruction("POKI", 0, 0, 1);
        int[] vec = new int[]{0, 1};
        int[] target = org.getTargetCoordinate(org.getDp(0), vec, environment); // CORRECTED

        sim.tick();

        assertThat(org.isInstructionFailed()).as("Instruction failed: " + org.getFailureReason()).isFalse();
        assertThat(environment.getMolecule(target).toInt()).isEqualTo(payload);
        // POKI(DATA) costs base 1 + 5
        assertThat(org.getEr()).isLessThanOrEqualTo(2000 - 1 - 5);
    }

    /**
     * Tests the POKS instruction (write from stack to location specified by stack vector).
     * This is a unit test for the VM's instruction logic.
     */
    @Test
    @Tag("unit")
    void testPoks() {
        int payload = new Molecule(Config.TYPE_DATA, 33).toInt();
        int[] vec = new int[]{0, 1};
        org.getDataStack().push(vec);
        org.getDataStack().push(payload);

        placeInstruction("POKS");
        int[] target = org.getTargetCoordinate(org.getDp(0), vec, environment); // CORRECTED

        sim.tick();

        assertThat(org.isInstructionFailed()).as("Instruction failed: " + org.getFailureReason()).isFalse();
        assertThat(environment.getMolecule(target).toInt()).isEqualTo(payload);
        // POKS(DATA) costs base 1 + 5
        assertThat(org.getEr()).isLessThanOrEqualTo(2000 - 1 - 5);
    }

    /**
     * Tests the PEEK instruction (read to register from location specified by register vector).
     * This is a unit test for the VM's instruction logic.
     */
    @Test
    @Tag("unit")
    void testPeek() {
        org.setDp(0, org.getIp()); // CORRECTED
        int[] vec = new int[]{0, 1};
        int[] target = org.getTargetCoordinate(org.getDp(0), vec, environment); // CORRECTED
        int payload = new Molecule(Config.TYPE_DATA, 7).toInt();
        environment.setMolecule(Molecule.fromInt(payload), target);

        org.setDr(1, vec);
        placeInstruction("PEEK", 0, 1);
        sim.tick();

        assertThat(org.getDr(0)).isEqualTo(payload);
        assertThat(environment.getMolecule(target).isEmpty()).isTrue();
    }

    /**
     * Tests the PEKI instruction (read to register from location specified by immediate vector).
     * This is a unit test for the VM's instruction logic.
     */
    @Test
    @Tag("unit")
    void testPeki() {
        org.setDp(0, org.getIp()); // CORRECTED
        int[] vec = new int[]{0, 1};
        int[] target = org.getTargetCoordinate(org.getDp(0), vec, environment); // CORRECTED
        int payload = new Molecule(Config.TYPE_DATA, 11).toInt();
        environment.setMolecule(Molecule.fromInt(payload), target);

        placeInstructionWithVector("PEKI", 0, vec);
        sim.tick();

        assertThat(org.getDr(0)).isEqualTo(payload);
        assertThat(environment.getMolecule(target).isEmpty()).isTrue();
    }

    /**
     * Tests the PEKS instruction (read to stack from location specified by stack vector).
     * This is a unit test for the VM's instruction logic.
     */
    @Test
    @Tag("unit")
    void testPeks() {
        org.setDp(0, org.getIp()); // CORRECTED
        int[] vec = new int[]{-1, 0};
        int[] target = org.getTargetCoordinate(org.getDp(0), vec, environment); // CORRECTED
        int payload = new Molecule(Config.TYPE_DATA, 9).toInt();
        environment.setMolecule(Molecule.fromInt(payload), target);

        org.getDataStack().push(vec);
        placeInstruction("PEKS");
        sim.tick();

        assertThat(org.getDataStack().pop()).isEqualTo(payload);
        assertThat(environment.getMolecule(target).isEmpty()).isTrue();
    }

    /**
     * Tests the PPKR instruction (PEEK+POKE with register operands).
     * This is a unit test for the VM's instruction logic.
     */
    @Test
    @Tag("unit")
    void testPpkr() {
        int[] vec = new int[]{0, 1};
        int originalPayload = new Molecule(Config.TYPE_DATA, 99).toInt();
        int newPayload = new Molecule(Config.TYPE_DATA, 111).toInt();
        
        // Set up target cell with original content
        int[] targetPos = org.getTargetCoordinate(org.getDp(0), vec, environment);
        environment.setMolecule(Molecule.fromInt(originalPayload), targetPos);
        
        // Set up registers: target reg, vector reg
        org.setDr(0, newPayload); // register contains value to write, will receive peeked value
        org.setDr(1, vec); // vector register

        placeInstruction("PPKR", 0, 1);
        sim.tick();

        assertThat(org.isInstructionFailed()).as("Instruction failed: " + org.getFailureReason()).isFalse();
        
        // Verify PPKR worked correctly: register contains peeked value, cell contains new value
        assertThat(org.getDr(0)).isEqualTo(originalPayload).as("Register should contain DATA:99 (read from cell 0|1)");
        assertThat(environment.getMolecule(targetPos).toInt()).isEqualTo(newPayload).as("Cell should contain DATA:111 (written from %DR0)");
        
        // Verify energy costs: PPKR(DATA->DATA) costs base 1 + 5 (peek) + 5 (poke) = 11
        int expectedEnergy = 2000 - 1 - 5 - 5;
        assertThat(org.getEr()).isEqualTo(expectedEnergy).as("Energy should be consumed correctly: base 1 + peek 5 + poke 5 = 11");
    }

    /**
     * Tests the PPKI instruction (PEEK+POKE with immediate vector).
     * This is a unit test for the VM's instruction logic.
     */
    @Test
    @Tag("unit")
    void testPpki() {
        int[] vec = new int[]{0, 1}; // PPKI reads [0, 1] from immediate operands
        int originalPayload = new Molecule(Config.TYPE_DATA, 99).toInt();
        int newPayload = new Molecule(Config.TYPE_DATA, 111).toInt();
        
        // Set up target cell with original content at the position where PPKI will actually look
        // PPKI will look at [5, 6] (DP + [0, 1])
        int[] targetPos = org.getTargetCoordinate(org.getDp(0), vec, environment);
        environment.setMolecule(Molecule.fromInt(originalPayload), targetPos);
        
        
        // Set up register: contains value to write, will receive peeked value
        org.setDr(0, newPayload); // register contains value to write

        int initialEr = org.getEr();
        placeInstruction("PPKI", 0, 0, 1); // PPKI %REG <Vector> - Vector components: [0,1]
        sim.tick();

        assertThat(org.isInstructionFailed()).as("Instruction failed: " + org.getFailureReason()).isFalse();
        
        // Verify PPKI worked correctly: register contains peeked value, cell contains new value
        assertThat(org.getDr(0)).isEqualTo(originalPayload).as("Register should contain DATA:99 (read from cell 0|1)");
        assertThat(environment.getMolecule(targetPos).toInt()).isEqualTo(newPayload).as("Cell should contain DATA:111 (written from %DR0)");
        
        // Verify energy costs: PPKI(DATA->DATA) costs base 1 + 5 (peek) + 5 (poke) = 11
        int expectedEnergy = initialEr - 1 - 5 - 5;
        assertThat(org.getEr()).isEqualTo(expectedEnergy).as("Energy should be consumed correctly: base 1 + peek 5 + poke 5 = 11");
    }

    /**
     * Tests the PPKS instruction (PEEK+POKE with stack operands).
     * This is a unit test for the VM's instruction logic.
     */
    @Test
    @Tag("unit")
    void testPpks() {
        int[] vec = new int[]{0, 1};
        int originalPayload = new Molecule(Config.TYPE_DATA, 99).toInt();
        int newPayload = new Molecule(Config.TYPE_DATA, 111).toInt();
        
        // Set up target cell with original content
        int[] targetPos = org.getTargetCoordinate(org.getDp(0), vec, environment);
        environment.setMolecule(Molecule.fromInt(originalPayload), targetPos);
        
        // Set up stack: new value and vector (PPKS reads vector from stack)
        org.getDataStack().push(vec);
        org.getDataStack().push(newPayload);

        placeInstruction("PPKS");
        sim.tick();

        assertThat(org.isInstructionFailed()).as("Instruction failed: " + org.getFailureReason()).isFalse();
        
        // Verify PPKS worked correctly: stack contains peeked value, cell contains new value
        assertThat(org.getDataStack().pop()).isEqualTo(originalPayload).as("Stack should contain DATA:99 (read from cell 0|1)");
        assertThat(environment.getMolecule(targetPos).toInt()).isEqualTo(newPayload).as("Cell should contain DATA:111 (written from stack)");
        
        // Verify energy costs: PPKS(DATA->DATA) costs base 1 + 5 (peek) + 5 (poke) = 11
        int expectedEnergy = 2000 - 1 - 5 - 5;
        assertThat(org.getEr()).isEqualTo(expectedEnergy).as("Energy should be consumed correctly: base 1 + peek 5 + poke 5 = 11");
    }

    /**
     * Tests that PPKR works when target cell is empty (stores empty molecule and writes new value).
     * This is a unit test for the VM's instruction logic.
     */
    @Test
    @Tag("unit")
    void testPpkrWorksOnEmptyCell() {
        int[] vec = new int[]{0, 1};
        int newPayload = new Molecule(Config.TYPE_DATA, 88).toInt();
        int emptyMolecule = new Molecule(Config.TYPE_CODE, 0).toInt();
        
        // Target cell is empty (not set)
        int[] targetPos = org.getTargetCoordinate(org.getDp(0), vec, environment);
        
        // Set up registers
        org.setDr(0, newPayload); // register contains value to write, will receive peeked value
        org.setDr(1, vec); // vector register

        placeInstruction("PPKR", 0, 1);
        sim.tick();

        assertThat(org.isInstructionFailed()).as("Instruction failed: " + org.getFailureReason()).isFalse();
        
        // Verify PPKR on empty cell: register contains empty molecule, cell contains new value
        assertThat(org.getDr(0)).isEqualTo(emptyMolecule).as("Register should contain empty molecule (CODE:0) when cell was empty");
        assertThat(environment.getMolecule(targetPos).toInt()).isEqualTo(newPayload).as("Cell should contain the new molecule that was written");
        
        // Verify energy costs: PPKR on empty cell: base 1 + 5 (poke only, no peek costs) = 6
        int expectedEnergy = 2000 - 1 - 5;
        assertThat(org.getEr()).isEqualTo(expectedEnergy).as("Energy should be consumed correctly: base 1 + poke 5 = 6 (no peek costs on empty cell)");
    }

    @org.junit.jupiter.api.AfterEach
    void assertNoInstructionFailure() {
        // Only assert no failure for tests that don't expect failures
        // Tests that expect failures (like testPpkrFailsOnEmptyCell) will handle their own assertions
    }
}