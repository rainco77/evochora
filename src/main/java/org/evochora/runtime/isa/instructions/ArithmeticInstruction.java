package org.evochora.runtime.isa.instructions;

import org.evochora.compiler.api.ProgramArtifact;
import org.evochora.runtime.Config;
import org.evochora.runtime.internal.services.ExecutionContext;
import org.evochora.runtime.isa.Instruction;
import org.evochora.runtime.model.Environment;
import org.evochora.runtime.model.Molecule;
import org.evochora.runtime.model.Organism;

import java.util.List;
import java.util.NoSuchElementException;

/**
 * Handles all arithmetic instructions, including scalar and vector operations.
 * It supports different operand sources like registers, immediate values, and the stack.
 */
public class ArithmeticInstruction extends Instruction {

    /**
     * Constructs a new ArithmeticInstruction.
     * @param organism The organism executing the instruction.
     * @param fullOpcodeId The full opcode ID of the instruction.
     */
    public ArithmeticInstruction(Organism organism, int fullOpcodeId) {
        super(organism, fullOpcodeId);
    }

    @Override
    public void execute(ExecutionContext context, ProgramArtifact artifact) {
        try {
            Organism organism = context.getOrganism();
            List<Operand> operands = resolveOperands(context.getWorld());
            if (organism.isInstructionFailed()) {
                return;
            }
            if (getName().startsWith("DOT") || getName().startsWith("CRS")) {
                handleVectorProducts(context.getWorld(), operands);
                return;
            }

            if (operands.size() != 2) {
                organism.instructionFailed("Invalid operand count for arithmetic operation.");
                return;
            }

            Operand op1 = operands.get(0); // Always the destination (register or stack)
            Operand op2 = operands.get(1);

            Object result;

            // Vector Arithmetic
            if (op1.value() instanceof int[] v1 && op2.value() instanceof int[] v2) {
                if (v1.length != v2.length) {
                    organism.instructionFailed("Vector dimensions must match.");
                    return;
                }
                int[] resVec = new int[v1.length];
                String opName = getName().substring(0, 3); // "ADDR" -> "ADD"

                switch (opName) {
                    case "ADD" -> { for (int i = 0; i < v1.length; i++) resVec[i] = v1[i] + v2[i]; }
                    case "SUB" -> { for (int i = 0; i < v1.length; i++) resVec[i] = v1[i] - v2[i]; }
                    default -> {
                        organism.instructionFailed("Unsupported vector operation: " + getName());
                        return;
                    }
                }
                result = resVec;
            }
            // Scalar Arithmetic
            else if (op1.value() instanceof Integer i1 && op2.value() instanceof Integer i2) {
                Molecule s1 = org.evochora.runtime.model.Molecule.fromInt(i1);

                // Determine proper decoding for op2 based on instruction variant
                String instrName = getName();
                Molecule s2;
                if (instrName.endsWith("I")) {
                    // Immediate operand: decode stored symbol to get scalar, then rewrap with s1.type
                    Molecule imm = org.evochora.runtime.model.Molecule.fromInt(i2);
                    s2 = new Molecule(s1.type(), imm.toScalarValue());
                } else {
                    // Register or Stack operand: decode as-is
                    s2 = org.evochora.runtime.model.Molecule.fromInt(i2);
                }

                if (Config.STRICT_TYPING && s1.type() != s2.type()) {
                    organism.instructionFailed("Operand types must match in strict mode.");
                    return;
                }

                long scalarResult;
                String opName = instrName.substring(0, 3); // "ADDR" -> "ADD"

                switch (opName) {
                    case "ADD" -> scalarResult = (long) s1.toScalarValue() + s2.toScalarValue();
                    case "SUB" -> scalarResult = (long) s1.toScalarValue() - s2.toScalarValue();
                    case "MUL" -> scalarResult = (long) s1.toScalarValue() * s2.toScalarValue();
                    case "DIV" -> {
                        if (s2.toScalarValue() == 0) { organism.instructionFailed("Division by zero."); return; }
                        scalarResult = (long) s1.toScalarValue() / s2.toScalarValue();
                    }
                    case "MOD" -> {
                        if (s2.toScalarValue() == 0) { organism.instructionFailed("Modulo by zero."); return; }
                        scalarResult = (long) s1.toScalarValue() % s2.toScalarValue();
                    }
                    default -> {
                        organism.instructionFailed("Unknown scalar operation: " + instrName);
                        return;
                    }
                }
                result = new Molecule(s1.type(), (int)scalarResult).toInt();
            } else {
                organism.instructionFailed("Mismatched or invalid operand types for arithmetic operation.");
                return;
            }

            // Write result back (either to register or stack)
            if (op1.rawSourceId() != -1) { // -1 means the operand came from the stack
                if (!writeOperand(op1.rawSourceId(), result)) {
                    return;
                }
            } else {
                organism.getDataStack().push(result);
            }

        } catch (NoSuchElementException e) {
            organism.instructionFailed("Stack underflow during arithmetic operation.");
            return;
        }
    }

    private void handleVectorProducts(Environment environment, List<Operand> operands) {
        Organism organism = this.organism;
        String name = getName();
        try {
            boolean stackVariant = name.endsWith("S");
            if (stackVariant) {
                if (operands.size() != 2) { organism.instructionFailed("" + name + " expects two stack operands."); return; }
                if (!(operands.get(0).value() instanceof int[] v2) || !(operands.get(1).value() instanceof int[] v1)) {
                    organism.instructionFailed(name + " requires vector operands.");
                    return;
                }
                int result = switch (name) {
                    case "DOTS" -> dot(v1, v2);
                    case "CRSS" -> cross2d(v1, v2);
                    default -> 0;
                };
                organism.getDataStack().push(new Molecule(Config.TYPE_DATA, result).toInt());
                return;
            }

            // Register variant: %DEST, %VEC1, %VEC2
            if (operands.size() != 3) { organism.instructionFailed(name + " expects 3 operands."); return; }
            int destReg = operands.get(0).rawSourceId();
            Object v1o = operands.get(1).value();
            Object v2o = operands.get(2).value();
            if (!(v1o instanceof int[] v1) || !(v2o instanceof int[] v2)) {
                organism.instructionFailed(name + " requires vector operands.");
                return;
            }
            int result = switch (name) {
                case "DOTR" -> dot(v1, v2);
                case "CRSR" -> cross2d(v1, v2);
                default -> 0;
            };
            if (!writeOperand(destReg, new Molecule(Config.TYPE_DATA, result).toInt())) {
                return;
            }
        } catch (NoSuchElementException e) {
            organism.instructionFailed("Stack underflow during vector product operation.");
            return;
        }
    }

    private int dot(int[] a, int[] b) {
        if (a.length != b.length) { this.organism.instructionFailed("Vector dimensions must match for DOT."); return 0; }
        long sum = 0;
        for (int i = 0; i < a.length; i++) sum += (long)a[i] * (long)b[i];
        return (int) sum;
    }

    private int cross2d(int[] a, int[] b) {
        if (a.length < 2 || b.length < 2) { this.organism.instructionFailed("CRS requires 2D vectors."); return 0; }
        return a[0] * b[1] - a[1] * b[0];
    }

    /**
     * Plans the execution of an arithmetic instruction.
     * @param organism The organism that will execute the instruction.
     * @param environment The environment in which the instruction will be executed.
     * @return The planned instruction.
     */
    public static Instruction plan(Organism organism, Environment environment) {
        int fullOpcodeId = environment.getMolecule(organism.getIp()).toInt();
        return new ArithmeticInstruction(organism, fullOpcodeId);
    }
}