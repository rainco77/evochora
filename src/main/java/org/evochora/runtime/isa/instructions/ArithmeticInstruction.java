package org.evochora.runtime.isa.instructions;

import org.evochora.app.setup.Config;
import org.evochora.app.Simulation;
import org.evochora.compiler.internal.legacy.AssemblerOutput;
import org.evochora.runtime.isa.Instruction;
import org.evochora.runtime.model.Molecule;
import org.evochora.runtime.model.Organism;
import org.evochora.runtime.model.World;

import java.util.List;
import java.util.Map;
import java.util.NoSuchElementException;

public class ArithmeticInstruction extends Instruction {

    public ArithmeticInstruction(Organism organism, int fullOpcodeId) {
        super(organism, fullOpcodeId);
    }

    @Override
    public void execute(Simulation simulation) {
        try {
            // Die Basisklasse holt die Operanden, egal ob von Registern, Stack oder als Immediate.
            List<Operand> operands = resolveOperands(simulation.getWorld());
            if (operands.size() != 2) {
                organism.instructionFailed("Invalid operand count for arithmetic operation.");
                return;
            }

            Operand op1 = operands.get(0); // Ist immer das Ziel (Register oder Stack)
            Operand op2 = operands.get(1);

            Object result;

            // --- Vektor-Arithmetik ---
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
            // --- Skalar-Arithmetik ---
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

            // Ergebnis zurückschreiben (entweder ins Register oder auf den Stack)
            if (op1.rawSourceId() != -1) { // -1 bedeutet, der Operand kam vom Stack
                writeOperand(op1.rawSourceId(), result);
            } else {
                organism.getDataStack().push(result);
            }

        } catch (NoSuchElementException e) {
            organism.instructionFailed("Stack underflow during arithmetic operation.");
        }
    }

    public static Instruction plan(Organism organism, World world) {
        int fullOpcodeId = world.getMolecule(organism.getIp()).toInt();
        return new ArithmeticInstruction(organism, fullOpcodeId);
    }

    public static AssemblerOutput assemble(String[] args, Map<String, Integer> registerMap, Map<String, Integer> labelMap, String instructionName) {
        String name = instructionName.toUpperCase();

        if (name.endsWith("R")) { // z.B. ADDR
            if (args.length != 2) throw new IllegalArgumentException(name + " expects 2 register arguments.");
            Integer reg1 = resolveRegToken(args[0], registerMap);
            Integer reg2 = resolveRegToken(args[1], registerMap);
            if (reg1 == null || reg2 == null) throw new IllegalArgumentException("Invalid register for " + name);
            return new AssemblerOutput.CodeSequence(List.of(new Molecule(Config.TYPE_DATA, reg1).toInt(), new Molecule(Config.TYPE_DATA, reg2).toInt()));

        } else if (name.endsWith("I")) { // z.B. ADDI
            if (args.length != 2) throw new IllegalArgumentException(name + " expects a register and an immediate value.");
            Integer reg1 = resolveRegToken(args[0], registerMap);
            if (reg1 == null) throw new IllegalArgumentException("Invalid register for " + name);

            String[] literalParts = args[1].split(":");
            if (literalParts.length != 2) throw new IllegalArgumentException("Immediate argument must be in TYPE:VALUE format.");
            String typeName = literalParts[0].toUpperCase();
            int value = Integer.parseInt(literalParts[1]);
            int type = switch (typeName) {
                case "CODE" -> Config.TYPE_CODE;
                case "DATA" -> Config.TYPE_DATA;
                case "ENERGY" -> Config.TYPE_ENERGY;
                case "STRUCTURE" -> Config.TYPE_STRUCTURE;
                default -> throw new IllegalArgumentException("Unknown type for literal: " + typeName);
            };
            return new AssemblerOutput.CodeSequence(List.of(new Molecule(Config.TYPE_DATA, reg1).toInt(), new Molecule(type, value).toInt()));

        } else if (name.endsWith("S")) { // z.B. ADDS
            if (args.length != 0) throw new IllegalArgumentException(name + " expects no arguments.");
            return new AssemblerOutput.CodeSequence(List.of());
        }

        throw new IllegalArgumentException("Cannot assemble unknown instruction variant: " + name);
    }
}
